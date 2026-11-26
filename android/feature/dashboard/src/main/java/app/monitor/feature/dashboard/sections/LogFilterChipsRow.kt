package app.monitor.feature.dashboard.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.model.LogLevel

private val NeonEmerald = Color(0xFF00E699)
private val NeonCyan = Color(0xFF00E5FF)
private val WarningAmber = Color(0xFFFFB020)
private val ErrorRed = Color(0xFFEF4444)

@Composable
fun LogFilterChipsRow(
    selectedFilter: LogLevel?,
    onSelectFilter: (LogLevel) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isAllSelected = selectedFilter == null
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isAllSelected) (if (isDark) Color(0x30818CF8) else Color(0xFFEDE9FE))
                    else (if (isDark) Color(0x10FFFFFF) else Color(0xFFF1F5F9))
                )
                .clickable { if (selectedFilter != null) onSelectFilter(selectedFilter) }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "ALL",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAllSelected) (if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED))
                        else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
            )
        }

        listOf(
            Pair(LogLevel.INFO, if (isDark) NeonEmerald else Color(0xFF059669)),
            Pair(LogLevel.METRIC, if (isDark) NeonCyan else Color(0xFF0284C7)),
            Pair(LogLevel.WARNING, if (isDark) WarningAmber else Color(0xFFD97706)),
            Pair(LogLevel.ERROR, if (isDark) ErrorRed else Color(0xFFDC2626))
        ).forEach { (level, color) ->
            val isSelected = selectedFilter == level
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) color.copy(alpha = if (isDark) 0.25f else 0.15f)
                        else (if (isDark) Color(0x10FFFFFF) else Color(0xFFF1F5F9))
                    )
                    .clickable { onSelectFilter(level) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = level.name,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) color else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                )
            }
        }
    }
}
