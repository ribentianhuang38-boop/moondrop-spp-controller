package com.moondrop.controller.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moondrop.controller.R
import com.moondrop.controller.bluetooth.BandConfig
import com.moondrop.controller.bluetooth.BluetoothManager
import com.moondrop.controller.ui.components.GlassPanel
import com.moondrop.controller.ui.components.PulsingDot
import com.moondrop.controller.ui.components.TechLabel
import com.moondrop.controller.ui.theme.MechaColors
import com.moondrop.controller.ui.theme.MechaTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Non-linear Easing Constants ───────────────────────────────
val MechaDecelEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)  // ultra-smooth decel
val MechaAccelEasing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(bluetoothManager: BluetoothManager) {
    val isConnected by bluetoothManager.connectionState.collectAsState()
    val isReconnecting by bluetoothManager.reconnectingState.collectAsState()
    val autoReconnect by bluetoothManager.autoReconnect.collectAsState()
    val connectedDevice by bluetoothManager.deviceState.collectAsState()
    val logs by bluetoothManager.logFlow.collectAsState()
    
    val activeAnc by bluetoothManager.ancMode.collectAsState()
    val activeGain by bluetoothManager.gainMode.collectAsState()
    val activePreset by bluetoothManager.presetMode.collectAsState()
    
    val systemVolume by bluetoothManager.systemVolume.collectAsState()
    val maxVolume by bluetoothManager.maxVolume.collectAsState()

    val batteryCase by bluetoothManager.batteryCase.collectAsState()
    val batteryLeft by bluetoothManager.batteryLeft.collectAsState()
    val batteryRight by bluetoothManager.batteryRight.collectAsState()
    
    val pairedDevices = remember { bluetoothManager.getPairedDevices() }
    var selectedDevice by remember { 
        mutableStateOf<BluetoothDevice?>(
            pairedDevices.firstOrNull { it.name?.contains("MOONDROP", ignoreCase = true) == true } 
                ?: pairedDevices.firstOrNull()
        ) 
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    var customHex by remember { mutableStateOf("") }
    var centralMac by remember { mutableStateOf("90f052c47271") }
    var showPEQEditor by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    
    // Popup window state
    var showConnectionPopup by remember { mutableStateOf(false) }
    
    // EQ variables
    val defaultBands = remember {
        listOf(
            BandConfig(31, 1.0f, 13, 0.0f),
            BandConfig(62, 1.0f, 13, 0.0f),
            BandConfig(125, 1.0f, 13, 0.0f),
            BandConfig(250, 1.0f, 13, 0.0f),
            BandConfig(500, 1.0f, 13, 0.0f),
            BandConfig(1000, 1.0f, 13, 0.0f),
            BandConfig(2000, 1.0f, 13, 0.0f),
            BandConfig(4000, 1.0f, 13, 0.0f),
            BandConfig(8000, 1.0f, 13, 0.0f),
            BandConfig(16000, 1.0f, 13, 0.0f)
        )
    }
    val eqBands = remember { mutableStateListOf<BandConfig>().apply { addAll(defaultBands) } }
    var preGain by remember { mutableStateOf(-3.0f) }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Trigger pop-up on successful connection
    LaunchedEffect(isConnected) {
        if (isConnected) {
            delay(300)
            showConnectionPopup = true
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MechaColors.surface)
    ) {
        // Tech grid lines decoration background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val step = 80.dp.toPx()
            val lineColor = MechaColors.secondaryDim.copy(alpha = 0.03f)
            
            // Vertical lines
            var x = 0f
            while (x < w) {
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1.dp.toPx()
                )
                x += step
            }
            
            // Horizontal lines
            var y = 0f
            while (y < h) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += step
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ─── Header / Top Bar ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                MechaColors.secondary.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { dropdownExpanded = true }
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MechaColors.primaryContainer)
                            .align(Alignment.CenterVertically)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UNIT-01 // HUB",
                        style = MechaTypography.technicalLabelLarge,
                        color = MechaColors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "▼",
                        fontSize = 8.sp,
                        color = MechaColors.primaryContainer.copy(alpha = 0.6f)
                    )

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(MechaColors.surfaceContainerHigh)
                    ) {
                        if (pairedDevices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No paired devices found", color = MechaColors.secondary) },
                                onClick = {}
                            )
                        } else {
                            pairedDevices.forEach { device ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(device.name ?: "Unknown Device", color = MechaColors.onSurface, style = MechaTypography.bodyMedium)
                                            Text(device.address, color = MechaColors.secondaryDim, style = MechaTypography.technicalLabel)
                                        }
                                    },
                                    onClick = {
                                        selectedDevice = device
                                        dropdownExpanded = false
                                        bluetoothManager.connect(device)
                                    }
                                )
                            }
                        }
                    }
                }

                // Sync status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConnected) {
                        PulsingDot(color = MechaColors.primaryContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE // SYNC",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.primaryContainer
                        )
                    } else if (isReconnecting) {
                        PulsingDot(color = Color.Yellow)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RECONNECTING",
                            style = MechaTypography.technicalLabel,
                            color = Color.Yellow
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MechaColors.secondaryDim)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LINK // LOST",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.secondaryDim
                        )
                    }
                }
            }

            // ─── Main Content Scrollable Area ─────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Hero Visualizer Card ─────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MechaColors.glassBorder, RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MechaColors.primaryContainer.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                        .clickable { showConnectionPopup = true }
                ) {
                    // Chamfered corner decoration using drawing path
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            // Top left corner decoration
                            moveTo(0f, 15f)
                            lineTo(15f, 0f)
                            moveTo(0f, 0f)
                            lineTo(0f, 20f)
                            moveTo(0f, 0f)
                            lineTo(20f, 0f)
                            
                            // Bottom right corner decoration
                            moveTo(w, h - 15f)
                            lineTo(w - 15f, h)
                            moveTo(w, h)
                            lineTo(w, h - 20f)
                            moveTo(w, h)
                            lineTo(w - 20f, h)
                        }
                        drawPath(
                            path = path,
                            color = MechaColors.primaryContainer,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    Text(
                        text = "ASSET // VISUALIZER",
                        style = MechaTypography.technicalLabel,
                        color = MechaColors.secondary.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 16.dp, start = 16.dp)
                    )

                    // Hero Render Image
                    Image(
                        painter = painterResource(id = R.drawable.headphone_hero),
                        contentDescription = "Moondrop Ultrasonic Render",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 24.dp)
                            .align(Alignment.Center)
                    )

                    // Battery tags overlaid on visualizer
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BatteryIndicator(label = "CASE", percentage = batteryCase)
                        BatteryIndicator(label = "L", percentage = batteryLeft)
                        BatteryIndicator(label = "R", percentage = batteryRight)
                    }
                }

                // ─── Noise Control Mode Card (Env // Mode) ────────────
                GlassPanel(
                    isActive = isConnected
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ENV // MODE",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.secondary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "SELECTIVE // ISOLATION",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.primaryContainer
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .background(
                                color = MechaColors.surfaceContainerLowest.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val modes = listOf("ANC", "Transparency", "Normal")
                        val modeLabels = mapOf("ANC" to "ANC", "Transparency" to "TRANS", "Normal" to "OFF")
                        
                        modes.forEach { mode ->
                            val active = activeAnc == mode
                            val animBgColor by animateColorAsState(
                                targetValue = if (active) MechaColors.primaryContainer.copy(alpha = 0.15f) else Color.Transparent,
                                animationSpec = tween(300, easing = MechaDecelEasing),
                                label = "modeBg"
                            )
                            val animBorderColor by animateColorAsState(
                                targetValue = if (active) MechaColors.activeGlowBorder else Color.Transparent,
                                animationSpec = tween(300, easing = MechaDecelEasing),
                                label = "modeBorder"
                            )
                            val animTextColor by animateColorAsState(
                                targetValue = if (active) MechaColors.primary else MechaColors.secondary.copy(alpha = 0.5f),
                                animationSpec = tween(200, easing = MechaDecelEasing),
                                label = "modeText"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(animBgColor)
                                    .border(1.dp, animBorderColor, RoundedCornerShape(6.dp))
                                    .clickable { bluetoothManager.setAncMode(mode) }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeLabels[mode] ?: mode,
                                    style = MechaTypography.buttonText,
                                    color = animTextColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ─── Amp Gain Card (Amp // Gain) ──────────────────────
                GlassPanel(
                    isActive = isConnected
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AMP // GAIN",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.secondary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "DB // ADJUSTMENT",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.secondary.copy(alpha = 0.4f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val gains = listOf("Low", "Medium", "High")
                        
                        gains.forEach { gain ->
                            val active = activeGain == gain
                            val animBgColor by animateColorAsState(
                                targetValue = if (active) MechaColors.primaryContainer.copy(alpha = 0.15f) else Color.Transparent,
                                animationSpec = tween(300, easing = MechaDecelEasing),
                                label = "gainBg"
                            )
                            val animBorderColor by animateColorAsState(
                                targetValue = if (active) MechaColors.activeGlowBorder else MechaColors.secondary.copy(alpha = 0.2f),
                                animationSpec = tween(300, easing = MechaDecelEasing),
                                label = "gainBorder"
                            )
                            val animTextColor by animateColorAsState(
                                targetValue = if (active) MechaColors.primary else MechaColors.secondary.copy(alpha = 0.6f),
                                animationSpec = tween(200, easing = MechaDecelEasing),
                                label = "gainText"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(animBgColor)
                                    .border(1.dp, animBorderColor, RoundedCornerShape(6.dp))
                                    .clickable { bluetoothManager.setGainMode(gain) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = gain.uppercase(),
                                    style = MechaTypography.technicalLabel,
                                    color = animTextColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ─── Volume Output Card (Vol // Output) ───────────────
                GlassPanel(
                    isActive = isConnected
                ) {
                    val volPercent = if (maxVolume > 0) (systemVolume * 100) / maxVolume else 0
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VOL // OUTPUT",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.secondary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "$volPercent%",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.primaryContainer
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                    ) {
                        // Custom styled slider
                        Slider(
                            value = systemVolume.toFloat(),
                            onValueChange = { bluetoothManager.setSystemVolume(it.toInt()) },
                            valueRange = 0f..maxVolume.toFloat(),
                            steps = if (maxVolume > 1) maxVolume - 1 else 0,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MechaColors.primaryContainer,
                                inactiveTrackColor = MechaColors.surfaceContainerHighest,
                                thumbColor = MechaColors.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ─── EQ Card (Eq // Equalizer) ────────────────────────
                GlassPanel(
                    isActive = isConnected
                ) {
                    val activePresetLabel = when (activePreset) {
                        0 -> "DEFAULT"
                        1 -> "BASSHEAD"
                        2 -> "CRISP"
                        63 -> "CUSTOM_MAP"
                        else -> "PRESET_$activePreset"
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EQ // EQUALIZER",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.secondary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "PRESET: $activePresetLabel",
                            style = MechaTypography.technicalLabel,
                            color = MechaColors.primaryContainer
                        )
                    }

                    // Interactive/Animated mini EQ waves representation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 16.dp)
                            .background(
                                color = MechaColors.surfaceContainerLowest.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val barCount = 10
                            for (i in 0 until barCount) {
                                val gainVal = if (i < eqBands.size) eqBands[i].gain else 0f
                                // Scale bar height based on gain: -12..12dB mapped to 20%..100%
                                val normalizedHeight = ((gainVal + 12f) / 24f).coerceIn(0.15f, 1f)
                                val animatedHeight by animateFloatAsState(
                                    targetValue = normalizedHeight,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "eqBar"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(animatedHeight)
                                        .padding(horizontal = 3.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    MechaColors.primaryContainer,
                                                    MechaColors.primaryContainer.copy(alpha = 0.2f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val presets = listOf(
                            0 to "Default",
                            1 to "Basshead",
                            2 to "Crisp",
                            63 to "Custom"
                        )
                        presets.forEach { (id, name) ->
                            val active = activePreset == id
                            val animBgColor by animateColorAsState(
                                targetValue = if (active) MechaColors.primaryContainer.copy(alpha = 0.15f) else Color.Transparent,
                                label = "presetBg"
                            )
                            val animBorderColor by animateColorAsState(
                                targetValue = if (active) MechaColors.activeGlowBorder else MechaColors.secondary.copy(alpha = 0.15f),
                                label = "presetBorder"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(animBgColor)
                                    .border(1.dp, animBorderColor, RoundedCornerShape(6.dp))
                                    .clickable { bluetoothManager.selectEQPreset(id) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.uppercase(),
                                    style = MechaTypography.technicalLabel,
                                    color = if (active) MechaColors.primary else MechaColors.secondary.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Expand PEQ config button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MechaColors.surfaceContainerHigh)
                                .border(1.dp, MechaColors.glassBorder, RoundedCornerShape(6.dp))
                                .clickable { showPEQEditor = !showPEQEditor }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showPEQEditor) "HIDE PEQ CONFIGURATION" else "CUSTOM PEQ MAP EDITOR",
                                style = MechaTypography.technicalLabel,
                                color = MechaColors.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Collapsible PEQ Editor
                    AnimatedVisibility(
                        visible = showPEQEditor,
                        enter = expandVertically(animationSpec = tween(400, easing = MechaDecelEasing)),
                        exit = shrinkVertically(animationSpec = tween(300, easing = MechaAccelEasing))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            Divider(color = Color.White.copy(alpha = 0.05f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Pre-Gain slider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "PRE-GAIN: ${String.format("%.1f", preGain)} DB",
                                    color = MechaColors.onSurface,
                                    style = MechaTypography.technicalLabel,
                                    modifier = Modifier.width(130.dp)
                                )
                                Slider(
                                    value = preGain,
                                    onValueChange = { preGain = it },
                                    valueRange = -12f..0f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MechaColors.primaryContainer,
                                        thumbColor = MechaColors.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Editor Headers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("FREQ (HZ)", color = MechaColors.secondaryDim, style = MechaTypography.technicalLabel, modifier = Modifier.weight(1f))
                                Text("Q FACTOR", color = MechaColors.secondaryDim, style = MechaTypography.technicalLabel, modifier = Modifier.weight(1.5f))
                                Text("GAIN (DB)", color = MechaColors.secondaryDim, style = MechaTypography.technicalLabel, modifier = Modifier.weight(2.5f))
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 10 bands list
                            eqBands.forEachIndexed { index: Int, band: BandConfig ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Freq input
                                    var freqStr by remember(band.freq) { mutableStateOf(band.freq.toString()) }
                                    OutlinedTextField(
                                        value = freqStr,
                                        onValueChange = { 
                                            freqStr = it
                                            val newFreq = it.toIntOrNull() ?: band.freq
                                            eqBands[index] = band.copy(freq = newFreq)
                                        },
                                        textStyle = MechaTypography.technicalLabel,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MechaColors.onSurface,
                                            unfocusedTextColor = MechaColors.onSurface,
                                            focusedBorderColor = MechaColors.primaryContainer,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .padding(end = 4.dp)
                                    )
                                    
                                    // Q input
                                    var qStr by remember(band.q) { mutableStateOf(String.format("%.2f", band.q)) }
                                    OutlinedTextField(
                                        value = qStr,
                                        onValueChange = { 
                                            qStr = it
                                            val newQ = it.toFloatOrNull() ?: band.q
                                            eqBands[index] = band.copy(q = newQ)
                                        },
                                        textStyle = MechaTypography.technicalLabel,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MechaColors.onSurface,
                                            unfocusedTextColor = MechaColors.onSurface,
                                            focusedBorderColor = MechaColors.primaryContainer,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                                        ),
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .height(42.dp)
                                            .padding(horizontal = 4.dp)
                                    )
                                    
                                    // Gain Slider
                                    Row(
                                        modifier = Modifier.weight(2.5f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Slider(
                                            value = band.gain,
                                            onValueChange = { eqBands[index] = band.copy(gain = it) },
                                            valueRange = -12f..12f,
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = MechaColors.primaryContainer,
                                                thumbColor = MechaColors.primary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${String.format("%.1f", band.gain)}DB",
                                            color = MechaColors.onSurface,
                                            style = MechaTypography.technicalLabel,
                                            modifier = Modifier
                                                .width(42.dp)
                                                .padding(start = 4.dp),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { bluetoothManager.sendCustomEQ(preGain, eqBands) },
                                colors = ButtonDefaults.buttonColors(containerColor = MechaColors.primaryContainer),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "APPLY MAP TO UNIT-01",
                                    color = MechaColors.onPrimaryContainer,
                                    style = MechaTypography.buttonText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ─── Collapsible Advanced Panel ───────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (showAdvanced) "CLOSE TACTICAL CONFIG" else "OPEN ADVANCED TACTICAL LINK",
                        style = MechaTypography.technicalLabel,
                        color = MechaColors.secondary.copy(alpha = 0.4f),
                        modifier = Modifier
                            .clickable { showAdvanced = !showAdvanced }
                            .padding(vertical = 12.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showAdvanced,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Protocol Switch Card
                        GlassPanel {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("PROTOCOL / CODEC SWITCH", style = MechaTypography.technicalLabel, color = MechaColors.secondary.copy(alpha = 0.6f))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        val cleanMac = centralMac.replace(":", "").replace("-", "")
                                        if (cleanMac.length == 12) {
                                            bluetoothManager.sendHex("ff040006001d2a02$cleanMac")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MechaColors.surfaceContainerHigh),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("LDAC", style = MechaTypography.technicalLabel, color = MechaColors.onSurface)
                                }
                                Button(
                                    onClick = { bluetoothManager.sendHex("ff040001001d200401") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MechaColors.surfaceContainerHigh),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("LC3 EN", style = MechaTypography.technicalLabel, color = MechaColors.onSurface)
                                }
                                Button(
                                    onClick = { bluetoothManager.sendHex("ff040001001d200400") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MechaColors.surfaceContainerHigh),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("LC3 DIS", style = MechaTypography.technicalLabel, color = MechaColors.onSurface)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { bluetoothManager.setAutoReconnect(!autoReconnect) }
                                    .padding(16.dp)
                            ) {
                                Checkbox(
                                    checked = autoReconnect,
                                    onCheckedChange = { bluetoothManager.setAutoReconnect(it) },
                                    colors = CheckboxDefaults.colors(checkedColor = MechaColors.primaryContainer, uncheckedColor = MechaColors.secondaryDim)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto-Reconnect after mode switch", color = MechaColors.onSurface, style = MechaTypography.bodySmall)
                            }
                        }

                        // Hex sender / MAC config
                        Row(modifier = Modifier.fillMaxWidth()) {
                            GlassPanel(
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("CENTRAL MAC (FOR LDAC)", color = MechaColors.secondary.copy(alpha = 0.6f), style = MechaTypography.technicalLabel)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = centralMac,
                                        onValueChange = { centralMac = it },
                                        textStyle = MechaTypography.technicalLabel,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MechaColors.primaryContainer,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                            focusedTextColor = MechaColors.onSurface,
                                            unfocusedTextColor = MechaColors.onSurface
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            GlassPanel(
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("RAW HEX SEND", color = MechaColors.secondary.copy(alpha = 0.6f), style = MechaTypography.technicalLabel)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = customHex,
                                        onValueChange = { customHex = it },
                                        textStyle = MechaTypography.technicalLabel,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MechaColors.primaryContainer,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                            focusedTextColor = MechaColors.onSurface,
                                            unfocusedTextColor = MechaColors.onSurface
                                        ),
                                        placeholder = { Text("ff04...", fontSize = 10.sp, color = MechaColors.secondaryDim) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            bluetoothManager.sendHex(customHex)
                                            customHex = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MechaColors.primaryContainer),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("SEND", style = MechaTypography.technicalLabel, color = MechaColors.onPrimaryContainer)
                                    }
                                }
                            }
                        }

                        // Packet Log card
                        GlassPanel {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("LINK PACKET LOG", color = MechaColors.secondary.copy(alpha = 0.6f), style = MechaTypography.technicalLabel)
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .padding(4.dp)
                                ) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(logs) { log ->
                                            val color = when (log.direction) {
                                                "TX" -> MechaColors.primaryContainer
                                                "TX (Offline)" -> MechaColors.secondaryDim
                                                "RX" -> Color(0xFF10B981)
                                                "SUCCESS" -> Color(0xFF10B981)
                                                "ERROR" -> Color(0xFFEF4444)
                                                "WARNING" -> Color(0xFFFBBF24)
                                                else -> MechaColors.onSurface
                                            }
                                            
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                                Text(
                                                    text = "[${log.time}] ",
                                                    color = MechaColors.secondaryDim,
                                                    style = MechaTypography.technicalLabel
                                                )
                                                Text(
                                                    text = "${log.direction}: ${log.message}",
                                                    color = color,
                                                    style = MechaTypography.technicalLabel
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Custom Headphone Dialog/Pop-up Window ────────────────────
        if (showConnectionPopup) {
            HeadphonePopupDialog(
                deviceName = connectedDevice?.name ?: "MOONDROP ULTRASONIC",
                batteryCase = batteryCase,
                batteryLeft = batteryLeft,
                batteryRight = batteryRight,
                onDismiss = { showConnectionPopup = false }
            )
        }
    }
}

@Composable
fun BatteryIndicator(label: String, percentage: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label: ",
            style = MechaTypography.technicalLabel,
            color = MechaColors.secondaryDim
        )
        Text(
            text = "$percentage%",
            style = MechaTypography.technicalLabel,
            color = if (percentage > 20) Color(0xFF10B981) else Color(0xFFEF4444)
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HeadphonePopupDialog(
    deviceName: String,
    batteryCase: Int,
    batteryLeft: Int,
    batteryRight: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        var animateTrigger by remember { mutableStateOf(false) }
        
        LaunchedEffect(Unit) {
            delay(50)
            animateTrigger = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = animateTrigger,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = MechaAccelEasing)
                )
            ) {
                // Pop-up Sheet Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(MechaColors.primaryContainer.copy(alpha = 0.3f), Color.Transparent)
                            ),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .background(MechaColors.surfaceContainerLow)
                        .clickable(enabled = false) {} // Prevent dismiss on sheet click
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header anchor line decoration
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "TACTICAL CONNECTION ESTABLISHED",
                        style = MechaTypography.technicalLabel,
                        color = MechaColors.primaryContainer
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = deviceName.uppercase(),
                        style = MechaTypography.headlineMedium,
                        color = MechaColors.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Floating renders of Left and Right buds side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.headphone_left),
                                contentDescription = "Left Earbud",
                                modifier = Modifier.size(110.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("LEFT UNIT", style = MechaTypography.technicalLabel, color = MechaColors.secondaryDim)
                            Text("$batteryLeft%", style = MechaTypography.technicalLabelLarge, color = MechaColors.onSurface)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.headphone_right),
                                contentDescription = "Right Earbud",
                                modifier = Modifier.size(110.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("RIGHT UNIT", style = MechaTypography.technicalLabel, color = MechaColors.secondaryDim)
                            Text("$batteryRight%", style = MechaTypography.technicalLabelLarge, color = MechaColors.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Case battery status
                    Row(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.headphone_case),
                                contentDescription = "Charging Case",
                                modifier = Modifier.size(45.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("CHARGING CASE", style = MechaTypography.technicalLabel, color = MechaColors.secondaryDim)
                                Text("TACTICAL CARRIER UNIT-01", style = MechaTypography.technicalLabel, fontSize = 8.sp, color = MechaColors.primary.copy(alpha = 0.5f))
                            }
                        }
                        Text("$batteryCase%", style = MechaTypography.dataReadout, color = MechaColors.onSurface)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MechaColors.primaryContainer),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "INITIALIZE CONTROL HUB",
                            color = MechaColors.onPrimaryContainer,
                            style = MechaTypography.buttonText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
