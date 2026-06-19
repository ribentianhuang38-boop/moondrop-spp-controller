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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moondrop.controller.R
import com.moondrop.controller.bluetooth.BandConfig
import com.moondrop.controller.bluetooth.BluetoothManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Design System Tokens ──────────────────────────────────────
val BgWhite = Color(0xFFFFFFFF)        // Pure white background
val TextPrimary = Color(0xFF111111)    // Primary text (charcoal)
val TextSecondary = Color(0xFF777777)  // Secondary text (grey)
val BorderLight = Color(0xFFE5E5EA)    // Hairline border
val ActiveAccent = Color(0xFF00FF66)   // Fluorescent green connection dot
val RoundedCornerDefault = RoundedCornerShape(6.dp) // Premium smooth corner radius

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
    
    // Splash screen states
    var isSplashScreenActive by remember { mutableStateOf(true) }
    var splashVisible by remember { mutableStateOf(false) }
    
    val splashAlpha by animateFloatAsState(
        targetValue = if (splashVisible) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )
    val splashScale by animateFloatAsState(
        targetValue = if (splashVisible) 1f else 0.85f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    // EQ bands local state
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

    // Sync local eqBands when activePreset changes
    LaunchedEffect(activePreset) {
        if (activePreset != 63) {
            val presetBands = when (activePreset) {
                1 -> listOf(
                    BandConfig(31, 1.0f, 13, 5.0f),
                    BandConfig(62, 1.0f, 13, 4.0f),
                    BandConfig(125, 1.0f, 13, 2.0f),
                    BandConfig(250, 1.0f, 13, 0.0f),
                    BandConfig(500, 1.0f, 13, 0.0f),
                    BandConfig(1000, 1.0f, 13, 0.0f),
                    BandConfig(2000, 1.0f, 13, 0.0f),
                    BandConfig(4000, 1.0f, 13, 0.0f),
                    BandConfig(8000, 1.0f, 13, 0.0f),
                    BandConfig(16000, 1.0f, 13, 0.0f)
                )
                2 -> listOf(
                    BandConfig(31, 1.0f, 13, 0.0f),
                    BandConfig(62, 1.0f, 13, 0.0f),
                    BandConfig(125, 1.0f, 13, 0.0f),
                    BandConfig(250, 1.0f, 13, 0.0f),
                    BandConfig(500, 1.0f, 13, 0.0f),
                    BandConfig(1000, 1.0f, 13, 0.0f),
                    BandConfig(2000, 1.0f, 13, 1.5f),
                    BandConfig(4000, 1.0f, 13, 3.0f),
                    BandConfig(8000, 1.0f, 13, 4.0f),
                    BandConfig(16000, 1.0f, 13, 2.0f)
                )
                else -> defaultBands // Flat
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

    if (isSplashScreenActive) {
        LaunchedEffect(Unit) {
            splashVisible = true
            delay(1500)
            splashVisible = false
            delay(400)
            isSplashScreenActive = false
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgWhite),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(
                    alpha = splashAlpha,
                    scaleX = splashScale,
                    scaleY = splashScale
                )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "Moondrop Logo",
                    modifier = Modifier.size(80.dp),
                    colorFilter = ColorFilter.tint(TextPrimary)
                )
                Spacer(modifier = Modifier.height(16.dp))
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
    } else {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgWhite)
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 40.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // ─── Minimalist Earbud Display (100% transparent backgrounds) ─────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Earbud Column
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
                    
                    // Right Earbud Column
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

                // ─── Noise Control Section ──────────────────────────────────────────────────
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
                            if (isConnected) {
                                val mode = when (index) {
                                    0 -> "Transparency"
                                    1 -> "ANC"
                                    else -> "Normal"
                                }
                                bluetoothManager.setAncMode(mode)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // ─── Gain Mode Section ──────────────────────────────────────────────────────
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
                            if (isConnected) {
                                val mode = when (index) {
                                    0 -> "Low"
                                    1 -> "Medium"
                                    else -> "High"
                                }
                                bluetoothManager.setGainMode(mode)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // ─── Equalizer Section ──────────────────────────────────────────────────────
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
                    
                    val presetItems = listOf("默认", "低音", "清晰", "自定义")
                    val currentPresetIndex = when (activePreset) {
                        0 -> 0
                        1 -> 1
                        2 -> 2
                        else -> 3
                    }
                    
                    MinimalSegmentedControl(
                        items = presetItems,
                        selectedIndex = currentPresetIndex,
                        onItemSelection = { index ->
                            if (isConnected) {
                                val presetId = when (index) {
                                    0 -> 0
                                    1 -> 1
                                    2 -> 2
                                    else -> 63
                                }
                                bluetoothManager.selectEQPreset(presetId)
                            }
                            if (index == 3) {
                                showPEQEditor = true
                            }
                        }
                    )
                    
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
                                    if (isConnected) {
                                        bluetoothManager.sendCustomEQ(preGain, eqBands) 
                                    }
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

                // ─── Volume Section ─────────────────────────────────────────────────────────
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

                // ─── Collapsible Advanced Section ───────────────────────────────────────────
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
                        // Codec
                        Column {
                            Text("PROTOCOL / CODEC SWITCH", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        val cleanMac = centralMac.replace(":", "").replace("-", "")
                                        if (cleanMac.length == 12 && isConnected) {
                                            bluetoothManager.sendHex("ff040006001d2a02$cleanMac")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BorderLight, contentColor = TextPrimary),
                                    shape = RoundedCornerDefault,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("LDAC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { 
                                        if (isConnected) bluetoothManager.sendHex("ff040001001d200401") 
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BorderLight, contentColor = TextPrimary),
                                    shape = RoundedCornerDefault,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("LC3 ON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { 
                                        if (isConnected) bluetoothManager.sendHex("ff040001001d200400") 
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BorderLight, contentColor = TextPrimary),
                                    shape = RoundedCornerDefault,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("LC3 OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                        // Hex sender
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
                                        if (isConnected) {
                                            bluetoothManager.sendHex(customHex)
                                            customHex = ""
                                        }
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
        }
    }
}

// ─── Custom Flat Segmented Control ─────────────────────────────
@Composable
fun MinimalSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelection: (index: Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerDefault)
            .clip(RoundedCornerDefault)
    ) {
        items.forEachIndexed { index, text ->
            val isSelected = index == selectedIndex
            
            // Non-linear color animation on click transitions
            val animBgColor by animateColorAsState(
                targetValue = if (isSelected) TextPrimary else Color.Transparent,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "segmentBg"
            )
            val animTextColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else TextSecondary,
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
                        .background(BorderLight)
                )
            }
        }
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
