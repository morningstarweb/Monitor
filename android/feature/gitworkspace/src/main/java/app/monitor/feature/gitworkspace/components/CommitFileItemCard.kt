package app.monitor.feature.gitworkspace.components

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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.FileChangeCategory
import app.monitor.core.model.GitFileChange

/**
 * Clean Git Changed File Item Card (< 100 lines).
 */
@Composable
fun CommitFileItemCard(
    file: GitFileChange,
    onClick: () -> Unit
) {
    val (catLabel, catBg, catColor) = when (file.category) {
        FileChangeCategory.Added -> Triple("A", Color(0xFF064E3B).copy(alpha = 0.50f), Color(0xFF00E699))
        FileChangeCategory.Deleted -> Triple("D", Color(0xFF4C0519).copy(alpha = 0.50f), Color(0xFFF43F5E))
        FileChangeCategory.Modified -> Triple("M", Color(0xFF0C4A6E).copy(alpha = 0.50f), Color(0xFF00E5FF))
        else -> Triple("M", Color(0xFF451A03).copy(alpha = 0.50f), Color(0xFFF59E0B))
    }
    val fileName = file.path.substringAfterLast('/')
    val dirPath = file.path.substringBeforeLast('/', "")

    val cardShape = RoundedCornerShape(16.dp)
    val cardBackground = Color(0xFF0C0E1A).copy(alpha = 0.94f)
    val borderBrush = Brush.linearGradient(listOf(Color(0x40818CF8), Color(0x2038BDF8)))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBackground)
            .border(1.2.dp, borderBrush, cardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(catBg)
                        .border(1.dp, catColor.copy(alpha = 0.6f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(catLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = catColor)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = fileName, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (dirPath.isNotBlank()) {
                        Text(text = dirPath, fontSize = 10.5.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (file.insertions > 0) Text("+${file.insertions}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E699))
                    if (file.deletions > 0) Text("-${file.deletions}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1500E5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Inspector", tint = Color(0xFF00E5FF), modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
