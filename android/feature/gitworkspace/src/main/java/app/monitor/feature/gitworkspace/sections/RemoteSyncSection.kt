package app.monitor.feature.gitworkspace.sections

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors

/**
 * Clean & Highly Animated Remote Synchronization Studio (< 120 lines).
 * Follows the unified Obsidian/Crystal glass design system.
 */
@Composable
fun RemoteSyncSection(
    currentBranch: String,
    upstreamBranch: String?,
    aheadCount: Int,
    behindCount: Int,
    modifier: Modifier = Modifier,
    isOperating: Boolean = false,
    onPullClick: () -> Unit = {},
    onPushClick: () -> Unit = {},
    onFetchClick: () -> Unit = {},
    onCompareClick: () -> Unit = {}
) {
    val isDark = LocalMonitorColors.current.isDark

    val transition = rememberInfiniteTransition(label = "RemoteSyncRadar")
    val borderPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart),
        label = "borderPhase"
    )
    val cloudPulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cloudPulse"
    )
    val glowPulse by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse"
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            (if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)).copy(alpha = glowPulse),
                            CircleShape
                        )
                        .border(1.5.dp, if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), CircleShape)
                )
                Text(
                    text = "Remote Synchronization",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
            }
            if (isOperating) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0x258B5CF6) else Color(0xFFEDE9FE))
                        .border(1.dp, if (isDark) Color(0x608B5CF6) else Color(0x408B5CF6), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Syncing...",
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFFC084FC) else Color(0xFF6D28D9),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Upstream Tracking Hero Card
        UpstreamHeroCard(
            currentBranch = currentBranch,
            upstreamBranch = upstreamBranch,
            aheadCount = aheadCount,
            behindCount = behindCount,
            borderPhase = borderPhase,
            cloudPulse = cloudPulse,
            isDark = isDark
        )

        // Action Buttons Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SyncActionCard("Fetch", "Prune", onFetchClick, isDark, icon = Icons.Default.Sync, modifier = Modifier.weight(1f))
            SyncActionCard("Pull", "Fast-fwd", onPullClick, isDark, icon = Icons.Default.ArrowDownward, modifier = Modifier.weight(1f))
            SyncActionCard("Push", "Publish", onPushClick, isDark, icon = Icons.Default.ArrowUpward, modifier = Modifier.weight(1f))
            SyncActionCard("Compare", "Diff view", onCompareClick, isDark, isCompare = true, modifier = Modifier.weight(1f))
        }
    }
}
