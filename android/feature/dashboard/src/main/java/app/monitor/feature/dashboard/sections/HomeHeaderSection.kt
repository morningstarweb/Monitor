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
import androidx.compose.material.icons.filled.Notifications
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
import app.monitor.feature.dashboard.DashboardUiState
import app.monitor.feature.dashboard.components.LivingMonitorLogo

/**
 * Minimalist Clean Header for HomeScreen (< 110 lines).
 * Zero unwanted text: sleek Monitor brand, living device icon, minimal live dot, and compact action buttons.
 */
@Composable
fun HomeHeaderSection(
    uiState: DashboardUiState,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAlertsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOnline = uiState.isOnline
    val pulseTransition = rememberInfiniteTransition(label = "HeaderLivePulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.30f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Living Monitor Logo + Clean Minimalist "Monitor" + Living Status Dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LivingMonitorLogo(modifier = Modifier.size(34.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Monitor",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                    letterSpacing = (-0.4).sp
                )

                // Minimal Living Status Dot (zero unwanted text!)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(if (isOnline) dotScale else 1f)
                        .background(if (isOnline) Color(0xFF10B981) else Color(0xFF64748B), CircleShape)
                )
            }
        }

        // Right: Clean Compact Action Controls (32dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val btnBg = if (isDarkTheme) Color(0xFF0E1220).copy(alpha = 0.90f) else Color(0xFFFFFFFF).copy(alpha = 0.92f)
            val btnBorder = if (isDarkTheme) listOf(Color(0x50818CF8), Color(0x30C084FC)) else listOf(Color(0x30000000), Color(0x20818CF8))
            val iconTint = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF1E293B)

            // Notifications Bell Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(btnBg)
                    .border(1.dp, Brush.linearGradient(btnBorder), CircleShape)
                    .clickable(onClick = onAlertsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Notifications, "Alerts", tint = iconTint, modifier = Modifier.size(16.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(6.dp)
                        .scale(dotScale)
                        .background(Color(0xFF8B5CF6), CircleShape)
                        .border(0.8.dp, if (isDarkTheme) Color(0xFF07080E) else Color.White, CircleShape)
                )
            }

            // Theme Toggle Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(btnBg)
                    .border(1.dp, Brush.linearGradient(btnBorder), CircleShape)
                    .clickable(onClick = onToggleTheme),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
