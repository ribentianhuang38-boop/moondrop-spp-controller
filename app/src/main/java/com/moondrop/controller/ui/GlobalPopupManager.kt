package com.moondrop.controller.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.moondrop.controller.bluetooth.BluetoothManager
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.moondrop.controller.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object GlobalPopupManager {
    private var currentView: View? = null
    private var lifecycleOwner: FloatingLifecycleOwner? = null

    fun showPopup(context: Context, bluetoothManager: BluetoothManager) {
        // If overlays are not permitted, do nothing
        if (!Settings.canDrawOverlays(context)) {
            return
        }

        // Avoid duplicate popups
        if (currentView != null) {
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val composeView = ComposeView(context)

        val owner = FloatingLifecycleOwner()
        owner.onCreate()
        owner.onStart()
        lifecycleOwner = owner

        composeView.setViewTreeLifecycleOwner(owner)
        composeView.setViewTreeSavedStateRegistryOwner(owner)
        composeView.setViewTreeViewModelStoreOwner(owner)

        composeView.setContent {
            val batteryLeft by bluetoothManager.batteryLeft.collectAsState()
            val batteryRight by bluetoothManager.batteryRight.collectAsState()
            val activeAnc by bluetoothManager.ancMode.collectAsState()

            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                visible = true
            }

            val coroutineScope = rememberCoroutineScope()
            val handleDismiss = {
                coroutineScope.launch {
                    visible = false
                    delay(300)
                    dismiss()
                }
            }

            MaterialTheme {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { handleDismiss() }
                    ) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)
                            ) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .width(360.dp)
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 80.dp)
                        ) {
                            Box {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black, RoundedCornerShape(24.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                        .clickable(enabled = true, onClick = {})
                                        .padding(top = 48.dp)
                                        .padding(bottom = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(vertical = 12.dp)
                                            .width(36.dp)
                                            .height(4.dp)
                                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "MOONDROP ULTRASONIC",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "已连接",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )

                                    Row(
                                        modifier = Modifier
                                            .padding(vertical = 24.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.headphone_left),
                                                contentDescription = "Left Earbud",
                                                modifier = Modifier.height(100.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "L  $batteryLeft%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.headphone_right),
                                                contentDescription = "Right Earbud",
                                                modifier = Modifier.height(100.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "R  $batteryRight%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 40.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "NOISE CONTROL",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 10.sp,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        val ancItems = listOf("通透", "降噪", "关闭")
                                        val currentAncIndex = when (activeAnc) {
                                            "Transparency" -> 0
                                            "ANC" -> 1
                                            else -> 2
                                        }

                                        MinimalSegmentedControl(
                                            items = ancItems,
                                            selectedIndex = currentAncIndex,
                                            onItemSelection = { index ->
                                                val mode = when (index) {
                                                    0 -> "Transparency"
                                                    1 -> "ANC"
                                                    else -> "Normal"
                                                }
                                                bluetoothManager.setAncMode(mode)
                                            },
                                            isDark = true
                                        )

                                        Spacer(modifier = Modifier.height(28.dp))

                                        Button(
                                            onClick = { handleDismiss() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Text("DONE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }

                                Image(
                                    painter = painterResource(id = R.drawable.ic_logo_transparent),
                                    contentDescription = "Moondrop Logo",
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = 24.dp, y = (-40).dp)
                                        .size(80.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(composeView, params)
            currentView = composeView
        } catch (e: Exception) {
            owner.onDestroy()
            lifecycleOwner = null
        }
    }

    fun dismiss() {
        val view = currentView ?: return
        val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {}
        currentView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }
}
