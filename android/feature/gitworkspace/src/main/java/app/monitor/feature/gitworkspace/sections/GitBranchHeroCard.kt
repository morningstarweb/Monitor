package app.monitor.feature.gitworkspace.sections

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.feature.gitworkspace.components.GitBranchVectorIcon

/**
 * Pure Minimalist, Unboxed Git Branch Hero & Actions Bar.
 * Completely eliminates heavy card borders, enclosing boxes, and multi-color gradients.
 * Delivers an open, airy developer bar (JetBrains / GitHub Desktop inspired).
 */
@Composable
fun GitBranchHeroCard(
    currentBranch: String,
    modifier: Modifier = Modifier,
    upstreamBranch: String? = null,
    aheadCount: Int = 0,
    behindCount: Int = 0,
    totalCommits: Int = 0,
    onNewBranchClick: () -> Unit = {},
    onPullClick: () -> Unit = {},
    onPushClick: () -> Unit = {},
    onFetchClick: () -> Unit = {},
    onCompareClick: () -> Unit = {}
) {
    val isDark = LocalMonitorColors.current.isDark
    val pulse = rememberInfiniteTransition(label = "HeroBranchPulse")
    val dotScale by pulse.animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Open Branch Header Row (No enclosing box!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GitBranchVectorIcon(tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                    Text(
                        text = currentBranch,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .scale(dotScale)
                            .background(if (isDark) Color(0xFF00E699) else Color(0xFF059669), CircleShape)
                    )

                    if (aheadCount > 0) {
                        Text(
                            text = "+$aheadCount ahead",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFF00E699) else Color(0xFF059669)
                        )
                    }
                    if (behindCount > 0) {
                        Text(
                            text = "-$behindCount behind",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFFFB020) else Color(0xFFD97706)
                        )
                    }
                }

                Text(
                    text = if (upstreamBranch != null) "tracking $upstreamBranch" else "tracking origin/$currentBranch",
                    fontSize = 11.5.sp,
                    color = if (isDark) Color(0xFF64748B) else Color(0xFF475569),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Minimalist New Branch Button (Locked to 1 line, no awkward wrapping)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0x18818CF8) else Color(0x1F7C3AED))
                    .border(1.dp, if (isDark) Color(0x35818CF8) else Color(0x207C3AED), RoundedCornerShape(8.dp))
                    .clickable(onClick = onNewBranchClick)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED), modifier = Modifier.size(13.dp))
                Text(
                    text = "New Branch",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // 2. Open Minimalist Action Pills (No thick borders!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinimalistActionPill("Pull", Icons.Default.ArrowDownward, if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7), onPullClick, Modifier.weight(1f), isDark = isDark)
            MinimalistActionPill("Push", Icons.Default.ArrowUpward, if (isDark) Color(0xFF00E699) else Color(0xFF059669), onPushClick, Modifier.weight(1f), isDark = isDark)
            MinimalistActionPill("Fetch", Icons.Default.Sync, if (isDark) Color(0xFFFFB020) else Color(0xFFD97706), onFetchClick, Modifier.weight(1f), isDark = isDark)
            MinimalistActionPill("Compare", null, if (isDark) Color(0xFFD946EF) else Color(0xFF9333EA), onCompareClick, Modifier.weight(1f), isCompare = true, isDark = isDark)
        }

        // Subtle Hairline Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(if (isDark) Color(0x12FFFFFF) else Color(0x150F172A))
        )
    }
}

@Composable
private fun MinimalistActionPill(
    label: String,
    icon: ImageVector?,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompare: Boolean = false,
    isDark: Boolean = true
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) Color(0x12FFFFFF) else Color(0xFFFFFFFF).copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(13.dp))
            } else if (isCompare) {
                GitBranchVectorIcon(tint = accentColor, modifier = Modifier.size(12.dp))
            }
            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155))
        }
    }
}
