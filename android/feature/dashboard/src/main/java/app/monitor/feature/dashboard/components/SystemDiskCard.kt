package app.monitor.feature.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.DiskSnapshot

/**
 * Pure Minimalist Living Storage Disk Telemetry Card bound to live DiskSnapshot (< 190 lines).
 */
@Composable
fun SystemDiskCard(
    disk: DiskSnapshot,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val totalGb = disk.totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val availGb = disk.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val usedGb = (totalGb - availGb).coerceAtLeast(0.0)
    val ratio = if (disk.totalBytes > 0) ((disk.totalBytes - disk.availableBytes).toFloat() / disk.totalBytes).coerceIn(0f, 1f) else 0f
    val pct = (ratio * 100f).toInt()

    val infiniteTransition = rememberInfiniteTransition(label = "DiskPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "diskPulse"
    )

    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.88f))
            .border(
                1.dp,
                if (isDark) Brush.verticalGradient(listOf(barColor.copy(alpha = 0.45f), Color(0x20818CF8), Color(0x1000E5FF)))
                else Brush.verticalGradient(listOf(Color(0x25000000), barColor.copy(alpha = 0.45f))),
                cardShape
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(barColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                            .border(1.dp, barColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = barColor, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = disk.name.ifBlank { disk.mountPoint },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Box(
                    modifier = Modifier.size(7.dp).scale(pulse).background(barColor, CircleShape)
                )
            }

            Text(
                text = "$pct%",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )

            Text(
                text = "${"%.1f".format(usedGb)} / ${"%.1f".format(totalGb)} GB (${disk.mountPoint})",
                fontSize = 11.5.sp,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // Living Drive Capacity Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isDark) Color(0x18FFFFFF) else Color(0x18000000))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.coerceAtLeast(0.02f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(barColor, barColor.copy(alpha = 0.70f))))
                )
            }
        }
    }
}

@Composable
fun SystemDiskCard(
    modifier: Modifier = Modifier,
    name: String,
    barColor: Color,
    driveLetter: String
) {
    val disk = DiskSnapshot(
        name = name,
        mountPoint = driveLetter,
        totalBytes = 256L * 1024L * 1024L * 1024L,
        availableBytes = 160L * 1024L * 1024L * 1024L
    )
    SystemDiskCard(disk = disk, barColor = barColor, modifier = modifier)
}
