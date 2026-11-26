package app.monitor.feature.gitworkspace.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.BranchInfo

@Composable
fun RemoteBranchCard(branch: BranchInfo, isDark: Boolean, modifier: Modifier = Modifier) {
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.90f)
    val borderBrush = if (isDark) Brush.horizontalGradient(listOf(Color(0x3538BDF8), Color(0x15818CF8)))
        else Brush.horizontalGradient(listOf(Color(0x30CBD5E1), Color(0x2094A3B8)))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderBrush, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Cloud, contentDescription = null, tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), modifier = Modifier.size(15.dp))
            Text(branch.name, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B), fontWeight = FontWeight.Medium)
        }
    }
}
