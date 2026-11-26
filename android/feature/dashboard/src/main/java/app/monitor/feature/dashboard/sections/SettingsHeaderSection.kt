package app.monitor.feature.dashboard.sections

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.feature.dashboard.components.LivingSettingsLogo

@Composable
fun SettingsHeaderSection(
    isOnline: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    val pulseTransition = rememberInfiniteTransition(label = "SettingsLivePulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.30f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Living Settings Logo + Clean Minimalist "Settings" + Living Status Dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LivingSettingsLogo(modifier = Modifier.size(34.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    letterSpacing = (-0.4).sp
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(if (isOnline) dotScale else 1f)
                        .background(if (isOnline) Color(0xFF10B981) else Color(0xFF64748B), CircleShape)
                )
            }
        }

        // Right: Theme Switcher Button (34dp CircleShape)
        val btnBg = if (isDark) Color(0xFF0E1220).copy(alpha = 0.90f) else Color(0xFFFFFFFF).copy(alpha = 0.92f)
        val btnBorder = if (isDark) {
            Brush.linearGradient(listOf(Color(0x50818CF8), Color(0x30C084FC)))
        } else {
            Brush.linearGradient(listOf(Color(0x30818CF8), Color(0x20CBD5E1)))
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(btnBg)
                .border(1.dp, btnBorder, CircleShape)
                .clickable(onClick = onToggleTheme),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = "Toggle Theme",
                tint = if (isDark) Color(0xFFC084FC) else Color(0xFF0F172A),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
