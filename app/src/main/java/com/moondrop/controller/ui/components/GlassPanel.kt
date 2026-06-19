package com.moondrop.controller.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moondrop.controller.ui.theme.MechaColors

/**
 * Glass panel composable – core container for the Mecha UI.
 * Creates a translucent card with backdrop-style blur border
 * and subtle top-left highlight bezel.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    glowColor: Color = MechaColors.primaryContainer,
    isActive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (isActive) {
        MechaColors.activeGlowBorder
    } else {
        MechaColors.glassBorder
    }

    Box(
        modifier = modifier
            .then(
                if (isActive) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.12f),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            size = size,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MechaColors.surfaceContainer.copy(alpha = 0.5f),
                            MechaColors.surfaceContainerLow.copy(alpha = 0.3f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            borderColor,
                            borderColor.copy(alpha = 0.03f)
                        )
                    ),
                    shape = shape
                ),
            content = content
        )
    }
}

/**
 * Section label with technical readout aesthetic
 */
@Composable
fun TechLabel(
    left: String,
    right: String = "",
    modifier: Modifier = Modifier,
    leftColor: Color = MechaColors.secondary.copy(alpha = 0.6f),
    rightColor: Color = MechaColors.primaryContainer
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        androidx.compose.material3.Text(
            text = left.uppercase(),
            style = com.moondrop.controller.ui.theme.MechaTypography.technicalLabel,
            color = leftColor,
            letterSpacing = com.moondrop.controller.ui.theme.MechaTypography.technicalLabel.letterSpacing
        )
        if (right.isNotEmpty()) {
            androidx.compose.material3.Text(
                text = right.uppercase(),
                style = com.moondrop.controller.ui.theme.MechaTypography.technicalLabel,
                color = rightColor,
                letterSpacing = com.moondrop.controller.ui.theme.MechaTypography.technicalLabel.letterSpacing
            )
        }
    }
}

/**
 * Animated pulsing indicator dot
 */
@Composable
fun PulsingDot(
    color: Color = MechaColors.primaryContainer,
    size: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = alpha))
    )
}
