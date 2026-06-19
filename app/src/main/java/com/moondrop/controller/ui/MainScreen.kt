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
                            text = connectedDevice?.name ?: selectedDevice?.name ?: "ULTRASONIC",
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
            // ─── Hero Product Card ────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.headphone_hero),
                        contentDescription = "Moondrop Headphone Render",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(16.dp)
                    )
                }
            }

            // ─── Battery Section ──────────────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Charging Case Battery
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.headphone_case),
                            contentDescription = "Charging Case",
                            modifier = Modifier.size(50.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "充电仓",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Moondrop Ultrasonic Case",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "$batteryCase%",
                            fontWeight = FontWeight.Bold,
                            color = if (batteryCase > 20) SuccessGreen else ErrorRed,
                            fontSize = 20.sp
                        )
                    }
                }

                // Left & Right Buds Battery Side-by-Side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Earbud Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "左耳",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "$batteryLeft%",
                                    fontWeight = FontWeight.Bold,
                                    color = if (batteryLeft > 20) SuccessGreen else ErrorRed,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Image(
                                painter = painterResource(id = R.drawable.headphone_left),
                                contentDescription = "Left Bud",
                                modifier = Modifier.height(80.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("单击: 播放/暂停", color = TextSecondary, fontSize = 11.sp)
                                Text("双击: 上一首", color = TextSecondary, fontSize = 11.sp)
                                Text("长按: 降噪切换", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    // Right Earbud Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "右耳",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "$batteryRight%",
                                    fontWeight = FontWeight.Bold,
                                    color = if (batteryRight > 20) SuccessGreen else ErrorRed,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Image(
                                painter = painterResource(id = R.drawable.headphone_right),
                                contentDescription = "Right Bud",
                                modifier = Modifier.height(80.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("单击: 播放/暂停", color = TextSecondary, fontSize = 11.sp)
                                Text("双击: 下一首", color = TextSecondary, fontSize = 11.sp)
                                Text("长按: 语音助手", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ─── Noise Control Section ────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "降噪模式",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val ancItems = listOf("降噪", "通透", "关闭")
                    val currentAncIndex = when (activeAnc) {
                        "ANC" -> 0
                        "Transparency" -> 1
                        else -> 2
                    }
                    
                    SegmentedControl(
                        items = ancItems,
                        selectedIndex = currentAncIndex,
                        onItemSelection = { index ->
                            if (isConnected) {
                                val mode = when (index) {
                                    0 -> "ANC"
                                    1 -> "Transparency"
                                    else -> "Normal"
                                }
                                bluetoothManager.setAncMode(mode)
                            }
                        }
                    )
                }
            }

            // ─── Gain Mode Section ────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "增益模式",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val gainItems = listOf("低增益", "中增益", "高增益")
                    val currentGainIndex = when (activeGain) {
                        "Low" -> 0
                        "Medium" -> 1
                        else -> 2
                    }
                    
                    SegmentedControl(
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
            }

            // ─── Equalizer Section ────────────────────────────────────
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
                            text = "均衡器",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                        val presetText = when (activePreset) {
                            0 -> "默认"
                            1 -> "低音"
                            2 -> "清晰"
                            63 -> "自定义"
                            else -> "预设 $activePreset"
                        }
                        Text(
                            text = "当前预设: $presetText",
                            color = BrandRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Expand Custom PEQ Button
                    OutlinedButton(
                        onClick = { showPEQEditor = !showPEQEditor },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text(if (showPEQEditor) "隐藏自定义 EQ 编辑器" else "展开自定义 EQ 编辑器", fontWeight = FontWeight.Bold)
                    }

                    AnimatedVisibility(
                        visible = showPEQEditor,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            HorizontalDivider(color = BorderLight)
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

            // ─── Volume Section ───────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val volPercent = if (maxVolume > 0) (systemVolume * 100) / maxVolume else 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "音量控制",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "$volPercent%",
                            fontWeight = FontWeight.Bold,
                            color = BrandRed,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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

            // ─── Collapsible Advanced Section ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { showAdvanced = !showAdvanced }
                ) {
                    Text(
                        text = if (showAdvanced) "隐藏高级选项 ▲" else "展开高级选项 ▼",
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

// ─── Custom Segmented Control ─────────────────────────────────
@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelection: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFEFEFF4), RoundedCornerShape(24.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, text ->
            val isSelected = index == selectedIndex
            val animBgColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.Transparent,
                animationSpec = tween(200),
                label = "segmentBg"
            )
            val animTextColor by animateColorAsState(
                targetValue = if (isSelected) BrandRed else TextSecondary,
                animationSpec = tween(200),
                label = "segmentText"
            )
            val shadowModifier = if (isSelected) {
                Modifier.shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp))
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .then(shadowModifier)
                    .background(animBgColor)
                    .clickable { onItemSelection(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = animTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
