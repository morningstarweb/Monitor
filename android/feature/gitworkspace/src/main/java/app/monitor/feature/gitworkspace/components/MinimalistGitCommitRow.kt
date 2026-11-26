package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.CommitNode
import app.monitor.feature.gitworkspace.model.GitRowLayout
import app.monitor.feature.gitworkspace.model.formatRelativeTime
import app.monitor.feature.gitworkspace.model.getLaneColor
import kotlin.math.max

@Composable
fun MinimalistGitCommitRow(
    commit: CommitNode,
    dagWidth: Dp,
    isHead: Boolean,
    currentBranch: String,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rowLayout: GitRowLayout? = null,
    drawGraph: Boolean = true
) {
    val laneColor = getLaneColor(rowLayout?.commitColorIndex ?: commit.colorIndex)

    val summaryLower = commit.summary.lowercase()
    val (tagLabel, tagColor) = when {
        summaryLower.startsWith("feat") -> Pair("feat", if (isDark) Color(0xFF00E699) else Color(0xFF059669))
        summaryLower.startsWith("fix") -> Pair("fix", if (isDark) Color(0xFFF43F5E) else Color(0xFFE11D48))
        summaryLower.startsWith("refactor") -> Pair("refactor", if (isDark) Color(0xFF818CF8) else Color(0xFF6366F1))
        summaryLower.startsWith("merge") -> Pair("merge", if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7))
        summaryLower.startsWith("perf") -> Pair("perf", if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706))
        summaryLower.startsWith("chore") -> Pair("chore", if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
        else -> Pair("", Color.Transparent)
    }

    // Dynamic per-row graph indentation: snug fit right next to the active graph lines of this row.
    // If only 1 lane is active, text starts immediately after lane 0 (~20dp).
    // If 2 lanes are active (like in trunk), text starts right after lane 1 (~33dp).
    // When branches expand, text smoothly moves right and truncates with ellipsis.
    val rowMaxLane = rowLayout?.maxLane ?: run {
        var m = commit.lane
        for (edge in commit.edges) {
            m = max(m, max(edge.fromLane, edge.toLane))
        }
        m
    }
    val rowIndent = (20 + (rowMaxLane.coerceAtLeast(0) * 13)).dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (drawGraph) {
            TopologicalGitGraphCanvas(
                commit = commit,
                rowLayout = rowLayout,
                isHead = isHead,
                modifier = Modifier
                    .width(rowIndent)
                    .height(54.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(rowIndent))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp, horizontal = 0.dp)
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = commit.summary,
                    fontSize = 13.sp,
                    fontWeight = if (isHead) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isDark) {
                        if (isHead) Color.White else Color(0xFFE2E8F0)
                    } else {
                        if (isHead) Color(0xFF0F172A) else Color(0xFF1E293B)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Text(
                    text = formatRelativeTime(commit.timestampUtcMs),
                    fontSize = 10.5.sp,
                    color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = commit.shortOid,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isHead) (if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)) else laneColor
                )

                if (isHead) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isDark) Color(0x2200E699) else Color(0x20059669))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "HEAD: $currentBranch",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF00E699) else Color(0xFF059669)
                        )
                    }
                }

                Text(text = "•", fontSize = 9.sp, color = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8))

                Text(
                    text = commit.authorName,
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (tagLabel.isNotBlank()) {
                    Text(text = "•", fontSize = 9.sp, color = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8))
                    Text(
                        text = tagLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tagColor
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Details",
            tint = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8),
            modifier = Modifier
                .padding(end = 4.dp)
                .size(14.dp)
        )
    }
}
