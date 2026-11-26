package app.monitor.feature.gitworkspace.sections

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.BranchInfo
import app.monitor.feature.gitworkspace.components.GitBranchVectorIcon

/**
 * Obsidian Inactive Branch Card.
 * Follows the unified Obsidian glass design system with zero text clipping.
 */
@Composable
fun InactiveBranchCard(
    branch: BranchInfo,
    isProtected: Boolean,
    isDark: Boolean,
    onSwitchBranch: (String) -> Unit,
    onMergeBranch: (String) -> Unit,
    onDeleteBranch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val branchCardShape = RoundedCornerShape(20.dp)
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.90f)
    val inactiveBorder = if (isDark) Brush.linearGradient(listOf(Color(0x60818CF8), Color(0x30C084FC), Color(0x5000E5FF)))
        else Brush.linearGradient(listOf(Color(0x30CBD5E1), Color(0x40E2E8F0)))

    val hasUnmerged = !branch.isMerged && branch.unmergedCommits > 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(branchCardShape)
            .background(cardBg)
            .border(1.2.dp, inactiveBorder, branchCardShape)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Branch Icon + Full-width Branch Name + Unmerged Commits Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF4C1D95).copy(alpha = 0.40f) else Color(0xFFF3E8FF))
                        .border(1.dp, if (isDark) Color(0x50C084FC) else Color(0x30A855F7), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    GitBranchVectorIcon(tint = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED), modifier = Modifier.size(15.dp))
                }

                Text(
                    text = branch.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    modifier = Modifier.weight(1f)
                )

                if (hasUnmerged) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0x2500E5FF) else Color(0xFFE0F2FE))
                            .border(1.dp, if (isDark) Color(0x6000E5FF) else Color(0x400284C7), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "+${branch.unmergedCommits}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                        )
                    }
                }
            }

            // Row 2: Tracking Subtitle on left, Actions on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (branch.upstream != null) "tracking ${branch.upstream}" else "local branch",
                    fontSize = 11.5.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0x228B5CF6) else Color(0xFFEDE9FE))
                            .border(1.dp, if (isDark) Color(0x608B5CF6) else Color(0x408B5CF6), RoundedCornerShape(8.dp))
                            .clickable { onSwitchBranch(branch.name) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("Switch", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFC084FC) else Color(0xFF6D28D9), softWrap = false, maxLines = 1)
                    }

                    if (hasUnmerged) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) Color(0x2000E5FF) else Color(0xFFE0F2FE))
                                .border(1.dp, if (isDark) Color(0x6000E5FF) else Color(0x400284C7), RoundedCornerShape(8.dp))
                                .clickable { onMergeBranch(branch.name) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), modifier = Modifier.size(12.dp))
                                Text("Merge", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), softWrap = false, maxLines = 1)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) Color(0x1800FF87) else Color(0xFFECFDF5))
                                .border(1.dp, if (isDark) Color(0x4000FF87) else Color(0x3010B981), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text("Merged", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF00FF87) else Color(0xFF059669), softWrap = false, maxLines = 1)
                        }
                    }

                    if (!isProtected) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) Color(0x20F43F5E) else Color(0xFFFFE4E6))
                                .border(1.dp, if (isDark) Color(0x60F43F5E) else Color(0x40F43F5E), RoundedCornerShape(8.dp))
                                .clickable { onDeleteBranch(branch.name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFF43F5E), modifier = Modifier.size(15.dp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0x20818CF8) else Color(0xFFF1F5F9))
                            .border(1.dp, if (isDark) Color(0x40818CF8) else Color(0x30CBD5E1), RoundedCornerShape(8.dp))
                            .clickable { isExpanded = !isExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = if (isDark) Color(0xFFC084FC) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Expandable Drawer for Branch Changes
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF080A14).copy(alpha = 0.85f) else Color(0xFFF8FAFC).copy(alpha = 0.95f))
                        .border(1.dp, if (isDark) Color(0x30818CF8) else Color(0x30CBD5E1), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!hasUnmerged) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF00FF87) else Color(0xFF059669))
                            )
                            Text(
                                text = "Up to date • 0 unmerged changes",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${branch.changedFiles.size} file${if (branch.changedFiles.size != 1) "s" else ""} changed",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                            )
                            Text(
                                text = "${branch.unmergedCommits} commit${if (branch.unmergedCommits != 1) "s" else ""} to merge",
                                fontSize = 10.5.sp,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            branch.changedFiles.take(15).forEach { file ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7))
                                    )
                                    Text(
                                        text = file,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
                                    )
                                }
                            }
                            if (branch.changedFiles.size > 15) {
                                Text(
                                    text = "+${branch.changedFiles.size - 15} more files",
                                    fontSize = 10.sp,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
