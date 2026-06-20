package com.moondrop.controller

import android.Manifest
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.moondrop.controller.bluetooth.BluetoothManager
import com.moondrop.controller.ui.MainScreen

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private var volumeReceiver: BroadcastReceiver? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkAndRequestNextPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val adapter = (getSystemService(AndroidBluetoothManager::class.java))?.adapter
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        bluetoothManager = BluetoothManager.getInstance(this, adapter, audioManager)

        // Initialize volume levels
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        bluetoothManager.initVolume(currentVol, maxVol)

        // Register broadcast receiver for volume changes
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val streamType = it.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        val volume = it.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0)
                        bluetoothManager.updateVolume(volume)
                    }
                }
            }
        }
        volumeReceiver = receiver
        registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.White
                ) {
                    MainScreen(bluetoothManager)
                }
            }
        }

        bluetoothManager.onRequestPermission = {
            checkAndRequestNextPermission()
        }

        // Check if Bluetooth permissions are already granted
        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        val missingBluetooth = bluetoothPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingBluetooth.isEmpty()) {
            bluetoothManager.setBluetoothPermissionState(true)
            initBluetooth()
        } else {
            bluetoothManager.setBluetoothPermissionState(false)
        }
    }

    private fun checkAndRequestNextPermission() {
        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        val missingBluetooth = bluetoothPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingBluetooth.isNotEmpty()) {
            bluetoothManager.setBluetoothPermissionState(false)
            requestPermissionLauncher.launch(missingBluetooth.toTypedArray())
            return
        }

        bluetoothManager.setBluetoothPermissionState(true)
        initBluetooth()

        // 2. Check and request Notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                return
            }
        }

        // 3. Check and request SYSTEM_ALERT_WINDOW (Overlay) permission
        if (!android.provider.Settings.canDrawOverlays(this)) {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun initBluetooth() {
        if (bluetoothManager.connectionState.value || bluetoothManager.reconnectingState.value) {
            return
        }
        val pairedDevices = bluetoothManager.getPairedDevices()
        val targetDevice = pairedDevices.firstOrNull { it.name?.contains("MOONDROP", ignoreCase = true) == true }
            ?: pairedDevices.firstOrNull()
        targetDevice?.let {
            bluetoothManager.connect(it)
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        volumeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
        volumeReceiver = null
    }

    companion object {
        var isAppInForeground = false
    }
}
