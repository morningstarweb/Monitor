package app.monitor.feature.gitworkspace.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.core.model.FileChangeCategory
import app.monitor.core.model.GitFileChange

/**
 * Living Obsidian/Crystal Code File Row (< 160 lines).
 * Clean typography without monospace spacing glitches, fluid width, and responsive diff counters.
 */
@Composable
fun FileItemRow(
    file: GitFileChange,
    isChecked: Boolean,
    onToggleCheck: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    val (tag, tagColor, tagBg) = when (file.category) {
        FileChangeCategory.Added, FileChangeCategory.Untracked -> Triple("A", Color(0xFF10B981), if (isDark) Color(0xFF064E3B) else Color(0xFFECFDF5))
        FileChangeCategory.Deleted -> Triple("D", Color(0xFFEF4444), if (isDark) Color(0xFF7F1D1D) else Color(0xFFFFE4E6))
        else -> Triple("M", if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED), if (isDark) Color(0xFF4C1D95) else Color(0xFFF3E8FF))
    }

    val normalizedPath = file.path.replace('\\', '/')
    val fileName = normalizedPath.substringAfterLast('/')
    val ext = if (fileName.contains('.')) fileName.substringAfterLast('.').uppercase().take(4) else "FILE"
    val breadcrumb = if (normalizedPath.contains('/')) normalizedPath.substringBeforeLast('/').replace("/", " › ") else "root"
    val rowShape = RoundedCornerShape(16.dp)

    val checkBg by animateColorAsState(
        targetValue = if (isChecked) Color(0xFF7C3AED) else (if (isDark) Color(0xFF131728) else Color(0xFFF1F5F9)),
        animationSpec = tween(180), label = "checkBg"
    )

    val rowBg = if (isDark) Color(0xFF0D1020).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.90f)
    val rowBorder = if (isDark) Brush.horizontalGradient(listOf(Color(0x35818CF8), Color(0x15FFFFFF), Color.Transparent))
                    else Brush.horizontalGradient(listOf(Color(0x30CBD5E1), Color(0x20E2E8F0), Color.Transparent))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBg)
            .border(1.dp, rowBorder, rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Interactive Checkbox
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(checkBg)
                    .border(
                        1.dp,
                        if (isChecked) (if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED))
                        else (if (isDark) Color(0x40818CF8) else Color(0x30CBD5E1)),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onToggleCheck),
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }

            // Language Tag Badge (.KT, .XML, etc.)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF1E1738) else Color(0xFFEDE9FE))
                    .border(1.dp, if (isDark) Color(0x50C084FC) else Color(0x30A855F7), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = ext, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF00E5FF) else Color(0xFF7C3AED))
            }

            // File Name & Path Breadcrumb
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = breadcrumb,
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Status Badge & Diff numbers (+32, -11)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 6.dp)
        ) {
            if (file.insertions > 0 || file.deletions > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (file.insertions > 0) Text("+${file.insertions}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFF34D399) else Color(0xFF059669))
                    if (file.deletions > 0) Text("-${file.deletions}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626))
                }
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(tagBg.copy(alpha = 0.55f))
                    .border(1.dp, tagColor.copy(alpha = 0.65f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = tag, color = tagColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
