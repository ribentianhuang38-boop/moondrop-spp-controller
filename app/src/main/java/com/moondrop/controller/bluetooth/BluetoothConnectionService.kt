package com.moondrop.controller.bluetooth

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.moondrop.controller.MainActivity
import com.moondrop.controller.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BluetoothConnectionService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var bluetoothManager: BluetoothManager
    private val CHANNEL_ID = "moondrop_controller_status"
    private val NOTIFICATION_ID = 1001

    private var ancReceiver: BroadcastReceiver? = null

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothConnectionService = this@BluetoothConnectionService
        fun getBluetoothManager(): BluetoothManager = bluetoothManager
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize BluetoothManager singleton
        val androidBTManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = androidBTManager.adapter
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        bluetoothManager = BluetoothManager.getInstance(applicationContext, adapter, audioManager)

        // Register receiver for notification action buttons
        val filter = IntentFilter("com.moondrop.controller.ACTION_SET_ANC")
        ancReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val mode = it.getStringExtra("mode") ?: "Normal"
                    bluetoothManager.setAncMode(mode)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ancReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(ancReceiver, filter)
        }

        // Keep updating notification when connection status, device, battery, or ANC changes
        serviceScope.launch {
            combine(
                bluetoothManager.connectionState,
                bluetoothManager.deviceState,
                bluetoothManager.ancMode,
                bluetoothManager.batteryLeft,
                bluetoothManager.batteryRight
            ) { _, _, _, _, _ ->
                Unit
            }.collect {
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceCompat()
        return START_STICKY
    }

    private fun startForegroundServiceCompat() {
        createNotificationChannel()
        val notification = buildStatusNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID, 
                        notification, 
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID, 
                        notification, 
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                }
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Headset Control Panel"
            val descriptionText = "Displays connection status, battery, and ANC controls at the top."
            // Use IMPORTANCE_MAX to request the OS pin the notification at the very top of the status drawer
            val importance = NotificationManager.IMPORTANCE_MAX
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildStatusNotification(): Notification {
        val isConnected = bluetoothManager.connectionState.value
        val batteryL = bluetoothManager.batteryLeft.value
        val batteryR = bluetoothManager.batteryRight.value
        val currentAnc = bluetoothManager.ancMode.value

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val transparencyIntent = Intent("com.moondrop.controller.ACTION_SET_ANC").apply {
            setPackage(packageName)
            putExtra("mode", "Transparency")
        }
        val transparencyPending = PendingIntent.getBroadcast(
            this,
            1,
            transparencyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ancIntent = Intent("com.moondrop.controller.ACTION_SET_ANC").apply {
            setPackage(packageName)
            putExtra("mode", "ANC")
        }
        val ancPending = PendingIntent.getBroadcast(
            this,
            2,
            ancIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val normalIntent = Intent("com.moondrop.controller.ACTION_SET_ANC").apply {
            setPackage(packageName)
            putExtra("mode", "Normal")
        }
        val normalPending = PendingIntent.getBroadcast(
            this,
            3,
            normalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ancStr = when (currentAnc) {
            "ANC" -> "降噪模式"
            "Transparency" -> "通透模式"
            "Normal" -> "普通模式"
            else -> "普通模式"
        }

        val title = if (isConnected) {
            "${bluetoothManager.deviceState.value?.name ?: "MOONDROP ULTRASONIC"} 已连接"
        } else {
            "MOONDROP 耳机未连接"
        }
        
        val content = if (isConnected) {
            "左耳: $batteryL% | 右耳: $batteryR% | 状态: $ancStr"
        } else {
            "请打开蓝牙并连接您的耳机"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOnlyAlertOnce(true)
            .setSortKey("001")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .apply {
                if (isConnected) {
                    addAction(0, "通透", transparencyPending)
                    addAction(0, "降噪", ancPending)
                    addAction(0, "关闭", normalPending)
                }
            }
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(NOTIFICATION_ID, buildStatusNotification())
        } catch (e: Exception) {
            // Log warning
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        ancReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {}
        }
    }
}
