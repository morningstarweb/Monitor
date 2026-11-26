package app.monitor.feature.gitworkspace.sections

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
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.core.model.PullRequestStateFilter
import app.monitor.core.model.PullRequestSummary

/**
 * Clean & Highly Animated Pull Requests Feed Section.
 * Follows the unified Obsidian/Crystal glass design system.
 */
@Composable
fun PullRequestsListSection(
    pullRequests: List<PullRequestSummary>,
    onSelectPr: (PullRequestSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    var filter by remember { mutableStateOf(PullRequestStateFilter.All) }

    val transition = rememberInfiniteTransition(label = "PrAnimation")
    val glowPulse by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "prGlowPulse"
    )
    val beaconPulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "prBeaconPulse"
    )

    val filteredPrs = remember(pullRequests, filter) {
        when (filter) {
            PullRequestStateFilter.All -> pullRequests
            PullRequestStateFilter.Open -> pullRequests.filter { it.state.equals("OPEN", ignoreCase = true) }
            PullRequestStateFilter.Merged -> pullRequests.filter { it.state.equals("MERGED", ignoreCase = true) }
            PullRequestStateFilter.Closed -> pullRequests.filter { it.state.equals("CLOSED", ignoreCase = true) }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Section Header with Glowing Dot & Filter Tabs
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
                    text = "Pull Requests Feed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
            }

            // Filter Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PullRequestStateFilter.entries.forEach { f ->
                    val isSel = filter == f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isSel) {
                                    if (isDark) Color(0xFF1E1B4B) else Color(0xFFE0E7FF)
                                } else {
                                    if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.85f)
                                }
                            )
                            .border(
                                1.dp,
                                if (isSel) {
                                    if (isDark) Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFF00E5FF)))
                                    else Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF0284C7)))
                                } else {
                                    if (isDark) Brush.linearGradient(listOf(Color(0x35818CF8), Color(0x1538BDF8)))
                                    else Brush.linearGradient(listOf(Color(0x20CBD5E1), Color(0x10E2E8F0)))
                                },
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { filter = f }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = f.name,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) {
                                if (isDark) Color(0xFF00E5FF) else Color(0xFF4338CA)
                            } else {
                                if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            }
                        )
                    }
                }
            }
        }

        // PR Cards Stream
        if (filteredPrs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.88f))
                    .border(
                        1.2.dp,
                        if (isDark) Brush.linearGradient(listOf(Color(0x40818CF8), Color(0x2038BDF8)))
                        else Brush.linearGradient(listOf(Color(0x20CBD5E1), Color(0x10E2E8F0))),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No pull requests in this view",
                    fontSize = 13.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }
        } else {
            filteredPrs.forEach { pr ->
                PullRequestItemCard(
                    pr = pr,
                    glowPulse = glowPulse,
                    beaconPulse = beaconPulse,
                    isDark = isDark,
                    onClick = { onSelectPr(pr) }
                )
            }
        }
    }
}
