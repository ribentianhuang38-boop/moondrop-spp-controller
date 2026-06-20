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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moondrop.controller.R
import com.moondrop.controller.bluetooth.BandConfig
import com.moondrop.controller.bluetooth.BluetoothManager
import kotlinx.coroutines.delay
import android.app.Activity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.saveable.rememberSaveable

// ─── Design System Tokens ──────────────────────────────────────
val BgWhite = Color(0xFFFFFFFF)        // Pure white background
val TextPrimary = Color(0xFF111111)    // Primary text (charcoal)
val TextSecondary = Color(0xFF777777)  // Secondary text (grey)
val BorderLight = Color(0xFFE5E5EA)    // Hairline border
val ActiveAccent = Color(0xFF00FF66)   // Fluorescent green connection dot
val RoundedCornerDefault = RoundedCornerShape(16.dp) // Smooth premium corner radius (superellipse profile)

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

    val batteryLeft by bluetoothManager.batteryLeft.collectAsState()
    val batteryRight by bluetoothManager.batteryRight.collectAsState()
    val hasPermission by bluetoothManager.bluetoothPermissionState.collectAsState()
    
    val pairedDevices = remember(hasPermission) { 
        if (hasPermission) bluetoothManager.getPairedDevices() else emptyList() 
    }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    LaunchedEffect(pairedDevices) {
        if (selectedDevice == null && pairedDevices.isNotEmpty()) {
            selectedDevice = pairedDevices.firstOrNull { it.name?.contains("MOONDROP", ignoreCase = true) == true }
                ?: pairedDevices.firstOrNull()
        }
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    var customHex by remember { mutableStateOf("") }
    var centralMac by remember { mutableStateOf("90f052c47271") }
    
    var showPEQEditor by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    
    // Splash screen states
    var isSplashScreenActive by rememberSaveable { mutableStateOf(true) }
    var splashVisible by rememberSaveable { mutableStateOf(true) }
    var splashTextVisible by remember { mutableStateOf(false) }
    
    val splashAlpha by animateFloatAsState(
        targetValue = if (splashVisible) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )
    val splashTextAlpha by animateFloatAsState(
        targetValue = if (splashTextVisible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "splashTextAlpha"
    )

    val view = LocalView.current
    val context = view.context
    if (!view.isInEditMode && context is Activity) {
        SideEffect {
            val window = context.window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
    }

    // Connection Pop-up dialog states
    var showConnectionPopup by remember { mutableStateOf(false) }
    var hasShownPopupForCurrentConnection by remember { mutableStateOf(false) }
    LaunchedEffect(isConnected) {
        if (isConnected) {
            if (!hasShownPopupForCurrentConnection) {
                showConnectionPopup = true
                hasShownPopupForCurrentConnection = true
            }
        } else {
            hasShownPopupForCurrentConnection = false
            showConnectionPopup = false
        }
    }

    val savedPreGain = remember { bluetoothManager.getSavedPreGain() }
    val savedGains = remember { bluetoothManager.getSavedBandGains() }
    
    val defaultBands = remember {
        listOf(
            BandConfig(31, 1.0f, 13, savedGains.getOrElse(0) { 0.0f }),
            BandConfig(62, 1.0f, 13, savedGains.getOrElse(1) { 0.0f }),
            BandConfig(125, 1.0f, 13, savedGains.getOrElse(2) { 0.0f }),
            BandConfig(250, 1.0f, 13, savedGains.getOrElse(3) { 0.0f }),
            BandConfig(500, 1.0f, 13, savedGains.getOrElse(4) { 0.0f }),
            BandConfig(1000, 1.0f, 13, savedGains.getOrElse(5) { 0.0f }),
            BandConfig(2000, 1.0f, 13, savedGains.getOrElse(6) { 0.0f }),
            BandConfig(4000, 1.0f, 13, savedGains.getOrElse(7) { 0.0f }),
            BandConfig(8000, 1.0f, 13, savedGains.getOrElse(8) { 0.0f }),
            BandConfig(16000, 1.0f, 13, savedGains.getOrElse(9) { 0.0f })
        )
    }
    val eqBands = remember { mutableStateListOf<BandConfig>().apply { addAll(defaultBands) } }
    var preGain by remember { mutableStateOf(savedPreGain) }
    
    val listState = rememberLazyListState()

    // Sync local eqBands when activePreset changes (Standard=6, Monitor=2, Dynamic=3, 89XX=4, 336XX=5)
    LaunchedEffect(activePreset) {
        if (activePreset != 63) {
            val presetBands = when (activePreset) {
                6 -> listOf( // 标准 (VDSF Target)
                    BandConfig(31, 1.0f, 13, 1.5f),
                    BandConfig(62, 1.0f, 13, 1.0f),
                    BandConfig(125, 1.0f, 13, 0.5f),
                    BandConfig(250, 1.0f, 13, 0.0f),
                    BandConfig(500, 1.0f, 13, 0.0f),
                    BandConfig(1000, 1.0f, 13, 0.0f),
                    BandConfig(2000, 1.0f, 13, 1.0f),
                    BandConfig(4000, 1.0f, 13, 2.0f),
                    BandConfig(8000, 1.0f, 13, 1.5f),
                    BandConfig(16000, 1.0f, 13, 0.5f)
                )
                2 -> listOf( // 监听 (Monitor Target - flat)
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
                3 -> listOf( // 动感 (Dynamic Target - V-shaped)
                    BandConfig(31, 1.0f, 13, 5.0f),
                    BandConfig(62, 1.0f, 13, 4.0f),
                    BandConfig(125, 1.0f, 13, 2.0f),
                    BandConfig(250, 1.0f, 13, 0.0f),
                    BandConfig(500, 1.0f, 13, -0.5f),
                    BandConfig(1000, 1.0f, 13, 0.0f),
                    BandConfig(2000, 1.0f, 13, 1.5f),
                    BandConfig(4000, 1.0f, 13, 3.5f),
                    BandConfig(8000, 1.0f, 13, 4.0f),
                    BandConfig(16000, 1.0f, 13, 2.0f)
                )
                4 -> listOf( // 89XX (Classic warm curve)
                    BandConfig(31, 1.0f, 13, 2.0f),
                    BandConfig(62, 1.0f, 13, 1.5f),
                    BandConfig(125, 1.0f, 13, 1.0f),
                    BandConfig(250, 1.0f, 13, 0.5f),
                    BandConfig(500, 1.0f, 13, 1.0f),
                    BandConfig(1000, 1.0f, 13, 1.5f),
                    BandConfig(2000, 1.0f, 13, 2.0f),
                    BandConfig(4000, 1.0f, 13, 1.0f),
                    BandConfig(8000, 1.0f, 13, 0.0f),
                    BandConfig(16000, 1.0f, 13, -1.0f)
                )
                5 -> listOf( // 336XX (High detail treble curve)
                    BandConfig(31, 1.0f, 13, 0.0f),
                    BandConfig(62, 1.0f, 13, 0.0f),
                    BandConfig(125, 1.0f, 13, -1.0f),
                    BandConfig(250, 1.0f, 13, -1.0f),
                    BandConfig(500, 1.0f, 13, 0.0f),
                    BandConfig(1000, 1.0f, 13, 1.0f),
                    BandConfig(2000, 1.0f, 13, 2.5f),
                    BandConfig(4000, 1.0f, 13, 4.0f),
                    BandConfig(8000, 1.0f, 13, 5.0f),
                    BandConfig(16000, 1.0f, 13, 3.0f)
                )
                else -> defaultBands
            }
            eqBands.clear()
            eqBands.addAll(presetBands)
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { dropdownExpanded = true }
                        ) {
                            Text(
                                text = connectedDevice?.name ?: selectedDevice?.name ?: "MOONDROP ULTRASONIC",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Device",
                                tint = TextSecondary,
                                modifier = Modifier.padding(start = 4.dp).size(18.dp)
                            )
                            
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(BgWhite)
                            ) {
                                if (pairedDevices.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("未发现配对设备", color = TextSecondary, fontSize = 13.sp) },
                                        onClick = {}
                                    )
                                } else {
                                    pairedDevices.forEach { device ->
                                        DropdownMenuItem(
                                            text = { 
                                                Column {
                                                    Text(device.name ?: "未知设备", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(device.address, color = TextSecondary, fontSize = 10.sp)
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
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            val statusDotColor = if (isConnected) ActiveAccent else if (isReconnecting) Color(0xFFFF9500) else TextSecondary
                            val statusText = if (isConnected) "CONNECTED" else if (isReconnecting) "RECONNECTING" else "DISCONNECTED"
                            
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            if (isConnected) {
                                Text(
                                    text = "DISCONNECT",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { bluetoothManager.disconnect() }
                                        .padding(vertical = 4.dp)
                                )
                            } else if (selectedDevice != null) {
                                Text(
                                    text = "CONNECT",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { bluetoothManager.connect(selectedDevice!!) }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgWhite)
                )
            }
        ) { innerPadding ->
            // ─── 3D Vertical Flip Transition when Connection State Switches ───────────────────
            val flipRotationX by animateFloatAsState(
                targetValue = if (isConnected) 0f else 180f,
                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)),
                label = "dashboardFlip"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .graphicsLayer {
                        rotationX = flipRotationX
                        cameraDistance = 12f * density
                    }
            ) {
                if (flipRotationX <= 90f) {
                    // Front Side: Connected Dashboard
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BgWhite)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 40.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Earbud Display (Clean cropped transparent renderings side-by-side)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
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
                                    modifier = Modifier
                                        .height(130.dp)
                                        .padding(bottom = 16.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Text(
                                    text = "L  $batteryLeft%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.headphone_right),
                                    contentDescription = "Right Earbud",
                                    modifier = Modifier
                                        .height(130.dp)
                                        .padding(bottom = 16.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Text(
                                    text = "R  $batteryRight%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Noise Control
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "NOISE CONTROL",
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
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
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // Gain Mode
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "GAIN MODE",
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val gainItems = listOf("低增益", "中增益", "高增益")
                            val currentGainIndex = when (activeGain) {
                                "Low" -> 0
                                "Medium" -> 1
                                else -> 2
                            }
                            
                            MinimalSegmentedControl(
                                items = gainItems,
                                selectedIndex = currentGainIndex,
                                onItemSelection = { index ->
                                    val mode = when (index) {
                                        0 -> "Low"
                                        1 -> "Medium"
                                        else -> "High"
                                    }
                                    bluetoothManager.setGainMode(mode)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // Equalizer (Vector line + 3x2 Grid Presets)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "EQUALIZER",
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            EQVisualizer(bands = eqBands)
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 3x2 Grid matching the Bluetrum preset index
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PresetButton(name = "标准 (VDSF)", isSelected = activePreset == 6, modifier = Modifier.weight(1f)) {
                                        bluetoothManager.selectEQPreset(6)
                                    }
                                    PresetButton(name = "监听", isSelected = activePreset == 2, modifier = Modifier.weight(1f)) {
                                        bluetoothManager.selectEQPreset(2)
                                    }
                                    PresetButton(name = "动感", isSelected = activePreset == 3, modifier = Modifier.weight(1f)) {
                                        bluetoothManager.selectEQPreset(3)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PresetButton(name = "89XX", isSelected = activePreset == 4, modifier = Modifier.weight(1f)) {
                                        bluetoothManager.selectEQPreset(4)
                                    }
                                    PresetButton(name = "336XX", isSelected = activePreset == 5, modifier = Modifier.weight(1f)) {
                                        bluetoothManager.selectEQPreset(5)
                                    }
                                    PresetButton(name = "自定义", isSelected = activePreset == 63, modifier = Modifier.weight(1f)) {
                                        bluetoothManager.selectEQPreset(63)
                                        showPEQEditor = true
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (showPEQEditor) "HIDE PARAMETRIC EQ EDIT" else "SHOW PARAMETRIC EQ EDIT",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { showPEQEditor = !showPEQEditor }
                                    .padding(vertical = 4.dp)
                            )

                            AnimatedVisibility(
                                visible = showPEQEditor,
                                enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(),
                                exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp)
                                ) {
                                    // Pre-Gain
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "PRE-GAIN: ${String.format("%.1f", preGain)} dB",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.width(110.dp)
                                        )
                                        Slider(
                                            value = preGain,
                                            onValueChange = { preGain = it },
                                            valueRange = -12f..0f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = TextPrimary,
                                                activeTrackColor = TextPrimary,
                                                inactiveTrackColor = BorderLight
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // 10 bands
                                    eqBands.forEachIndexed { index, band ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${band.freq} Hz",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(60.dp)
                                            )
                                            Slider(
                                                value = band.gain,
                                                onValueChange = { newGain ->
                                                    eqBands[index] = band.copy(gain = newGain)
                                                },
                                                valueRange = -12f..12f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = TextPrimary,
                                                    activeTrackColor = TextPrimary,
                                                    inactiveTrackColor = BorderLight
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${String.format("%.1f", band.gain)} dB",
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(50.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Button(
                                        onClick = { 
                                            bluetoothManager.sendCustomEQ(preGain, eqBands) 
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                                        shape = RoundedCornerDefault,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("APPLY PARAMETRIC EQ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // Volume
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val volPercent = if (maxVolume > 0) (systemVolume * 100) / maxVolume else 0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                  Text(
                                      text = "VOLUME",
                                      fontWeight = FontWeight.Bold,
                                      color = TextSecondary,
                                      fontSize = 10.sp,
                                      letterSpacing = 1.sp
                                  )
                                  Text(
                                      text = "$volPercent%",
                                      fontWeight = FontWeight.Bold,
                                      color = TextPrimary,
                                      fontSize = 12.sp
                                  )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = systemVolume.toFloat(),
                                onValueChange = { bluetoothManager.setSystemVolume(it.toInt()) },
                                valueRange = 0f..maxVolume.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = TextPrimary,
                                    activeTrackColor = TextPrimary,
                                    inactiveTrackColor = BorderLight
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // Debug Options Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (showAdvanced) "HIDE DEBUG OPTIONS" else "SHOW DEBUG OPTIONS",
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clickable { showAdvanced = !showAdvanced }
                                    .padding(8.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = showAdvanced,
                            enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(),
                            exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Protocol / Codec Toggles (Single merged switch button for each)
                                Column {
                                    Text("PROTOCOL / CODEC SWITCH", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val ldacEnabled by bluetoothManager.ldacEnabled.collectAsState()
                                        val lc3Enabled by bluetoothManager.lc3Enabled.collectAsState()
                                        
                                        // LDAC Toggle Button
                                        PresetButton(
                                            name = if (ldacEnabled) "LDAC [ON]" else "LDAC [OFF]",
                                            isSelected = ldacEnabled,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            bluetoothManager.toggleLdac(centralMac)
                                        }
                                        
                                        // LC3 Toggle Button
                                        PresetButton(
                                            name = if (lc3Enabled) "LC3 [ON]" else "LC3 [OFF]",
                                            isSelected = lc3Enabled,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            bluetoothManager.toggleLc3()
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { bluetoothManager.setAutoReconnect(!autoReconnect) }
                                    ) {
                                        Checkbox(
                                            checked = autoReconnect,
                                            onCheckedChange = { bluetoothManager.setAutoReconnect(it) },
                                            colors = CheckboxDefaults.colors(checkedColor = TextPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Auto-reconnect on codec change", color = TextPrimary, fontSize = 12.sp)
                                    }
                                }

                                // MAC
                                Column {
                                    Text("TRANSMITTER MAC (FOR LDAC)", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = centralMac,
                                        onValueChange = { centralMac = it },
                                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = TextPrimary,
                                            unfocusedBorderColor = BorderLight
                                        ),
                                        shape = RoundedCornerDefault
                                    )
                                }

                                // Hex Sender
                                Column {
                                    Text("RAW HEX COMMAND SENDER", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customHex,
                                            onValueChange = { customHex = it },
                                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                            placeholder = { Text("ff04...", color = TextSecondary, fontSize = 13.sp) },
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = TextPrimary,
                                                unfocusedBorderColor = BorderLight
                                            ),
                                            shape = RoundedCornerDefault
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                bluetoothManager.sendHex(customHex)
                                                customHex = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                                            shape = RoundedCornerDefault
                                        ) {
                                            Text("SEND", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }

                                // Logs
                                Column {
                                    Text("TRANSMISSION LOGS", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .border(1.dp, BorderLight, RoundedCornerDefault)
                                            .background(Color(0xFFFAFAFA))
                                            .padding(8.dp)
                                    ) {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(logs) { log ->
                                                val logColor = when (log.direction) {
                                                    "TX" -> TextPrimary
                                                    "RX" -> Color(0xFF34C759)
                                                    "ERROR" -> Color(0xFFFF3B30)
                                                    else -> TextSecondary
                                                }
                                                
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "[${log.time}] ",
                                                        color = TextSecondary,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp
                                                    )
                                                    Text(
                                                        text = "${log.direction}: ${log.message}",
                                                        color = logColor,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Back Side: Disconnected state (flipped 180 degrees to show upright)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationX = 180f
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(BgWhite)
                                .padding(horizontal = 40.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (!hasPermission) {
                                Image(
                                    painter = painterResource(id = R.drawable.headphone_render),
                                    contentDescription = "Moondrop Headphones",
                                    modifier = Modifier
                                        .width(260.dp)
                                        .height(180.dp)
                                        .padding(bottom = 32.dp),
                                    contentScale = ContentScale.Fit
                                )
                                
                                Text(
                                    text = "PERMISSION REQUIRED",
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    letterSpacing = 2.sp
                                )
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Text(
                                    text = "蓝牙权限未开启",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 22.sp
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "应用需要蓝牙权限以扫描并控制您的 Moondrop 耳机。\n请点击下方按钮授予权限。",
                                    fontWeight = FontWeight.Normal,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(36.dp))
                                
                                Button(
                                    onClick = {
                                        bluetoothManager.requestBluetoothPermission()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                                    shape = RoundedCornerDefault,
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(48.dp)
                                ) {
                                    Text("授予权限", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.headphone_render),
                                    contentDescription = "Moondrop Headphones",
                                    modifier = Modifier
                                        .width(260.dp)
                                        .height(180.dp)
                                        .padding(bottom = 32.dp),
                                    contentScale = ContentScale.Fit
                                )
                                
                                Text(
                                    text = "DISCONNECTED",
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    letterSpacing = 2.sp
                                )
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Text(
                                    text = "待连接",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 22.sp
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "请确保耳机已开机并靠近手机。\n点击下方按钮或在顶部选择设备开始连接。",
                                    fontWeight = FontWeight.Normal,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(36.dp))
                                
                                Button(
                                    onClick = {
                                        selectedDevice?.let { bluetoothManager.connect(it) }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                                    shape = RoundedCornerDefault,
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(48.dp)
                                ) {
                                    Text("连接设备", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlay: Splash screen
        if (isSplashScreenActive) {
            LaunchedEffect(Unit) {
                delay(150)
                splashTextVisible = true
                delay(650)
                splashVisible = false
                delay(500)
                isSplashScreenActive = false
                // ALWAYS trigger permission check after splash finishes to check Bluetooth, Notification, and Overlay permissions
                bluetoothManager.requestBluetoothPermission()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .graphicsLayer {
                        alpha = splashAlpha
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer(
                        alpha = 1f
                    )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "Moondrop Logo",
                        modifier = Modifier.size(100.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer {
                            alpha = splashTextAlpha
                        }
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "MOONDROP",
                            letterSpacing = 4.sp,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "U L T R A S O N I C",
                            letterSpacing = 2.sp,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Overlay: AirPods-style bottom slide-up pop-up upon connection
        AnimatedVisibility(
            visible = showConnectionPopup,
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
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showConnectionPopup = false }
            ) {
                // Floating card wrapper
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding() // Keep it above system navigation gesture bar/mBack
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 40.dp) // Suspend it above the bottom
                ) {
                    // 1. The Dialog Card Body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(24.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .clickable(enabled = true, onClick = {}) // prevent click propagation
                            .padding(top = 48.dp) // extra padding for overlapping badge
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle
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

                        // Main Body: Left/Right earbud battery status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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

                        // Lower Part: Quick ANC toggle and dismiss button
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
                                onClick = { showConnectionPopup = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerDefault,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("DONE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // 2. The Overlapping Logo Badge (half out of the top edge, top-left aligned, static, size 80.dp)
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

// ─── Custom Flat Segmented Control ─────────────────────────────
@Composable
fun MinimalSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelection: (index: Int) -> Unit,
    isDark: Boolean = false
) {
    val borderColor = if (isDark) Color.White.copy(alpha = 0.2f) else BorderLight
    val unselectedTextColor = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary
    val selectedBgColor = if (isDark) Color.White else TextPrimary
    val selectedTextColor = if (isDark) Color.Black else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerDefault)
            .clip(RoundedCornerDefault)
    ) {
        items.forEachIndexed { index, text ->
            val isSelected = index == selectedIndex
            
            // Non-linear color animation on click transitions
            val animBgColor by animateColorAsState(
                targetValue = if (isSelected) selectedBgColor else Color.Transparent,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "segmentBg"
            )
            val animTextColor by animateColorAsState(
                targetValue = if (isSelected) selectedTextColor else unselectedTextColor,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "segmentText"
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(animBgColor)
                    .clickable { onItemSelection(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = animTextColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif
                )
            }
            if (index < items.size - 1) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(borderColor)
                )
            }
        }
    }
}

// ─── Custom Preset Button Component ────────────────────────────
@Composable
fun PresetButton(
    name: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val animBgColor by animateColorAsState(
        targetValue = if (isSelected) TextPrimary else Color.Transparent,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "presetBg"
    )
    val animTextColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else TextSecondary,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "presetText"
    )
    
    Box(
        modifier = modifier
            .border(1.dp, BorderLight, RoundedCornerDefault)
            .clip(RoundedCornerDefault)
            .background(animBgColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = animTextColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.SansSerif
        )
    }
}

// ─── EQ Curve Visualizer (Smooth mathematical Bezier curve vector line) ──
@Composable
fun EQVisualizer(
    bands: List<BandConfig>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(BgWhite)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Central grid hairline
            drawLine(
                color = BorderLight,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx()
            )
            
            // Map frequencies to X/Y coordinates
            val points = bands.mapIndexed { idx, band ->
                val x = idx * (size.width / (bands.size - 1))
                val y = size.height - ((band.gain + 12f) / 24f) * size.height
                Offset(x, y)
            }
            
            // Smooth mathematical Bezier curve path
            val path = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points[0].x, points[0].y)
                    for (i in 0 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val controlX = (p1.x + p2.x) / 2
                        val controlY = (p1.y + p2.y) / 2
                        quadraticBezierTo(p1.x, p1.y, controlX, controlY)
                    }
                    lineTo(points.last().x, points.last().y)
                }
            }
            
            // Draw vector line
            drawPath(
                path = path,
                color = TextPrimary,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Draw node points
            points.forEach { pt ->
                drawCircle(
                    color = TextPrimary,
                    radius = 2.5.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}
