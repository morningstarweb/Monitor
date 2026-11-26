package app.monitor.feature.dashboard.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import app.monitor.core.designsystem.theme.MonitorTypography
import app.monitor.core.designsystem.theme.NeonCyan
import app.monitor.core.designsystem.theme.NeonEmeraldRaw
import app.monitor.core.designsystem.theme.NeonPink
import app.monitor.core.designsystem.theme.WarningAmber
import app.monitor.core.model.AlertItem

@Composable
fun AlertItemCard(alert: AlertItem, onAck: () -> Unit, onSnooze: () -> Unit) {
    val (sevColor, sevLabel) = when (alert.severity.lowercase()) {
        "critical" -> NeonPink to "CRITICAL"
        "warning" -> WarningAmber to "WARNING"
        else -> NeonCyan to "INFO"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14182C).copy(alpha = 0.94f))
            .border(1.dp, sevColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sevLabel, style = MonitorTypography.labelSmall, fontWeight = FontWeight.Bold, color = sevColor, fontSize = 10.sp)
                    Text("·", color = Color(0xFF64748B))
                    Text(alert.ruleKind, style = MonitorTypography.bodySmall, color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                if (alert.ackedAtUtcMs != null) {
                    Text("ACKNOWLEDGED", style = MonitorTypography.labelSmall, color = NeonEmeraldRaw, fontSize = 9.sp)
                }
            }
            Text(alert.title, style = MonitorTypography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            Text(alert.body, style = MonitorTypography.bodySmall, color = Color(0xFFCBD5E1), fontSize = 11.sp)

            if (alert.ackedAtUtcMs == null && alert.recoveredAtUtcMs == null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertActionButton("Acknowledge", Color(0xFF818CF8), onAck)
                    AlertActionButton("Snooze 1h", Color(0xFF94A3B8), onSnooze)
                }
            }
        }
    }
}

@Composable
fun AlertActionButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, style = MonitorTypography.labelSmall, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
fun AlertFilterPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) Color(0xFF818CF8).copy(alpha = 0.20f) else Color(0xFF14182C)
    val border = if (isSelected) Color(0xFF818CF8).copy(alpha = 0.60f) else Color(0xFF262C4A)
    val text = if (isSelected) Color(0xFFC7D2FE) else Color(0xFF94A3B8)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, style = MonitorTypography.labelSmall, color = text, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.sp)
    }
}
