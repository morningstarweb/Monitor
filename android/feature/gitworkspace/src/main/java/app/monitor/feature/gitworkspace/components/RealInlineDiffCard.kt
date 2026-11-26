package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import app.monitor.core.model.DiffLineType
import app.monitor.core.model.FileDiffResponse

@Composable
fun RealInlineDiffCard(
    diff: FileDiffResponse,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF13172E).copy(alpha = 0.92f))
            .border(1.dp, Color(0x30818CF8), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = diff.path.substringAfterLast('/'),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "+${diff.insertions}", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = "-${diff.deletions}", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D1022))
                    .padding(vertical = 6.dp)
            ) {
                val previewLines = diff.hunks.flatMap { it.lines }.take(25)
                if (previewLines.isEmpty()) {
                    Text(
                        text = if (diff.isBinary) "Binary file difference" else "No text differences",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                } else {
                    previewLines.forEach { line ->
                        val (sign, type) = when (line.lineType) {
                            DiffLineType.Addition -> Pair("+", 1)
                            DiffLineType.Deletion -> Pair("-", -1)
                            DiffLineType.Context -> Pair(" ", 0)
                        }
                        val num = line.newLineNum ?: line.oldLineNum ?: 0L
                        DiffCodeLine(num = num.toInt(), sign = sign, text = line.content, lineType = type)
                    }
                }
            }
        }
    }
}

@Composable
fun DiffCodeLine(
    num: Int,
    sign: String,
    text: String,
    lineType: Int,
    modifier: Modifier = Modifier
) {
    val bg = when (lineType) {
        1 -> Color(0x3300FF87)
        -1 -> Color(0x33FF453A)
        else -> Color.Transparent
    }
    val textColor = when (lineType) {
        1 -> Color(0xFF4ADE80)
        -1 -> Color(0xFFFF6B6B)
        else -> Color(0xFFA1A1AA)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (num > 0) "$num" else " ",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF94A3B8),
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = sign,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )
    }
}
