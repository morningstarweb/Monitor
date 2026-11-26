package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.CommitNode
import app.monitor.feature.gitworkspace.model.GitRowLayout
import app.monitor.feature.gitworkspace.model.formatRelativeTime
import kotlin.math.max

private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondaryLight = Color(0xFFA1A1AA)
private val TextMutedDark = Color(0xFF71717A)

/**
 * Full Git Graph Commit Row with DAG graph lane, branch badges, and living commit status (< 175 lines).
 */
@Composable
fun FullGitGraphCommitRow(
    commit: CommitNode,
    maxLanes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHeadCommit: Boolean = false,
    currentBranch: String = "main",
    rowLayout: GitRowLayout? = null
) {
    val graphCanvasWidth = remember(maxLanes) {
        (max(2, maxLanes) * 11 + 18).dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopologicalGitGraphCanvas(
            commit = commit,
            rowLayout = rowLayout,
            isHead = isHeadCommit,
            modifier = Modifier
                .width(graphCanvasWidth)
                .height(64.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Commit summary with optional HEAD badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isHeadCommit) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF064E3B))
                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(currentBranch, color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = commit.summary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Subtitle: short OID, relative time, author
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = commit.shortOid,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = Color(0xFFC084FC),
                    fontWeight = FontWeight.Medium
                )
                Text("·", color = TextMutedDark, fontSize = 11.sp)
                Text(
                    text = formatRelativeTime(commit.timestampUtcMs),
                    fontSize = 11.5.sp,
                    color = TextSecondaryLight
                )
                Text("·", color = TextMutedDark, fontSize = 11.sp)
                Text(
                    text = commit.authorName.substringBefore(' '),
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        CommitTypeIconBox(commit = commit)
    }
}

@Composable
fun CommitTypeIconBox(
    commit: CommitNode,
    modifier: Modifier = Modifier
) {
    val summaryLower = commit.summary.lowercase()
    val isMerge = commit.parentOids.size > 1 || summaryLower.startsWith("merge")
    val isAdd = summaryLower.startsWith("add") || summaryLower.startsWith("create") || summaryLower.startsWith("new")
    val isStyle = summaryLower.startsWith("update ui") || summaryLower.startsWith("style") || summaryLower.startsWith("design")

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF14192E))
            .border(1.dp, Color(0x35818CF8), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            isMerge -> GitBranchVectorIcon(tint = Color(0xFFC084FC), modifier = Modifier.size(15.dp))
            isAdd -> Icon(Icons.Default.AddBox, contentDescription = "Add", tint = Color(0xFF34D399), modifier = Modifier.size(15.dp))
            isStyle -> Icon(Icons.Default.Brush, contentDescription = "Style", tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
            else -> Icon(Icons.Default.Code, contentDescription = "Code", tint = Color(0xFF818CF8), modifier = Modifier.size(15.dp))
        }
    }
}
