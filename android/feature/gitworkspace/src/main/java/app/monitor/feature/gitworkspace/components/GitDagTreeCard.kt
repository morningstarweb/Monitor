package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.core.model.CommitNode
import app.monitor.feature.gitworkspace.model.computeVsCodeGitGraphLayout
import kotlin.math.max

/**
 * Pure Minimalist, Unboxed Git Commit Stream & DAG Tree.
 * Follows the unified Obsidian/Crystal glass design system.
 */
@Composable
fun GitDagTreeCard(
    displayedCommits: List<CommitNode>,
    maxLanes: Int,
    onCommitClick: (CommitNode) -> Unit,
    modifier: Modifier = Modifier,
    currentBranch: String = "main",
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMoreCommits: Boolean = false,
    searchQuery: String = "",
    errorMessage: String? = null,
    onLoadMore: () -> Unit = {}
) {
    val isDark = LocalMonitorColors.current.isDark
    var isFolded by remember { mutableStateOf(false) }

    val graphLayout = remember(displayedCommits) {
        computeVsCodeGitGraphLayout(displayedCommits)
    }

    val computedMaxLanes = remember(graphLayout, maxLanes) {
        max(graphLayout.maxLanes, maxLanes)
    }

    val dagWidth = remember(computedMaxLanes) {
        (max(2, computedMaxLanes) * 13 + 18).dp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Commit, contentDescription = null, tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                Text("COMMITS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), letterSpacing = 0.8.sp)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0x1838BDF8) else Color(0x200284C7))
                        .clickable { isFolded = !isFolded }
                        .padding(horizontal = 7.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("${displayedCommits.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                    Icon(
                        imageVector = if (isFolded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isFolded) "Expand" else "Collapse",
                        tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Text(text = currentBranch, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFF00E699) else Color(0xFF059669))
        }

        // Loading / Empty States
        if (isLoading && displayedCommits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else if (displayedCommits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No commits match \"$searchQuery\"" else (errorMessage ?: "No commit history found"),
                    fontSize = 12.5.sp,
                    color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B)
                )
            }
        } else if (isFolded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isFolded = false }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), CircleShape)
                )
                Text(
                    text = "${displayedCommits.size} commits folded • Tap to expand commit stream",
                    color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            val commitRowHeight = 54.dp
            val totalGraphHeight = (displayedCommits.size * 54).dp

            Box(modifier = Modifier.fillMaxWidth()) {
                // 1. Full-fidelity VS Code Continuous Git Graph Canvas (single vector pass, zero gaps)
                ContinuousGitGraphCanvas(
                    layout = graphLayout,
                    rowHeight = commitRowHeight,
                    modifier = Modifier
                        .width(dagWidth)
                        .height(totalGraphHeight)
                )

                // 2. Commit Stream Rows (Metadata, text, clicks)
                Column(modifier = Modifier.fillMaxWidth()) {
                    displayedCommits.forEachIndexed { index, commit ->
                        val rowLayout = graphLayout.rows.getOrNull(index)
                        MinimalistGitCommitRow(
                            commit = commit,
                            rowLayout = rowLayout,
                            dagWidth = dagWidth,
                            isHead = index == 0,
                            currentBranch = currentBranch,
                            isDark = isDark,
                            onClick = { onCommitClick(commit) },
                            drawGraph = false,
                            modifier = Modifier.height(commitRowHeight)
                        )
                    }
                }
            }

                if (hasMoreCommits || isLoadingMore) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Loading older commits from repository...",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0x1400E5FF) else Color(0xFFE0F2FE))
                                    .clickable(onClick = onLoadMore)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Load Older Commits (${displayedCommits.size} loaded)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
