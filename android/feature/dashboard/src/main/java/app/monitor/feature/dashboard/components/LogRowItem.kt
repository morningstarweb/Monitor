package app.monitor.feature.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import app.monitor.feature.dashboard.model.LogLevel
import app.monitor.feature.dashboard.model.RealAuditLog

private val NeonEmerald = Color(0xFF00E699)
private val NeonCyan = Color(0xFF00E5FF)
private val WarningAmber = Color(0xFFFFB020)
private val ErrorRed = Color(0xFFEF4444)

/**
 * Pure Minimalist, Unboxed System Log Row.
 * Zero borders, high-legibility monospace telemetry typography.
 */
@Composable
fun LogRowItem(
    log: RealAuditLog,
    modifier: Modifier = Modifier,
    isLatest: Boolean = false,
    onClick: () -> Unit = {}
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val (dotColor, tagLabel) = when (log.level) {
        LogLevel.INFO -> Pair(if (isDark) NeonEmerald else Color(0xFF059669), "INFO")
        LogLevel.METRIC -> Pair(if (isDark) NeonCyan else Color(0xFF0284C7), "METRIC")
        LogLevel.WARNING -> Pair(if (isDark) WarningAmber else Color(0xFFD97706), "WARN")
        LogLevel.ERROR -> Pair(if (isDark) ErrorRed else Color(0xFFDC2626), "ERR")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Level Indicator Dot
        Box(
            modifier = Modifier
                .size(6.5.dp)
                .background(dotColor, CircleShape)
        )

        // Monospace Timestamp
        Text(
            text = log.timestamp,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (isDark) Color(0xFF64748B) else Color(0xFF475569),
            softWrap = false,
            maxLines = 1
        )

        // Level Tag Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(dotColor.copy(alpha = 0.15f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = tagLabel,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = dotColor,
                softWrap = false,
                maxLines = 1
            )
        }

        // Log Message Text
        Text(
            text = log.message,
            fontSize = 12.5.sp,
            color = if (isDark) {
                if (isLatest) Color.White else Color(0xFFE2E8F0)
            } else {
                if (isLatest) Color(0xFF0F172A) else Color(0xFF334155)
            },
            fontWeight = if (isLatest) FontWeight.Medium else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
