package app.monitor.feature.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.components.icons.GitNodeIcon
import app.monitor.core.model.RepoSummary

/**
 * 3D Obsidian Glass Project Workspace Card (< 170 lines)
 */
@Composable
fun ApprovedProjectCard(
    project: RepoSummary,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val cardShape = RoundedCornerShape(22.dp)
    val transition = rememberInfiniteTransition(label = "ProjectPulse")
    val dotPulse by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotPulse"
    )

    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.88f)
    val cardBorder = if (isActive) Brush.linearGradient(listOf(Color(0x7000E5FF), Color(0x60818CF8), Color(0x40C084FC)))
    else if (isDark) Brush.verticalGradient(listOf(Color.White.copy(0.18f), Color(0xFF818CF8).copy(0.12f), Color.Transparent))
    else Brush.verticalGradient(listOf(Color(0x20000000), Color(0x10818CF8), Color.Transparent))

    Box(
        modifier = modifier.fillMaxWidth().clip(cardShape).background(cardBg)
            .border(1.2.dp, cardBorder, cardShape).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                // 3D Git Node squircle
                val nodeBg = if (isDark) Brush.verticalGradient(listOf(Color(0xFF1E1738), Color(0xFF0E1122))) else Brush.verticalGradient(listOf(Color(0xFFEDE9FE), Color(0xFFE0E7FF)))
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(nodeBg)
                        .border(1.dp, if (isDark) Color(0x60C084FC) else Color(0x508B5CF6), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    GitNodeIcon(modifier = Modifier.size(22.dp).scale(if (isActive) dotPulse else 1f))
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = project.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isActive) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x2500E699))
                                    .border(1.dp, Color(0x7000E699), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF00E699) else Color(0xFF059669), letterSpacing = 0.5.sp, softWrap = false, maxLines = 1)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        // Branch Pill
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) Color(0xFF161A2E) else Color(0xFFF1F5F9))
                                .border(1.dp, if (isDark) Color(0x35818CF8) else Color(0x256366F1), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.ForkRight, null, tint = Color(0xFF818CF8), modifier = Modifier.size(12.dp))
                                Text(
                                    text = project.currentBranch.ifBlank { "main" }, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Clean / Changes Status (Sum of modified, untracked, and staged files)
                        val totalChanges = project.modifiedCount + project.stagedCount + project.untrackedCount
                        val isSynced = project.isClean || totalChanges == 0
                        if (isSynced) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(5.dp).background(Color(0xFF10B981), CircleShape))
                                Text("Synced", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFF10B981) else Color(0xFF059669), softWrap = false, maxLines = 1)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(5.dp).background(Color(0xFFF59E0B), CircleShape))
                                val countStr = if (totalChanges >= 10_000) "${totalChanges / 1000}k" else "$totalChanges"
                                Text(
                                    text = "$countStr changed", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF13172E).copy(alpha = 0.6f) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ProjectPreviewCard(project: RepoSummary, onClick: () -> Unit) {
    ApprovedProjectCard(project = project, isActive = false, onClick = onClick)
}
