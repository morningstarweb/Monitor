package app.monitor.feature.gitworkspace.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.BranchInfo
import app.monitor.feature.gitworkspace.components.GitBranchVectorIcon

/**
 * Executive Living Active Branch Hero Card.
 * Follows the unified Obsidian glass design system with zero text clipping.
 */
@Composable
fun ActiveBranchCard(
    branch: BranchInfo,
    unmergedBranches: List<BranchInfo> = emptyList(),
    isDark: Boolean,
    onMergeAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val activeCardShape = RoundedCornerShape(20.dp)
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)
    val activeBorderBrush = Brush.linearGradient(
        listOf(
            Color(0xFF00FF87),
            Color(0xFF00E5FF),
            Color(0xFF818CF8),
            Color(0xFFC084FC)
        )
    )

    val pulse = rememberInfiniteTransition(label = "activePulse")
    val dotScale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(activeCardShape)
            .background(cardBg)
            .border(1.3.dp, activeBorderBrush, activeCardShape)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Badges & Status Strip + Expand Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF064E3B).copy(alpha = 0.60f) else Color(0xFFECFDF5))
                            .border(1.dp, if (isDark) Color(0x6000FF87) else Color(0x3010B981), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        GitBranchVectorIcon(tint = Color(0xFF00FF87), modifier = Modifier.size(16.dp))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0x2500FF87) else Color(0x20059669))
                            .border(1.dp, if (isDark) Color(0x7000FF87) else Color(0x40059669), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "HEAD",
                            color = if (isDark) Color(0xFF00FF87) else Color(0xFF059669),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0x2000FF87) else Color(0x20059669))
                            .border(1.dp, if (isDark) Color(0x7000FF87) else Color(0x40059669), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .scale(dotScale)
                                    .background(if (isDark) Color(0xFF00FF87) else Color(0xFF059669), CircleShape)
                            )
                            Text(
                                text = "Active Branch",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF00FF87) else Color(0xFF059669)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0x2000FF87) else Color(0xFFF1F5F9))
                            .border(1.dp, if (isDark) Color(0x4000FF87) else Color(0x30CBD5E1), RoundedCornerShape(8.dp))
                            .clickable { isExpanded = !isExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = if (isDark) Color(0xFF00FF87) else Color(0xFF059669),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Row 2: Full-width branch title & metadata
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = branch.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (branch.upstream != null) "tracking ${branch.upstream} • Up to date" else "local branch • Up to date",
                    fontSize = 11.5.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }

            // Smart Merge All Section: Shown when external branches have unmerged commits
            if (unmergedBranches.isNotEmpty()) {
                val branchSummary = unmergedBranches.joinToString(", ") { "${it.name} (+${it.unmergedCommits})" }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0xFF09131C).copy(alpha = 0.95f) else Color(0xFFF0FDF4))
                        .border(
                            1.2.dp,
                            Brush.horizontalGradient(
                                if (isDark) listOf(Color(0xFF00FF87), Color(0xFF00E5FF))
                                else listOf(Color(0xFF10B981), Color(0xFF0284C7))
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDark) Color(0x3500FF87) else Color(0x2510B981)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CallMerge,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFF00FF87) else Color(0xFF059669),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = "Smart Merge All",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF0F172A)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDark) Color(0x3000E5FF) else Color(0x200284C7))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${unmergedBranches.size} branch(es)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            if (isDark) listOf(Color(0xFF059669), Color(0xFF0284C7))
                                            else listOf(Color(0xFF10B981), Color(0xFF0EA5E9))
                                        )
                                    )
                                    .clickable { onMergeAllClick() }
                                    .padding(horizontal = 12.dp, vertical = 6.5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CallMerge,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Merge All",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Auto-merge external branches ($branchSummary) into '${branch.name}'.",
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }
            }

            // Expandable Drawer for Active Branch
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF080A14).copy(alpha = 0.85f) else Color(0xFFF8FAFC).copy(alpha = 0.95f))
                        .border(1.dp, if (isDark) Color(0x4000FF87) else Color(0x30CBD5E1), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
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
                            text = "Current checked out branch • HEAD is up to date",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}
