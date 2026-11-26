package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import app.monitor.core.model.GitFileChange

/**
 * Dedicated Diff Hunks Code Viewer Component (< 140 lines).
 */
@Composable
fun DiffHunksViewer(
    file: GitFileChange,
    diff: FileDiffResponse?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070914))
            .border(1.dp, Color(0x3038BDF8), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                Text("Diff Inspector", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
            }
            Text("Unified View", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = Color(0xFF94A3B8))
        }

        val hunks = diff?.hunks
        if (hunks != null && hunks.isNotEmpty()) {
            if (diff.isTruncated) {
                Text(
                    text = "Diff truncated on desktop for performance — showing partial hunks.",
                    fontSize = 11.sp,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            hunks.forEach { hunk ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2500E5FF))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = hunk.header.ifBlank { "@@ -1,${file.deletions} +1,${file.insertions} @@ in ${file.path}" },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    hunk.lines.forEach { line ->
                        val (prefix, textColor, lineBg) = when (line.lineType) {
                            DiffLineType.Addition -> Triple("+", Color(0xFF00E699), Color(0x1800E699))
                            DiffLineType.Deletion -> Triple("-", Color(0xFFF43F5E), Color(0x18F43F5E))
                            DiffLineType.Context -> Triple(" ", Color(0xFF94A3B8), Color.Transparent)
                        }
                        val lineNum = line.newLineNum ?: line.oldLineNum
                        val lineNumStr = lineNum?.let { "%4d ".format(it) } ?: "     "
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(lineBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = lineNumStr, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B), softWrap = false)
                            Text(text = "$prefix ", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = textColor, fontWeight = FontWeight.Bold, softWrap = false)
                            Text(text = line.content, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = textColor, softWrap = false)
                        }
                    }
                }
            }
                }
            }
        } else if (diff == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00E5FF), strokeWidth = 2.dp)
                    Text("Loading diff telemetry…", fontSize = 12.5.sp, color = Color(0xFF94A3B8))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        diff.isBinary -> "Binary file difference — preview not available"
                        file.insertions > 0 || file.deletions > 0 -> "File change recorded (+${file.insertions} / -${file.deletions} lines)"
                        else -> "No textual differences for this file"
                    },
                    fontSize = 12.5.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
