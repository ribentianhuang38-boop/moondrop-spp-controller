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
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            bluetoothManager.setBluetoothPermissionState(true)
            initBluetooth()
        } else {
            bluetoothManager.setBluetoothPermissionState(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val adapter = (getSystemService(AndroidBluetoothManager::class.java))?.adapter
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        bluetoothManager = BluetoothManager(this, adapter, audioManager)

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
            checkPermissions()
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            bluetoothManager.setBluetoothPermissionState(false)
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            bluetoothManager.setBluetoothPermissionState(true)
            initBluetooth()
        }
    }

    private fun initBluetooth() {
        val pairedDevices = bluetoothManager.getPairedDevices()
        val targetDevice = pairedDevices.firstOrNull { it.name?.contains("MOONDROP", ignoreCase = true) == true }
            ?: pairedDevices.firstOrNull()
        targetDevice?.let {
            bluetoothManager.connect(it)
        }
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
}
