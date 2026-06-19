package com.moondrop.controller.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moondrop.controller.bluetooth.BandConfig
import com.moondrop.controller.bluetooth.BluetoothManager

val BgDark = Color(0xFF0F172A)
val BgCard = Color(0xFF1E293B)
val AccentIndigo = Color(0xFF6366F1)
val TextLight = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)
val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)
val WarningYellow = Color(0xFFFBBF24)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(bluetoothManager: BluetoothManager) {
    val isConnected by bluetoothManager.connectionState.collectAsState()
    val isReconnecting by bluetoothManager.reconnectingState.collectAsState()
    val autoReconnect by bluetoothManager.autoReconnect.collectAsState()
    val logs by bluetoothManager.logFlow.collectAsState()
    
    val activeAnc by bluetoothManager.ancMode.collectAsState()
    val activeGain by bluetoothManager.gainMode.collectAsState()
    val activePreset by bluetoothManager.presetMode.collectAsState()
    
    val pairedDevices = remember { bluetoothManager.getPairedDevices() }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(pairedDevices.firstOrNull { it.name?.contains("MOONDROP", ignoreCase = true) == true } ?: pairedDevices.firstOrNull()) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    var customHex by remember { mutableStateOf("") }
    var centralMac by remember { mutableStateOf("90f052c47271") }
    
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

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MOONDROP SPP CONTROL",
                color = AccentIndigo,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isConnected) SuccessGreen 
                            else if (isReconnecting) WarningYellow 
                            else ErrorRed
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConnected) "Connected" 
                           else if (isReconnecting) "Reconnecting..." 
                           else "Disconnected",
                    color = if (isConnected) SuccessGreen 
                            else if (isReconnecting) WarningYellow 
                            else ErrorRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CONNECTION SETTINGS", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgDark, RoundedCornerShape(6.dp))
                        .border(1.dp, TextMuted, RoundedCornerShape(6.dp))
                        .clickable { if (!isConnected && !isReconnecting) dropdownExpanded = true }
                        .padding(12.dp)
                ) {
                    Text(
                        text = selectedDevice?.let { "${it.name ?: "Unknown"} (${it.address})" } ?: "No Paired Devices",
                        color = TextLight,
                        fontSize = 14.sp
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(BgCard)
                    ) {
                        pairedDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text("${device.name ?: "Unknown"} (${device.address})", color = TextLight) },
                                onClick = {
                                    selectedDevice = device
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { selectedDevice?.let { bluetoothManager.connect(it) } },
                        enabled = !isConnected && !isReconnecting && selectedDevice != null,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Connect", color = TextLight, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { bluetoothManager.disconnect() },
                        enabled = isConnected || isReconnecting,
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disconnect", color = TextLight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Codec & Mode Switch Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CODEC / PROTOCOL SWITCH (模式切换)", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { 
                            val cleanMac = centralMac.replace(":", "").replace("-", "")
                            if (cleanMac.length == 12) {
                                bluetoothManager.sendHex("ff040006001d2a02$cleanMac")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text("LDAC Mode", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { bluetoothManager.sendHex("ff040001001d200401") },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text("LC3 Enable", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { bluetoothManager.sendHex("ff040001001d200400") },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text("LC3 Disable", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { bluetoothManager.setAutoReconnect(!autoReconnect) }
                ) {
                    Checkbox(
                        checked = autoReconnect,
                        onCheckedChange = { bluetoothManager.setAutoReconnect(it) },
                        colors = CheckboxDefaults.colors(checkedColor = AccentIndigo, uncheckedColor = TextMuted)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto-Reconnect after mode switch (切换后自动重连)", color = TextLight, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ANC & Gain Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ANC CONTROL", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isNormal = activeAnc == "Normal"
                    Button(
                        onClick = { bluetoothManager.sendHex("ff040001001d100401") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNormal) SuccessGreen else BgDark,
                            contentColor = if (isNormal) TextLight else TextMuted
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text("Normal", fontSize = 11.sp)
                    }
                    val isAnc = activeAnc == "ANC"
                    Button(
                        onClick = { bluetoothManager.sendHex("ff040001001d100402") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAnc) SuccessGreen else BgDark,
                            contentColor = if (isAnc) TextLight else TextMuted
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text("ANC", fontSize = 11.sp)
                    }
                    val isTransp = activeAnc == "Transparency"
                    Button(
                        onClick = { bluetoothManager.sendHex("ff040001001d100404") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTransp) SuccessGreen else BgDark,
                            contentColor = if (isTransp) TextLight else TextMuted
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text("Transp.", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("GAIN CONTROL", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isLow = activeGain == "Low"
                    Button(
                        onClick = { bluetoothManager.sendHex("ff040001001d1e0202") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLow) SuccessGreen else BgDark,
                            contentColor = if (isLow) TextLight else TextMuted
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text("Low", fontSize = 11.sp)
                    }
                    val isMedium = activeGain == "Medium"
                    Button(
                        onClick = { bluetoothManager.sendHex("ff040001001d1e0201") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMedium) SuccessGreen else BgDark,
                            contentColor = if (isMedium) TextLight else TextMuted
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text("Medium", fontSize = 11.sp)
                    }
                    val isHigh = activeGain == "High"
                    Button(
                        onClick = { bluetoothManager.sendHex("ff040001001d1e0200") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHigh) SuccessGreen else BgDark,
                            contentColor = if (isHigh) TextLight else TextMuted
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    ) {
                        Text("High", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // EQ Preset selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("EQ PRESET SELECT", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val presets = listOf(
                        0 to "Default",
                        1 to "Basshead",
                        2 to "Crisp",
                        63 to "Custom"
                    )
                    presets.forEach { (id, name) ->
                        val active = activePreset == id
                        Button(
                            onClick = { bluetoothManager.selectEQPreset(id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) SuccessGreen else BgDark,
                                contentColor = if (active) TextLight else TextMuted
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                        ) {
                            Text(name, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom 10-Band PEQ Editor Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CUSTOM 10-BAND PEQ", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Pre-Gain slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pre-Gain: ${String.format("%.1f", preGain)} dB", color = TextLight, fontSize = 12.sp, modifier = Modifier.width(130.dp))
                    Slider(
                        value = preGain,
                        onValueChange = { preGain = it },
                        valueRange = -12f..0f,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Headers
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Freq (Hz)", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Q Factor", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Gain (dB)", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(2f))
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 10 bands
                eqBands.forEachIndexed { index, band ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                focusedBorderColor = AccentIndigo,
                                unfocusedBorderColor = TextMuted
                            ),
                            modifier = Modifier.weight(1f).height(38.dp).padding(end = 4.dp)
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
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                focusedBorderColor = AccentIndigo,
                                unfocusedBorderColor = TextMuted
                            ),
                            modifier = Modifier.weight(1f).height(38.dp).padding(horizontal = 4.dp)
                        )
                        
                        // Gain Slider + label
                        Row(
                            modifier = Modifier.weight(2f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = band.gain,
                                onValueChange = { eqBands[index] = band.copy(gain = it) },
                                valueRange = -12f..12f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${String.format("%.1f", band.gain)}dB",
                                color = TextLight,
                                fontSize = 11.sp,
                                modifier = Modifier.width(45.dp).padding(start = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { bluetoothManager.sendCustomEQ(preGain, eqBands) },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Custom EQ to Headset", color = TextLight, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.weight(1.1f),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Central MAC (for LDAC)", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = centralMac,
                        onValueChange = { centralMac = it },
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentIndigo,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Card(
                modifier = Modifier.weight(0.9f),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("RAW HEX", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customHex,
                        onValueChange = { customHex = it },
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentIndigo,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        placeholder = { Text("ff04...", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            bluetoothManager.sendHex(customHex)
                            customHex = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Hex", fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("PACKET LOG", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF020617))
                        .padding(8.dp)
                ) {
                    items(logs) { log ->
                        val color = when (log.direction) {
                            "TX" -> AccentIndigo
                            "RX" -> SuccessGreen
                            "SUCCESS" -> SuccessGreen
                            "ERROR" -> ErrorRed
                            "WARNING" -> WarningYellow
                            else -> TextLight
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                text = "[${log.time}] ",
                                color = TextMuted,
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
