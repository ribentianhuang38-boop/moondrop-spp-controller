package com.moondrop.controller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothManager(private val bluetoothAdapter: BluetoothAdapter?) {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    private val isConnected = MutableStateFlow(false)
    private val isReconnecting = MutableStateFlow(false)
    private val logs = MutableStateFlow<List<LogEntry>>(emptyList())
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var autoReconnectEnabled = MutableStateFlow(true)
    private var currentConnectedDevice: BluetoothDevice? = null

    private val currentAnc = MutableStateFlow("Normal")
    private val currentGain = MutableStateFlow("Medium")
    private val selectedPreset = MutableStateFlow(0)

    val connectionState = isConnected.asStateFlow()
    val reconnectingState = isReconnecting.asStateFlow()
    val logFlow = logs.asStateFlow()
    val autoReconnect = autoReconnectEnabled.asStateFlow()
    val ancMode = currentAnc.asStateFlow()
    val gainMode = currentGain.asStateFlow()
    val presetMode = selectedPreset.asStateFlow()

    data class LogEntry(val time: String, val direction: String, val message: String)

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled.value = enabled
        addLog("INFO", "Auto-reconnect set to: $enabled")
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun connect(device: BluetoothDevice) {
        currentConnectedDevice = device
        isReconnecting.value = false
        addLog("INFO", "Connecting to ${device.name} (${device.address})...")
        scope.launch {
            try {
                disconnectInternal()
                
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                isConnected.value = true
                addLog("SUCCESS", "Connected to ${device.name}!")
                
                scope.launch {
                    delay(600)
                    sendHex("ff040000001d1003") // Query ANC
                    delay(250)
                    sendHex("ff040000001d1e01") // Query Gain
                    delay(250)
                    sendHex("ff040000001d0a02") // Query EQ Preset
                }
                
                startReadLoop(device)
            } catch (e: IOException) {
                addLog("ERROR", "Connection failed: ${e.message}")
                isConnected.value = false
                try { socket?.close() } catch (ex: Exception) {}
                
                if (autoReconnectEnabled.value) {
                    triggerAutoReconnect(device)
                }
            }
        }
    }

    fun disconnect() {
        currentConnectedDevice = null
        isReconnecting.value = false
        if (isConnected.value) {
            addLog("WARNING", "Disconnected by user.")
        }
        isConnected.value = false
        disconnectInternal()
    }

    private fun disconnectInternal() {
        try { inputStream?.close() } catch (e: Exception) {}
        try { outputStream?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
    }

    fun sendHex(hexStr: String) {
        val cleanHex = hexStr.replace(" ", "").replace(":", "")
        if (cleanHex.isEmpty()) return
        
        scope.launch {
            if (!isConnected.value || outputStream == null) {
                addLog("ERROR", "Not connected to any device.")
                return@launch
            }
            try {
                val bytes = hexStringToByteArray(cleanHex)
                outputStream?.write(bytes)
                outputStream?.flush()
                addLog("TX", cleanHex)
            } catch (e: IOException) {
                addLog("ERROR", "Send failed: ${e.message}")
            }
        }
    }

    private fun startReadLoop(device: BluetoothDevice) {
        val buffer = ByteArray(1024)
        val byteBuffer = mutableListOf<Byte>()
        while (isConnected.value) {
            try {
                val bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead == -1) {
                    addLog("WARNING", "Connection closed by device.")
                    isConnected.value = false
                    break
                }
                
                for (i in 0 until bytesRead) {
                    byteBuffer.add(buffer[i])
                }
                
                parseStream(byteBuffer)
            } catch (e: IOException) {
                if (isConnected.value) {
                    addLog("ERROR", "Read error: ${e.message}")
                    isConnected.value = false
                }
                break
            }
        }
        
        // If we lost connection and auto-reconnect is active, run it
        if (!isConnected.value && autoReconnectEnabled.value && currentConnectedDevice != null) {
            triggerAutoReconnect(device)
        }
    }

    private fun triggerAutoReconnect(device: BluetoothDevice) {
        if (isReconnecting.value) return
        
        scope.launch {
            isReconnecting.value = true
            addLog("WARNING", "Connection lost! Waiting 5s for device to restart before auto-reconnect...")
            delay(5000)
            
            var attempt = 1
            val maxAttempts = 15
            while (attempt <= maxAttempts && !isConnected.value && isReconnecting.value) {
                addLog("INFO", "Auto-reconnect attempt ($attempt/$maxAttempts)...")
                try {
                    disconnectInternal()
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket?.connect()
                    
                    inputStream = socket?.inputStream
                    outputStream = socket?.outputStream
                    isConnected.value = true
                    isReconnecting.value = false
                    addLog("SUCCESS", "Auto-reconnected successfully!")
                    
                    startReadLoop(device)
                    break
                } catch (e: Exception) {
                    attempt++
                    delay(3000)
                }
            }
            if (!isConnected.value) {
                isReconnecting.value = false
                addLog("ERROR", "Auto-reconnect failed after $maxAttempts attempts.")
            }
        }
    }

    private fun parseStream(buffer: MutableList<Byte>) {
        while (buffer.size >= 8) {
            if (buffer[0] == 0xff.toByte() && buffer[1] == 0x04.toByte()) {
                val lenHigh = buffer[2].toInt() and 0xff
                val lenLow = buffer[3].toInt() and 0xff
                val length = (lenHigh shl 8) or lenLow
                val totalLen = 8 + length
                if (buffer.size < totalLen) {
                    break
                }
                
                val packet = buffer.subList(0, totalLen).toByteArray()
                val hexStr = byteArrayToHexString(packet)
                val decoded = decodePacket(packet)
                addLog("RX", "$hexStr  --> [Decoded: $decoded]")
                
                repeat(totalLen) { buffer.removeAt(0) }
            } else if (buffer[0] == 0xff.toByte() && buffer[1] == 0x01.toByte()) {
                val totalLen = 8
                if (buffer.size < totalLen) {
                    break
                }
                val packet = buffer.subList(0, totalLen).toByteArray()
                val hexStr = byteArrayToHexString(packet)
                addLog("RX", "$hexStr  --> [Handshake / Init Response]")
                repeat(totalLen) { buffer.removeAt(0) }
            } else {
                buffer.removeAt(0)
            }
        }
    }

    private fun decodePacket(packet: ByteArray): String {
        if (packet.size < 8) return "Too Short"
        val cmdSpace = byteArrayToHexString(packet.copyOfRange(4, 6))
        val cmdId = byteArrayToHexString(packet.copyOfRange(6, 8))
        val dataPart = packet.copyOfRange(8, packet.size)

        if (cmdSpace != "001d") {
            return "Custom CmdSpace:$cmdSpace CmdID:$cmdId Data:${byteArrayToHexString(dataPart)}"
        }

        return when (cmdId) {
            "1103" -> {
                if (dataPart.isNotEmpty()) {
                    val ancVal = dataPart[0].toInt() and 0xff
                    val mode = when (ancVal) {
                        1 -> "Normal"
                        2 -> "ANC"
                        4 -> "Transparency"
                        else -> "Unknown"
                    }
                    currentAnc.value = mode
                    "ANC Status: $mode"
                } else "ANC Status Response"
            }
            "1f01" -> {
                if (dataPart.isNotEmpty()) {
                    val gainVal = dataPart[0].toInt() and 0xff
                    val mode = when (gainVal) {
                        0 -> "High"
                        1 -> "Medium"
                        2 -> "Low"
                        else -> "Unknown"
                    }
                    currentGain.value = mode
                    "Gain Status: $mode"
                } else "Gain Status Response"
            }
            "1f02" -> {
                val status = if (byteArrayToHexString(dataPart) == "00") "Success" else "Failed (${byteArrayToHexString(dataPart)})"
                "Gain Set ACK: $status"
            }
            "2b02" -> {
                val status = if (byteArrayToHexString(dataPart) == "00") "Success" else "Failed (${byteArrayToHexString(dataPart)})"
                "LDAC Activation ACK: $status"
            }
            "2104" -> {
                val status = if (byteArrayToHexString(dataPart) == "00") "Success" else "Failed (${byteArrayToHexString(dataPart)})"
                "LC3 / LE Audio Set ACK: $status"
            }
            "0b05" -> {
                if (dataPart.size > 8) {
                    "Panel/EQ Details (${dataPart.size} bytes): ${byteArrayToHexString(dataPart.copyOfRange(0, 8))}..."
                } else {
                    "Panel/EQ Details: ${byteArrayToHexString(dataPart)}"
                }
            }
            "0b02" -> {
                if (dataPart.isNotEmpty()) {
                    val presetVal = dataPart[0].toInt() and 0xff
                    selectedPreset.value = presetVal
                    "Selected EQ Preset: $presetVal"
                } else "Selected EQ Preset Response"
            }
            "0105" -> {
                try {
                    val version = String(dataPart, Charsets.US_ASCII)
                    "Firmware Version: $version"
                } catch (e: Exception) {
                    "Firmware version hex: ${byteArrayToHexString(dataPart)}"
                }
            }
            "0302" -> {
                try {
                    val sn = String(dataPart.copyOfRange(1, dataPart.size), Charsets.US_ASCII)
                    "Serial Number: $sn"
                } catch (e: Exception) {
                    "SN Hex: ${byteArrayToHexString(dataPart)}"
                }
            }
            "0107" -> "Heartbeat / Generic ACK (0107)"
            else -> "CmdID $cmdId | Data: ${byteArrayToHexString(dataPart)}"
        }
    }

    private fun addLog(direction: String, message: String) {
        val now = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val currentLogs = logs.value.toMutableList()
        currentLogs.add(LogEntry(now, direction, message))
        if (currentLogs.size > 200) {
            currentLogs.removeAt(0)
        }
        logs.value = currentLogs
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun selectEQPreset(presetId: Int) {
        val packet = byteArrayOf(
            0xff.toByte(), 0x04.toByte(),
            0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x1d.toByte(),
            0x0a.toByte(), 0x03.toByte(),
            presetId.toByte()
        )
        scope.launch {
            if (!isConnected.value || outputStream == null) {
                addLog("ERROR", "Not connected to any device.")
                return@launch
            }
            try {
                outputStream?.write(packet)
                outputStream?.flush()
                addLog("TX", byteArrayToHexString(packet) + " [Preset Set to $presetId]")
                selectedPreset.value = presetId
            } catch (e: IOException) {
                addLog("ERROR", "Send Preset failed: ${e.message}")
            }
        }
    }

    fun sendCustomEQ(preGain: Float, bands: List<BandConfig>) {
        if (bands.isEmpty()) return
        val startBand = 0
        val endBand = bands.size - 1
        val payloadSize = 4 + bands.size * 7
        val payload = ByteArray(payloadSize)
        
        payload[0] = startBand.toByte()
        payload[1] = endBand.toByte()
        
        val formattedPreGain = (preGain * 60.0f).toInt().coerceIn(-32768, 32767)
        payload[2] = ((formattedPreGain shr 8) and 0xff).toByte()
        payload[3] = (formattedPreGain and 0xff).toByte()
        
        for (i in bands.indices) {
            val band = bands[i]
            val offset = 4 + i * 7
            
            val freqVal = band.freq.coerceIn(0, 65535)
            payload[offset] = ((freqVal shr 8) and 0xff).toByte()
            payload[offset + 1] = (freqVal and 0xff).toByte()
            
            val qVal = (band.q * 4096.0f).toInt().coerceIn(0, 65535)
            payload[offset + 2] = ((qVal shr 8) and 0xff).toByte()
            payload[offset + 3] = (qVal and 0xff).toByte()
            
            payload[offset + 4] = band.filterType.toByte()
            
            val gainVal = (band.gain * 60.0f).toInt().coerceIn(-32768, 32767)
            payload[offset + 5] = ((gainVal shr 8) and 0xff).toByte()
            payload[offset + 6] = (gainVal and 0xff).toByte()
        }
        
        val packet = ByteArray(8 + payloadSize)
        packet[0] = 0xff.toByte()
        packet[1] = 0x04.toByte()
        packet[2] = ((payloadSize shr 8) and 0xff).toByte()
        packet[3] = (payloadSize and 0xff).toByte()
        packet[4] = 0x00.toByte()
        packet[5] = 0x1d.toByte()
        packet[6] = 0x0a.toByte()
        packet[7] = 0x07.toByte()
        System.arraycopy(payload, 0, packet, 8, payloadSize)
        
        scope.launch {
            if (!isConnected.value || outputStream == null) {
                addLog("ERROR", "Not connected to any device.")
                return@launch
            }
            try {
                outputStream?.write(packet)
                outputStream?.flush()
                addLog("TX", byteArrayToHexString(packet) + " [Custom EQ Applied]")
                selectedPreset.value = 63 // User EQ
            } catch (e: IOException) {
                addLog("ERROR", "Send EQ failed: ${e.message}")
            }
        }
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}

data class BandConfig(
    val freq: Int,
    val q: Float,
    val filterType: Int,
    val gain: Float
)
