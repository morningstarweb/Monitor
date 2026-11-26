package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.CommitNode
import app.monitor.core.model.GitFileChange
import app.monitor.feature.gitworkspace.model.formatFullDate

/**
 * Commit Author, Parent OID, and Changes Telemetry Card (< 120 lines).
 */
@Composable
fun CommitTelemetryCard(
    commit: CommitNode,
    effectiveFiles: List<GitFileChange>,
    laneColor: Color,
    modifier: Modifier = Modifier
) {
    val totalInsertions = effectiveFiles.sumOf { it.insertions }
    val totalDeletions = effectiveFiles.sumOf { it.deletions }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF13182E), Color(0xFF0C101F))))
            .border(1.dp, Brush.horizontalGradient(listOf(Color(0x5038BDF8), Color(0x30818CF8))), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Author & Parent (Clean horizontal layout with no vertical squishing)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(laneColor, Color(0xFF8B5CF6)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(commit.authorName.take(1).uppercase().ifBlank { "M" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = commit.authorName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(text = formatFullDate(commit.timestampUtcMs), fontSize = 10.5.sp, color = Color(0xFF94A3B8), maxLines = 1)
                    }
                }

                if (commit.parentOids.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x20818CF8))
                            .border(1.dp, Color(0x35818CF8), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "⎇ ${commit.parentOids.first().take(7)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0x18818CF8), thickness = 0.8.dp)

            // Integrated Compact Stats Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1800E699))
                        .border(1.dp, Color(0x4000E699), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+$totalInsertions additions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E699))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x18FF2E93))
                        .border(1.dp, Color(0x40FF2E93), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("-$totalDeletions deletions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF2E93))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1838BDF8))
                        .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${effectiveFiles.size} files", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                }
            }
        }
    }
}
