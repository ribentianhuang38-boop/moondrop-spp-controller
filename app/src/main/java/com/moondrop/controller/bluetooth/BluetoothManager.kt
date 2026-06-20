package com.moondrop.controller.bluetooth

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.moondrop.controller.ui.GlobalPopupManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val audioManager: android.media.AudioManager? = null
) {

    companion object {
        @Volatile
        private var instance: BluetoothManager? = null

        fun getInstance(context: Context, bluetoothAdapter: BluetoothAdapter?, audioManager: android.media.AudioManager?): BluetoothManager {
            return instance ?: synchronized(this) {
                instance ?: BluetoothManager(context.applicationContext, bluetoothAdapter, audioManager).also { instance = it }
            }
        }
    }

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    
    private val CHANNEL_ID = "moondrop_controller_status"
    private val NOTIFICATION_ID = 1001
    private val prefs = context.getSharedPreferences("moondrop_settings", Context.MODE_PRIVATE)
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    private val isConnected = MutableStateFlow(false)
    private val isReconnecting = MutableStateFlow(false)
    private val logs = MutableStateFlow<List<LogEntry>>(emptyList())
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var autoReconnectEnabled = MutableStateFlow(true)
    private var currentConnectedDevice = MutableStateFlow<BluetoothDevice?>(null)

    private val currentAnc = MutableStateFlow("Normal")
    private val currentGain = MutableStateFlow("Medium")
    private val selectedPreset = MutableStateFlow(prefs.getInt("selected_preset", 6))
    
    private val isLdacEnabled = MutableStateFlow(false)
    private val isLc3Enabled = MutableStateFlow(false)
    
    private val currentSystemVolume = MutableStateFlow(0)
    private val maxSystemVolume = MutableStateFlow(15)

    private val hasBluetoothPermission = MutableStateFlow(true)

    // Battery values (simulated or real if queried)
    private val currentBatteryCase = MutableStateFlow(100)
    private val currentBatteryLeft = MutableStateFlow(100)
    private val currentBatteryRight = MutableStateFlow(100)

    val connectionState = isConnected.asStateFlow()
    val reconnectingState = isReconnecting.asStateFlow()
    val logFlow = logs.asStateFlow()
    val autoReconnect = autoReconnectEnabled.asStateFlow()
    val deviceState = currentConnectedDevice.asStateFlow()
    
    val ancMode = currentAnc.asStateFlow()
    val gainMode = currentGain.asStateFlow()
    val presetMode = selectedPreset.asStateFlow()
    
    val ldacEnabled = isLdacEnabled.asStateFlow()
    val lc3Enabled = isLc3Enabled.asStateFlow()
    
    val systemVolume = currentSystemVolume.asStateFlow()
    val maxVolume = maxSystemVolume.asStateFlow()
    
    val bluetoothPermissionState = hasBluetoothPermission.asStateFlow()
    
    var onRequestPermission: (() -> Unit)? = null
    
    fun setBluetoothPermissionState(granted: Boolean) {
        hasBluetoothPermission.value = granted
    }
    
    fun requestBluetoothPermission() {
        onRequestPermission?.invoke()
    }

    val batteryCase = currentBatteryCase.asStateFlow()
    val batteryLeft = batteryLeftFlow()
    val batteryRight = batteryRightFlow()

    private fun batteryLeftFlow() = currentBatteryLeft.asStateFlow()
    private fun batteryRightFlow() = currentBatteryRight.asStateFlow()

    init {
        // Register BroadcastReceiver for notification noise control buttons
        val filter = IntentFilter("com.moondrop.controller.ACTION_SET_ANC")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    val mode = it.getStringExtra("mode") ?: "Normal"
                    setAncMode(mode)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        // Update notification when connection status, ANC, or battery changes
        scope.launch {
            combine(isConnected, currentAnc, currentBatteryLeft, currentBatteryRight) { _, _, _, _ ->
                Unit
            }.collect {
                updateNotification()
            }
        }

        // Show global popup ONLY on connection transition (from disconnected to connected)
        scope.launch {
            var wasConnected = false
            isConnected.collect { connected ->
                if (connected && !wasConnected) {
                    if (!com.moondrop.controller.MainActivity.isAppInForeground) {
                        scope.launch(Dispatchers.Main) {
                            GlobalPopupManager.showPopup(context, this@BluetoothManager)
                        }
                    }
                } else if (!connected && wasConnected) {
                    scope.launch(Dispatchers.Main) {
                        GlobalPopupManager.dismiss()
                    }
                }
                wasConnected = connected
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Headset Status"
            val descriptionText = "Displays connection status, battery, and ANC mode."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (!isConnected.value) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        createNotificationChannel()

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val transparencyIntent = Intent("com.moondrop.controller.ACTION_SET_ANC").apply {
            setPackage(context.packageName)
            putExtra("mode", "Transparency")
        }
        val transparencyPending = PendingIntent.getBroadcast(
            context,
            1,
            transparencyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ancIntent = Intent("com.moondrop.controller.ACTION_SET_ANC").apply {
            setPackage(context.packageName)
            putExtra("mode", "ANC")
        }
        val ancPending = PendingIntent.getBroadcast(
            context,
            2,
            ancIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val normalIntent = Intent("com.moondrop.controller.ACTION_SET_ANC").apply {
            setPackage(context.packageName)
            putExtra("mode", "Normal")
        }
        val normalPending = PendingIntent.getBroadcast(
            context,
            3,
            normalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ancStr = when (currentAnc.value) {
            "ANC" -> "降噪"
            "Transparency" -> "通透"
            "Normal" -> "普通"
            else -> "普通"
        }

        val title = "${currentConnectedDevice.value?.name ?: "MOONDROP ULTRASONIC"} 已连接"
        val content = "左耳: ${currentBatteryLeft.value}% | 右耳: ${currentBatteryRight.value}% | 模式: $ancStr"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(0, "通透", transparencyPending)
            .addAction(0, "降噪", ancPending)
            .addAction(0, "关闭", normalPending)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Log warning if error occurs
        }
    }

    fun getSavedPreGain(): Float {
        return prefs.getFloat("custom_pre_gain", -3.0f)
    }

    fun getSavedBandGains(): List<Float> {
        val list = mutableListOf<Float>()
        for (i in 0 until 10) {
            list.add(prefs.getFloat("custom_band_gain_$i", 0.0f))
        }
        return list
    }

    data class LogEntry(val time: String, val direction: String, val message: String)

    fun initVolume(current: Int, max: Int) {
        currentSystemVolume.value = current
        maxSystemVolume.value = max
    }

    fun updateVolume(volume: Int) {
        currentSystemVolume.value = volume
    }

    fun setSystemVolume(volume: Int) {
        audioManager?.let {
            try {
                it.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, volume, 0)
                currentSystemVolume.value = volume
            } catch (e: Exception) {
                addLog("ERROR", "Set volume failed: ${e.message}")
            }
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled.value = enabled
        addLog("INFO", "Auto-reconnect set to: $enabled")
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun connect(device: BluetoothDevice) {
        currentConnectedDevice.value = device
        isReconnecting.value = false
        addLog("INFO", "Connecting to ${device.name ?: "Device"} (${device.address})...")
        scope.launch {
            try {
                disconnectInternal()
                
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                isConnected.value = true
                addLog("SUCCESS", "Connected to ${device.name ?: "Device"}!")
                
                scope.launch {
                    delay(600)
                    sendHex("ff040000001d1003") // Query ANC
                    delay(250)
                    sendHex("ff040000001d1e01") // Query Gain
                    delay(250)
                    
                    val savedPresetId = prefs.getInt("selected_preset", 6)
                    if (savedPresetId == 63) {
                        val gains = getSavedBandGains()
                        val preGainVal = getSavedPreGain()
                        val bands = listOf(
                            BandConfig(31, 1.0f, 13, gains.getOrElse(0) { 0.0f }),
                            BandConfig(62, 1.0f, 13, gains.getOrElse(1) { 0.0f }),
                            BandConfig(125, 1.0f, 13, gains.getOrElse(2) { 0.0f }),
                            BandConfig(250, 1.0f, 13, gains.getOrElse(3) { 0.0f }),
                            BandConfig(500, 1.0f, 13, gains.getOrElse(4) { 0.0f }),
                            BandConfig(1000, 1.0f, 13, gains.getOrElse(5) { 0.0f }),
                            BandConfig(2000, 1.0f, 13, gains.getOrElse(6) { 0.0f }),
                            BandConfig(4000, 1.0f, 13, gains.getOrElse(7) { 0.0f }),
                            BandConfig(8000, 1.0f, 13, gains.getOrElse(8) { 0.0f }),
                            BandConfig(16000, 1.0f, 13, gains.getOrElse(9) { 0.0f })
                        )
                        sendCustomEQ(preGainVal, bands)
                    } else {
                        selectEQPreset(savedPresetId)
                    }
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
        currentConnectedDevice.value = null
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
                addLog("TX (Offline)", cleanHex)
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

    fun setAncMode(mode: String) {
        currentAnc.value = mode
        val hex = when (mode) {
            "Normal" -> "ff040001001d100401"
            "ANC" -> "ff040001001d100402"
            "Transparency" -> "ff040001001d100404"
            else -> return
        }
        sendHex(hex)
    }

    fun setGainMode(mode: String) {
        currentGain.value = mode
        val hex = when (mode) {
            "Low" -> "ff040001001d1e0202"
            "Medium" -> "ff040001001d1e0201"
            "High" -> "ff040001001d1e0200"
            else -> return
        }
        sendHex(hex)
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
        if (!isConnected.value && autoReconnectEnabled.value && currentConnectedDevice.value != null) {
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
        selectedPreset.value = presetId
        prefs.edit().putInt("selected_preset", presetId).apply()
        val packet = byteArrayOf(
            0xff.toByte(), 0x04.toByte(),
            0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x1d.toByte(),
            0x00.toByte(), 0x0c.toByte(), // Bluetrum select preset command
            presetId.toByte()
        )
        scope.launch {
            if (!isConnected.value || outputStream == null) {
                addLog("TX (Offline)", byteArrayToHexString(packet) + " [Preset Set to $presetId]")
                return@launch
            }
            try {
                outputStream?.write(packet)
                outputStream?.flush()
                addLog("TX", byteArrayToHexString(packet) + " [Preset Set to $presetId]")
            } catch (e: IOException) {
                addLog("ERROR", "Send Preset failed: ${e.message}")
            }
        }
    }

    fun toggleLdac(centralMac: String) {
        val cleanMac = centralMac.replace(":", "").replace("-", "")
        if (isLdacEnabled.value) {
            // Deactivate LDAC
            sendHex("ff040001001d2a0200") // Deactivate command
            isLdacEnabled.value = false
            addLog("INFO", "Deactivating LDAC...")
        } else {
            if (cleanMac.length == 12) {
                sendHex("ff040006001d2a02$cleanMac")
                isLdacEnabled.value = true
                addLog("INFO", "Activating LDAC with MAC $cleanMac...")
            }
        }
    }

    fun toggleLc3() {
        if (isLc3Enabled.value) {
            sendHex("ff040001001d200400") // LC3 OFF
            isLc3Enabled.value = false
            addLog("INFO", "Disabling LC3 / LE Audio...")
        } else {
            sendHex("ff040001001d200401") // LC3 ON
            isLc3Enabled.value = true
            addLog("INFO", "Enabling LC3 / LE Audio...")
        }
    }

    fun sendCustomEQ(preGain: Float, bands: List<BandConfig>) {
        selectedPreset.value = 63 // User EQ
        prefs.edit().putInt("selected_preset", 63).apply()
        prefs.edit().putFloat("custom_pre_gain", preGain).apply()
        for (i in bands.indices) {
            prefs.edit().putFloat("custom_band_gain_$i", bands[i].gain).apply()
        }
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
                addLog("TX (Offline)", byteArrayToHexString(packet) + " [Custom EQ Applied]")
                return@launch
            }
            try {
                outputStream?.write(packet)
                outputStream?.flush()
                addLog("TX", byteArrayToHexString(packet) + " [Custom EQ Applied]")
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
