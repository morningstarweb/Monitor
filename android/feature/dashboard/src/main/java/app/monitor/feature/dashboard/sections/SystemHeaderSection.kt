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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.components.LivingCpuProcessor

/**
 * Clean Executive Header for System Telemetry (< 100 lines).
 * Perfectly matches HomeHeaderSection and GitGraphHeaderSection:
 * Living 34dp feature badge, bold 24sp title, living status dot, and 34dp action buttons.
 */
@Composable
fun SystemHeaderSection(
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
    hostname: String = "Desktop Workstation",
    onRefresh: () -> Unit = {}
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val pulseTransition = rememberInfiniteTransition(label = "SystemHeaderLivePulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
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
        // Left: Living 34dp System Badge + Clean "System" + Living Status Dot
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))))
                    .border(1.dp, Color(0x80C084FC), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                LivingCpuProcessor(modifier = Modifier.size(20.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "System",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    softWrap = false
                )

                // Living Status Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(if (isOnline) dotScale else 1f)
                        .background(
                            if (isOnline) Color(0xFF10B981) else Color(0xFF64748B),
                            CircleShape
                        )
                )
            }
        }

        // Right: Desktop Hostname Pill + 34dp Compact Refresh Action
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Hostname Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1838BDF8))
                    .border(1.dp, Color(0x3038BDF8), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = hostname,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8),
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Compact 34dp Refresh Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF0E1220).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.92f))
                    .border(1.dp, if (isDark) Color(0x30818CF8) else Color(0x20000000), CircleShape)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
