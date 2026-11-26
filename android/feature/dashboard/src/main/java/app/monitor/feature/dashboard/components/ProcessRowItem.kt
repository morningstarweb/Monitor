package app.monitor.feature.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.ProcessSnapshot
import java.util.Locale

/**
 * Pure Minimalist, Unboxed Instance / Process Row.
 * Features live system-synchronized CPU load bar and dynamic memory gauge.
 */
@Composable
fun ProcessRowItem(
    process: ProcessSnapshot,
    modifier: Modifier = Modifier,
    maxCpuInList: Double = 100.0,
    onClick: () -> Unit = {}
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val cpuPercent = process.cpuPercent
    val memoryMb = process.memoryBytes / (1024 * 1024)

    // Live Synced Dynamic CPU Load Bar
    val normalizedBarRatio = if (maxCpuInList > 0.0) {
        (cpuPercent / max(maxCpuInList, 10.0)).toFloat().coerceIn(0.04f, 1f)
    } else 0.04f

    val animatedBarWidth by animateFloatAsState(
        targetValue = normalizedBarRatio,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cpuBarWidth"
    )

    // Dynamic Color Coded to Actual Load
    val loadColor by animateColorAsState(
        targetValue = when {
            cpuPercent > 20.0 -> Color(0xFFF43F5E)
            cpuPercent > 5.0 -> Color(0xFFFFB020)
            else -> if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
        },
        label = "cpuColor"
    )

    val memoryText = if (memoryMb >= 1024) {
        String.format(Locale.US, "%.1f GB", memoryMb / 1024.0)
    } else {
        "$memoryMb MB"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Process Icon with Load Indicator
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0x15FFFFFF) else Color(0x10000000)),
                contentAlignment = Alignment.Center
            ) {
                WindowsLogoIcon(tint = loadColor, modifier = Modifier.size(15.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = process.name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // PID Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isDark) Color(0x18FFFFFF) else Color(0x12000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${process.pid}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }

                // Live Synchronized CPU Load Bar (Directly represents real CPU load)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDark) Color(0x12FFFFFF) else Color(0x15000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedBarWidth)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(loadColor)
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Right side: Real CPU % + Real Memory
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%.1f%%", cpuPercent),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = loadColor
            )

            Text(
                text = memoryText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isDark) Color(0xFF64748B) else Color(0xFF475569)
            )
        }
    }
}

private fun max(a: Double, b: Double): Double = if (a > b) a else b
