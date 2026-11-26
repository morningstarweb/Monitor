package app.monitor.feature.dashboard.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.MonitorTypography
import app.monitor.core.designsystem.theme.NeonCyan
import app.monitor.core.designsystem.theme.NeonPink
import app.monitor.core.model.CheckpointItem

@Composable
fun CheckpointRowItem(item: CheckpointItem, onRequestRollback: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF14182C).copy(alpha = 0.94f))
            .border(1.dp, if (item.isSafety) NeonPink.copy(alpha = 0.40f) else Color(0x30818CF8), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f).padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.commitSha.take(7), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 11.sp, maxLines = 1, softWrap = false)
                    Text("·", color = Color(0xFF64748B))
                    Text(item.branch, style = MonitorTypography.bodySmall, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (item.isSafety) Text("SAFETY", style = MonitorTypography.labelSmall, color = NeonPink, fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 1, softWrap = false)
            }
            Text(item.description, style = MonitorTypography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${item.filesChangedCount} files", style = MonitorTypography.bodySmall, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, softWrap = false)
                    Text("+${item.linesAdded}", style = MonitorTypography.bodySmall, color = Color(0xFF10B981), fontSize = 11.sp, maxLines = 1, softWrap = false)
                    Text("-${item.linesDeleted}", style = MonitorTypography.bodySmall, color = NeonPink, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x20818CF8))
                        .border(1.dp, Color(0x50818CF8), RoundedCornerShape(8.dp))
                        .clickable(onClick = onRequestRollback)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("1-Tap Rollback", style = MonitorTypography.labelSmall, color = Color(0xFFC7D2FE), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
