package com.moondrop.controller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val logs = MutableStateFlow<List<LogEntry>>(emptyList())
    private val scope = CoroutineScope(Dispatchers.IO)

    val connectionState = isConnected.asStateFlow()
    val logFlow = logs.asStateFlow()

    data class LogEntry(val time: String, val direction: String, val message: String)

    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun connect(device: BluetoothDevice) {
        addLog("INFO", "Connecting to ${device.name} (${device.address})...")
        scope.launch {
            try {
                disconnect()
                
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                isConnected.value = true
                addLog("SUCCESS", "Connected to ${device.name}!")
                
                startReadLoop()
            } catch (e: IOException) {
                addLog("ERROR", "Connection failed: ${e.message}")
                isConnected.value = false
                try { socket?.close() } catch (ex: Exception) {}
            }
        }
    }

    fun disconnect() {
        if (isConnected.value) {
            addLog("WARNING", "Disconnected.")
        }
        isConnected.value = false
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

    private fun startReadLoop() {
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
                        1 -> "Normal / Off"
                        2 -> "Noise Cancelling (ANC On)"
                        4 -> "Transparency"
                        else -> "Unknown"
                    }
                    "ANC Status: $mode"
                } else "ANC Status Response"
            }
            "1f01" -> {
                if (dataPart.isNotEmpty()) {
                    val gainVal = dataPart[0].toInt() and 0xff
                    val mode = when (gainVal) {
                        0 -> "High Gain"
                        1 -> "Medium Gain"
                        2 -> "Low Gain"
                        else -> "Unknown"
                    }
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
            "0b05" -> {
                if (dataPart.size > 8) {
                    "Panel/EQ Details (${dataPart.size} bytes): ${byteArrayToHexString(dataPart.copyOfRange(0, 8))}..."
                } else {
                    "Panel/EQ Details: ${byteArrayToHexString(dataPart)}"
                }
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

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
