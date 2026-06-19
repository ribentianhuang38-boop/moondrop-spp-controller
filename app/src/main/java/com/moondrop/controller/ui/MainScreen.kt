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

// ─── Design System Tokens (Light Theme) ────────────────────────
val BgLight = Color(0xFFF4F4F6)       // Light grey background
val CardBg = Color(0xFFFFFFFF)        // White card background
val BrandRed = Color(0xFFE3000F)      // Moondrop signature red
val TextPrimary = Color(0xFF1D1D1F)   // Apple-style dark text
val TextSecondary = Color(0xFF6E6E73) // Apple-style secondary text
val BorderLight = Color(0xFFE5E5EA)   // Light grey border
val SuccessGreen = Color(0xFF34C759)  // Success green
val ErrorRed = Color(0xFFFF3B30)      // Low battery / error red
val WarningOrange = Color(0xFFFF9500) // Reconnecting orange

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
    
    // UI expanding accordions mapping to the official screenshots
    var showEQPreset by remember { mutableStateOf(false) }
    var showPEQEditor by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dropdownExpanded = true }
                    ) {
                        Text(
                            text = connectedDevice?.name ?: selectedDevice?.name ?: "MOONDROP Ultrasonic",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Device",
                            tint = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(CardBg)
                        ) {
                            if (pairedDevices.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("未发现配对设备", color = TextSecondary) },
                                    onClick = {}
                                )
                            } else {
                                pairedDevices.forEach { device ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(device.name ?: "未知设备", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                Text(device.address, color = TextSecondary, fontSize = 11.sp)
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
                        val statusColor = if (isConnected) SuccessGreen else if (isReconnecting) WarningOrange else TextSecondary
                        val statusText = if (isConnected) "已连接" else if (isReconnecting) "重连中" else "未连接"
                        
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        if (isConnected) {
                            TextButton(
                                onClick = { bluetoothManager.disconnect() },
                                colors = ButtonDefaults.textButtonColors(contentColor = BrandRed)
                            ) {
                                Text("断开", fontWeight = FontWeight.Bold)
                            }
                        } else if (selectedDevice != null) {
                            TextButton(
                                onClick = { bluetoothManager.connect(selectedDevice!!) },
                                colors = ButtonDefaults.textButtonColors(contentColor = SuccessGreen)
                            ) {
                                Text("连接", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgLight)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Hero Product Side-by-Side (No border/card container, like official app) ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
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
                            .padding(bottom = 12.dp),
                        contentScale = ContentScale.Fit
                    )
                    BatteryIndicator(level = batteryLeft, label = "L")
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
                            .padding(bottom = 12.dp),
                        contentScale = ContentScale.Fit
                    )
                    BatteryIndicator(level = batteryRight, label = "R")
                }
            }

            // ─── Case Battery Small Pill Row (Optional but extremely useful to keep) ───
            if (batteryCase > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBg)
                            .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.headphone_case),
                            contentDescription = "Charging Case",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "充电仓: $batteryCase%",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ─── Playback Quick & Volume Control Card (Matches official volume split concept) ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Circular Speaker Toggle Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF1D1D1F))
                            .clickable {
                                // Toggle volume mute or set to 0/mid as a quick action
                                if (systemVolume > 0) {
                                    bluetoothManager.setSystemVolume(0)
                                } else {
                                    bluetoothManager.setSystemVolume(maxVolume / 2)
                                }
                            }
                    ) {
                        val speakerColor = Color.White
                        Canvas(modifier = Modifier.size(16.dp)) {
                            // Speaker cone shape
                            drawRect(
                                color = speakerColor,
                                topLeft = Offset(0f, 4.dp.toPx()),
                                size = Size(4.dp.toPx(), 8.dp.toPx())
                            )
                            val path = Path().apply {
                                moveTo(4.dp.toPx(), 4.dp.toPx())
                                lineTo(9.dp.toPx(), 0f)
                                lineTo(9.dp.toPx(), 16.dp.toPx())
                                lineTo(4.dp.toPx(), 12.dp.toPx())
                                close()
                            }
                            drawPath(path, speakerColor)
                            
                            // Wave line arc
                            drawArc(
                                color = speakerColor,
                                startAngle = -45f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(5.dp.toPx(), 2.dp.toPx()),
                                size = Size(8.dp.toPx(), 12.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Right Side: Volume Slider
                    Column(modifier = Modifier.weight(1f)) {
                        val volPercent = if (maxVolume > 0) (systemVolume * 100) / maxVolume else 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "系统音量",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "$volPercent%",
                                fontWeight = FontWeight.Bold,
                                color = BrandRed,
                                fontSize = 13.sp
                            )
                        }
                        Slider(
                            value = systemVolume.toFloat(),
                            onValueChange = { bluetoothManager.setSystemVolume(it.toInt()) },
                            valueRange = 0f..maxVolume.toFloat(),
                            colors = SliderDefaults.colors(
                                activeTrackColor = BrandRed,
                                thumbColor = BrandRed
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ─── Noise Control Section (降噪设置 with circular icons) ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "降噪设置",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_next_step),
                            contentDescription = "More",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OfficialCircleButton(
                            label = "通透",
                            isSelected = activeAnc == "Transparency",
                            onClick = {
                                if (isConnected) {
                                    bluetoothManager.setAncMode("Transparency")
                                }
                            }
                        ) {
                            val color = if (activeAnc == "Transparency") Color.White else Color(0xFF8E8E93)
                            Canvas(modifier = Modifier.size(24.dp)) {
                                drawCircle(
                                    color = color,
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(
                                    color = color,
                                    radius = size.minDimension / 4.5f,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        OfficialCircleButton(
                            label = "降噪",
                            isSelected = activeAnc == "ANC",
                            onClick = {
                                if (isConnected) {
                                    bluetoothManager.setAncMode("ANC")
                                }
                            }
                        ) {
                            val color = if (activeAnc == "ANC") Color.White else Color(0xFF8E8E93)
                            Canvas(modifier = Modifier.size(24.dp)) {
                                drawCircle(
                                    color = color,
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(
                                    color = color,
                                    radius = size.minDimension / 5f
                                )
                            }
                        }

                        OfficialCircleButton(
                            label = "关闭",
                            isSelected = activeAnc == "Normal" || activeAnc == "",
                            onClick = {
                                if (isConnected) {
                                    bluetoothManager.setAncMode("Normal")
                                }
                            }
                        ) {
                            val color = if (activeAnc == "Normal" || activeAnc == "") Color.White else Color(0xFF8E8E93)
                            Canvas(modifier = Modifier.size(24.dp)) {
                                drawCircle(
                                    color = color,
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(center.x, center.y - 5.dp.toPx()),
                                    end = Offset(center.x, center.y + 5.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }

            // ─── Gain Mode Section (功能设置 with circular icons) ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "功能设置",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_next_step),
                            contentDescription = "More",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OfficialCircleButton(
                            label = "低增益",
                            isSelected = activeGain == "Low",
                            onClick = {
                                if (isConnected) {
                                    bluetoothManager.setGainMode("Low")
                                }
                            }
                        ) {
                            val color = if (activeGain == "Low") Color.White else Color(0xFF8E8E93)
                            Canvas(modifier = Modifier.size(24.dp)) {
                                drawCircle(
                                    color = color,
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(center.x - 3.dp.toPx(), center.y - 5.dp.toPx()),
                                    end = Offset(center.x - 3.dp.toPx(), center.y + 5.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(center.x + 3.dp.toPx(), center.y - 5.dp.toPx()),
                                    end = Offset(center.x + 3.dp.toPx(), center.y + 5.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        OfficialCircleButton(
                            label = "中增益",
                            isSelected = activeGain == "Medium",
                            onClick = {
                                if (isConnected) {
                                    bluetoothManager.setGainMode("Medium")
                                }
                            }
                        ) {
                            val color = if (activeGain == "Medium") Color.White else Color(0xFF8E8E93)
                            Canvas(modifier = Modifier.size(24.dp)) {
                                drawCircle(
                                    color = color,
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(center.x - 5.dp.toPx(), center.y - 5.dp.toPx()),
                                    end = Offset(center.x - 5.dp.toPx(), center.y + 5.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(center.x, center.y - 5.dp.toPx()),
                                    end = Offset(center.x, center.y + 5.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(center.x + 5.dp.toPx(), center.y - 5.dp.toPx()),
                                    end = Offset(center.x + 5.dp.toPx(), center.y + 5.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        OfficialCircleButton(
                            label = "高增益",
                            isSelected = activeGain == "High" || activeGain == "",
                            onClick = {
                                if (isConnected) {
                                    bluetoothManager.setGainMode("High")
                                }
                            }
                        ) {
                            val color = if (activeGain == "High" || activeGain == "") Color.White else Color(0xFF8E8E93)
                            Canvas(modifier = Modifier.size(24.dp)) {
                                drawCircle(
                                    color = color,
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(center.x - 5.dp.toPx(), center.y - 5.dp.toPx()),
                                    end = Offset(center.x - 5.dp.toPx(), center.y + 5.dp.toPx()),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(center.x, center.y - 5.dp.toPx()),
                                    end = Offset(center.x, center.y + 5.dp.toPx()),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = color,
                                    start = Offset(center.x + 5.dp.toPx(), center.y - 5.dp.toPx()),
                                    end = Offset(center.x + 5.dp.toPx(), center.y + 5.dp.toPx()),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }

            // ─── Scenario Presets Card (场景预设 collapsible accordion) ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEQPreset = !showEQPreset },
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Black Circular List Icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF1D1D1F))
                        ) {
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val yStart = 3.dp.toPx()
                                val gap = 5.dp.toPx()
                                for (i in 0..2) {
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(0f, yStart + i * gap),
                                        end = Offset(size.width, yStart + i * gap),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = "场景预设",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Icon(
                            painter = painterResource(id = R.drawable.ic_next_step),
                            contentDescription = "Expand EQ Presets",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = showEQPreset,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Divider(color = BorderLight)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val presetText = when (activePreset) {
                                0 -> "默认"
                                1 -> "低音"
                                2 -> "清晰"
                                63 -> "自定义"
                                else -> "预设 $activePreset"
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "均衡器曲线",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "当前预设: $presetText",
                                    color = BrandRed,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // EQ Curve Visualizer
                            EQVisualizer(bands = eqBands)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // EQ Preset Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val presets = listOf(
                                    0 to "默认",
                                    1 to "低音",
                                    2 to "清晰",
                                    63 to "自定义"
                                )
                                presets.forEach { (id, name) ->
                                    val active = activePreset == id
                                    FilterChip(
                                        selected = active,
                                        onClick = { 
                                            if (isConnected) {
                                                bluetoothManager.selectEQPreset(id) 
                                            }
                                        },
                                        label = { Text(name, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandRed.copy(alpha = 0.1f),
                                            selectedLabelColor = BrandRed,
                                            selectedLeadingIconColor = BrandRed
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── STI Sound Preference Card (STI个人声音偏好 collapsible accordion) ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPEQEditor = !showPEQEditor },
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Black Circular Soundwave Icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF1D1D1F))
                        ) {
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val barWidth = 2.dp.toPx()
                                val heights = listOf(6.dp.toPx(), 12.dp.toPx(), 9.dp.toPx(), 5.dp.toPx())
                                val spacing = 3.dp.toPx()
                                val startX = (size.width - (4 * barWidth + 3 * spacing)) / 2f
                                for (i in 0..3) {
                                    val x = startX + i * (barWidth + spacing)
                                    val h = heights[i]
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(x + barWidth / 2f, (size.height - h) / 2f),
                                        end = Offset(x + barWidth / 2f, (size.height + h) / 2f),
                                        strokeWidth = barWidth,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = "STI 个人声音偏好 (自定义 10 段 PEQ)",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Icon(
                            painter = painterResource(id = R.drawable.ic_next_step),
                            contentDescription = "Expand PEQ Editor",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = showPEQEditor,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Divider(color = BorderLight)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Pre-Gain
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "前级增益: ${String.format("%.1f", preGain)} dB",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(110.dp)
                                )
                                Slider(
                                    value = preGain,
                                    onValueChange = { preGain = it },
                                    valueRange = -12f..0f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = BrandRed,
                                        thumbColor = BrandRed
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 10 bands editor list
                            eqBands.forEachIndexed { index, band ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${band.freq}Hz",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
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
                                            activeTrackColor = BrandRed,
                                            thumbColor = BrandRed
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${String.format("%.1f", band.gain)}dB",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
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
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("保存并应用到耳机", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ─── Collapsible Advanced Section ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { showAdvanced = !showAdvanced }
                ) {
                    Text(
                        text = if (showAdvanced) "隐藏高级调试选项 ▲" else "展开高级调试选项 ▼",
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = showAdvanced,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Codec Switch Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("音频解码 / 协议切换", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F2F7), contentColor = TextPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("LDAC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { 
                                        if (isConnected) bluetoothManager.sendHex("ff040001001d200401") 
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F2F7), contentColor = TextPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("开启 LC3", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { 
                                        if (isConnected) bluetoothManager.sendHex("ff040001001d200400") 
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F2F7), contentColor = TextPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("关闭 LC3", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { bluetoothManager.setAutoReconnect(!autoReconnect) }
                            ) {
                                Checkbox(
                                    checked = autoReconnect,
                                    onCheckedChange = { bluetoothManager.setAutoReconnect(it) },
                                    colors = CheckboxDefaults.colors(checkedColor = BrandRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("协议切换后自动尝试重新连接", color = TextPrimary, fontSize = 13.sp)
                            }
                        }
                    }

                    // Hex sender / MAC config
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("发射端 MAC 地址 (LDAC 使用)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = centralMac,
                                onValueChange = { centralMac = it },
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = BorderLight
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text("原始十六进制命令发送", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customHex,
                                    onValueChange = { customHex = it },
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                                    placeholder = { Text("ff04...", color = TextSecondary, fontSize = 14.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BrandRed,
                                        unfocusedBorderColor = BorderLight
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (isConnected) {
                                            bluetoothManager.sendHex(customHex)
                                            customHex = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("发送", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Packet Log Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("通信数据包日志", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(Color(0xFFF2F2F7), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(logs) { log ->
                                        val color = when (log.direction) {
                                            "TX" -> BrandRed
                                            "TX (Offline)" -> TextSecondary
                                            "RX" -> SuccessGreen
                                            "SUCCESS" -> SuccessGreen
                                            "ERROR" -> ErrorRed
                                            "WARNING" -> WarningOrange
                                            else -> TextPrimary
                                        }
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "[${log.time}] ",
                                                color = TextSecondary,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "${log.direction}: ${log.message}",
                                                color = color,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
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

// ─── Custom Circular Button to match official app UI ──────────
@Composable
fun OfficialCircleButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable BoxScope.() -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isSelected) Color(0xFF1D1D1F) else Color(0xFFF2F2F7))
        ) {
            iconContent()
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Battery Indicator Component (Matches official horizontal style) ──
@Composable
fun BatteryIndicator(level: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Horizontal Battery Container
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(13.dp)
                    .border(1.5.dp, Color(0xFF8E8E93), RoundedCornerShape(3.dp))
                    .padding(1.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((level.coerceIn(0, 100)) / 100f)
                        .background(
                            if (level > 20) Color(0xFF34C759) else Color(0xFFFF3B30),
                            RoundedCornerShape(1.dp)
                        )
                )
            }
            Spacer(modifier = Modifier.width(1.dp))
            // Battery tip
            Box(
                modifier = Modifier
                    .size(width = 2.dp, height = 4.dp)
                    .background(Color(0xFF8E8E93), RoundedCornerShape(right = 1.dp))
            )
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Text(
                text = "$level%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )
    }
}

// ─── EQ Curve Visualizer ──────────────────────────────────────
@Composable
fun EQVisualizer(
    bands: List<BandConfig>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFFF2F2F7), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            bands.forEach { band ->
                // Map gain (-12 to +12) to fraction (0.1 to 1.0)
                val normalizedHeight = ((band.gain + 12f) / 24f).coerceIn(0.1f, 1f)
                val animatedHeight by animateFloatAsState(
                    targetValue = normalizedHeight,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "eqHeight"
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(animatedHeight)
                        .padding(horizontal = 2.dp)
                        .background(BrandRed, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                )
            }
        }
    }
}
