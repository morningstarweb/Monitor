package app.monitor.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.AccentMint
import app.monitor.core.designsystem.theme.CardSurfaceElevated
import app.monitor.core.designsystem.theme.ErrorRed
import app.monitor.core.designsystem.theme.TextMuted
import app.monitor.core.designsystem.theme.TextPrimary
import app.monitor.core.model.DiffHunk
import app.monitor.core.model.DiffLine
import app.monitor.core.model.DiffLineType
import app.monitor.core.model.FileDiffResponse

@Composable
fun DiffViewer(
    diff: FileDiffResponse,
    modifier: Modifier = Modifier
) {
    MonitorCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = CardSurfaceElevated
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // File Summary Header
            val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
            val addColor = if (isDark) AccentMint else Color(0xFF059669)
            val delColor = if (isDark) ErrorRed else Color(0xFFDC2626)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = diff.path,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (diff.insertions > 0) {
                    Text(
                        text = "+${diff.insertions}",
                        color = addColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (diff.deletions > 0) {
                    Text(
                        text = "-${diff.deletions}",
                        color = delColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (diff.isBinary) {
                Text(
                    text = "Binary file (not shown)",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                return@MonitorCard
            }

            if (diff.isTruncated) {
                Text("Preview truncated by the desktop. Open the full file locally to review the remaining changes.",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 12.dp))
            }

            // Hunks List
            diff.hunks.forEach { hunk ->
                DiffHunkSection(hunk = hunk)
            }
        }
    }
}

@Composable
private fun DiffHunkSection(hunk: DiffHunk) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        // Hunk Header (e.g. @@ -1,3 +1,4 @@)
        Text(
            text = hunk.header,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .background(app.monitor.core.designsystem.theme.SurfaceBackground)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )

        // Diff Lines
        hunk.lines.forEach { line ->
            DiffLineRow(line = line)
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val (bgColor, textColor, prefix) = when (line.lineType) {
        DiffLineType.Addition -> Triple(
            Color(0x1A10B981),
            AccentMint,
            "+"
        )
        DiffLineType.Deletion -> Triple(
            Color(0x1AEF4444),
            ErrorRed,
            "-"
        )
        DiffLineType.Context -> Triple(
            Color.Transparent,
            TextPrimary,
            " "
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Line number
        val lineNum = line.newLineNum ?: line.oldLineNum
        val numStr = lineNum?.toString() ?: ""
        Box(
            modifier = Modifier
                .width(36.dp)
                .padding(end = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = numStr,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        // Prefix (+ / - / space)
        Text(
            text = prefix,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = textColor,
            modifier = Modifier.width(16.dp)
        )

        // Content
        Text(
            text = line.content,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = textColor
        )
    }
}

