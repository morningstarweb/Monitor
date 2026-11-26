package app.monitor.feature.gitworkspace.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.gitworkspace.components.GitBranchVectorIcon

@Composable
fun SyncActionCard(
    label: String,
    sublabel: String,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isCompare: Boolean = false
) {
    val cardBackground = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.90f)
    val borderBrush = if (isDark) {
        if (isCompare) Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFFC084FC)))
        else Brush.linearGradient(listOf(Color(0x60818CF8), Color(0x30C084FC), Color(0x5000E5FF)))
    } else {
        if (isCompare) Brush.linearGradient(listOf(Color(0x50818CF8), Color(0x50C084FC)))
        else Brush.linearGradient(listOf(Color(0x25CBD5E1), Color(0x35E2E8F0)))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cardBackground)
            .border(1.2.dp, borderBrush, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7),
                    modifier = Modifier.size(17.dp)
                )
            } else if (isCompare) {
                GitBranchVectorIcon(
                    tint = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCompare) {
                    if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)
                } else {
                    if (isDark) Color.White else Color(0xFF0F172A)
                }
            )
            Text(
                text = sublabel,
                fontSize = 9.sp,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
        }
    }
}
