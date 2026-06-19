package com.moondrop.controller.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Mecha Color Palette ───────────────────────────────────────
object MechaColors {
    // Surface layers (depth system)
    val surface = Color(0xFF131315)
    val surfaceDim = Color(0xFF131315)
    val surfaceContainerLowest = Color(0xFF0E0E10)
    val surfaceContainerLow = Color(0xFF1B1B1D)
    val surfaceContainer = Color(0xFF201F21)
    val surfaceContainerHigh = Color(0xFF2A2A2C)
    val surfaceContainerHighest = Color(0xFF353437)
    val surfaceBright = Color(0xFF39393B)

    // Primary - Ruby / Crimson
    val primary = Color(0xFFFFB3B1)
    val primaryContainer = Color(0xFFFF535B)
    val onPrimary = Color(0xFF680011)
    val onPrimaryContainer = Color(0xFF5B000E)
    val inversePrimary = Color(0xFFBB152C)

    // Text
    val onSurface = Color(0xFFE5E1E4)
    val onSurfaceVariant = Color(0xFFE4BEBC)
    val secondary = Color(0xFFC1C7CD)
    val secondaryDim = Color(0xFF6B7280)

    // Outline & borders
    val outline = Color(0xFFAB8987)
    val outlineVariant = Color(0xFF5B403F)

    // Status
    val error = Color(0xFFFFB4AB)
    val errorContainer = Color(0xFF93000A)

    // Glass
    val glassPanel = Color(0x66201F21)       // ~40% opacity
    val glassBorder = Color(0x1AFFB3B1)      // ~10% opacity
    val glassHighlight = Color(0x0DFFFFFF)   // ~5% white

    // Glow
    val redGlow = Color(0x26FF535B)           // ~15% neon red
    val redGlowStrong = Color(0x4DFF535B)     // ~30%
    val activeGlowBorder = Color(0x99FF535B)  // ~60%
}

// ─── Typography ────────────────────────────────────────────────
// We'll use system-safe font stacks since custom Google Fonts
// need to be bundled. The design calls for:
// Space Grotesk → SansSerif Bold (geometric)
// JetBrains Mono → Monospace
// Geist → SansSerif (clean body)

object MechaTypography {
    val headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 38.sp
    )
    val headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 31.sp
    )
    val technicalLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        lineHeight = 10.sp
    )
    val technicalLabelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        lineHeight = 12.sp
    )
    val buttonText = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.7.sp,
        lineHeight = 14.sp
    )
    val bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 27.sp
    )
    val bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    val dataReadout = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.sp
    )
}
