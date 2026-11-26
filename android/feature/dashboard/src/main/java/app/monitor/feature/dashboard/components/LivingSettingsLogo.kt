package app.monitor.feature.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.theme.LocalMonitorColors

@Composable
fun LivingSettingsLogo(
    modifier: Modifier = Modifier,
    isPulsing: Boolean = true
) {
    val isDark = LocalMonitorColors.current.isDark
    val shape = RoundedCornerShape(10.dp)

    val transition = rememberInfiniteTransition(label = "SettingsGearRot")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gearRotation"
    )

    val bgBrush = if (isDark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF131B2E),
                Color(0xFF0D1424)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFF1F5F9),
                Color(0xFFE2E8F0)
            )
        )
    }

    val borderBrush = if (isDark) {
        Brush.linearGradient(
            listOf(
                Color(0x80818CF8),
                Color(0x30C084FC)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0x50818CF8),
                Color(0x30CBD5E1)
            )
        )
    }

    val iconTint = if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = iconTint,
            modifier = Modifier
                .size(19.dp)
                .rotate(if (isPulsing) rotation else 0f)
        )
    }
}
