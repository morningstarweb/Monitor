package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CleanWorkingTreeCard(isDark: Boolean, modifier: Modifier = Modifier) {
    val cleanBg = if (isDark) Color(0xFF0C0E1A).copy(0.92f) else Color.White.copy(0.90f)
    val cleanBorder = if (isDark) Color(0x35818CF8) else Color(0x20CBD5E1)
    val cleanText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cleanBg)
            .border(1.dp, cleanBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Working tree clean. No uncommitted changes.",
            color = cleanText,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FoldedFilesCard(filesCount: Int, isDark: Boolean, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val foldBg = if (isDark) Color(0xFF0C0E1A).copy(0.92f) else Color.White.copy(0.90f)
    val foldBorder = if (isDark) Color(0x35818CF8) else Color(0x20CBD5E1)
    val foldText = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(foldBg)
            .border(1.dp, foldBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onExpand)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$filesCount changed files folded • Tap to expand list",
            color = foldText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
