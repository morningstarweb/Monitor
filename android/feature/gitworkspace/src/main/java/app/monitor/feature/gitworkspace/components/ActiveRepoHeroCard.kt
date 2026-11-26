package app.monitor.feature.gitworkspace.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.core.model.GitWorkspaceSnapshot

/**
 * Minimalist Clean Active Repository Hero Card (< 190 lines).
 * Follows the unified Obsidian/Crystal glass design system.
 */
@Composable
fun ActiveRepoHeroCard(
    repoDisplayName: String,
    repoPath: String,
    snapshot: GitWorkspaceSnapshot?,
    onSwitchRepoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    val cardShape = RoundedCornerShape(24.dp)
    val transition = rememberInfiniteTransition(label = "RepoPulse")
    val dotScale by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dotScale"
    )
    val currentBranch = snapshot?.currentBranch?.ifBlank { "main" } ?: "main"
    val aheadCount = snapshot?.aheadCount ?: 0
    val modifiedCount = snapshot?.files?.count { !it.staged } ?: 0

    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)
    val cardBorder = if (isDark) Brush.verticalGradient(listOf(Color.White.copy(0.22f), Color(0xFF818CF8).copy(0.18f), Color.Transparent))
                     else Brush.verticalGradient(listOf(Color(0x40CBD5E1), Color(0x2094A3B8), Color.Transparent))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(1.2.dp, cardBorder, cardShape)
            .clickable(onClick = onSwitchRepoClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).padding(end = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = if (isDark) Brush.verticalGradient(listOf(Color(0xFF241C48), Color(0xFF101328)))
                                    else Brush.verticalGradient(listOf(Color(0xFFEDE9FE), Color(0xFFE0E7FF)))
                        )
                        .border(
                            1.2.dp,
                            if (isDark) Brush.linearGradient(listOf(Color(0x90C084FC), Color(0x4000E5FF)))
                            else Brush.linearGradient(listOf(Color(0x60A855F7), Color(0x400284C7))),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    LivingGitBranchNode(tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), size = 26.dp)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Full Repository Title (No horizontal squeeze)
                    Text(
                        text = repoDisplayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Secondary Path Line
                    Text(
                        text = repoPath,
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Active Branch & Status Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .scale(dotScale)
                                .background(if (isDark) Color(0xFF00FF87) else Color(0xFF059669), CircleShape)
                        )

                        Text(
                            text = "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF00FF87) else Color(0xFF059669),
                            maxLines = 1
                        )

                        Text("•", fontSize = 10.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))

                        Text(
                            text = currentBranch,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text("•", fontSize = 10.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))

                        val formattedMod = if (modifiedCount >= 10_000) "${modifiedCount / 1000}k" else "$modifiedCount"
                        val shortStatus = when {
                            aheadCount > 0 && modifiedCount > 0 -> "+$aheadCount • $formattedMod changed"
                            aheadCount > 0 -> "+$aheadCount ahead"
                            modifiedCount > 0 -> "$formattedMod changed"
                            else -> "Clean"
                        }
                        Text(
                            text = shortStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (modifiedCount > 0) (if (isDark) Color(0xFFFFB300) else Color(0xFFD97706))
                                    else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF15192E) else Color(0xFFF1F5F9))
                    .border(1.dp, if (isDark) Color(0x35818CF8) else Color(0x25CBD5E1), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Switch",
                    tint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
