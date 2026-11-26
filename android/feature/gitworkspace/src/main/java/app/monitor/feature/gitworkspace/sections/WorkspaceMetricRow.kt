package app.monitor.feature.gitworkspace.sections

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.monitor.core.designsystem.components.CircularProgressRing
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.core.model.GitWorkspaceSnapshot
import app.monitor.feature.gitworkspace.components.LivingCommitLayers
import app.monitor.feature.gitworkspace.components.LivingGitBranchNode
import app.monitor.feature.gitworkspace.components.LivingPullIcon

private fun formatMetric(raw: Long, isSigned: Boolean = false): String {
    val abs = kotlin.math.abs(raw)
    val sign = if (raw > 0 && isSigned) "+" else if (raw < 0) "-" else ""
    return when {
        abs >= 1_000_000 -> "$sign${abs / 1_000_000}.${(abs % 1_000_000) / 100_000}M"
        abs >= 10_000 -> "$sign${abs / 1_000}.${(abs % 1_000) / 100}k"
        else -> if (isSigned && raw > 0) "+$raw" else "$raw"
    }
}

/**
 * 3-Stat Repository Health Gauge Row (< 150 lines).
 * Zero layout breaking or digit wrapping on large numbers.
 */
@Composable
fun WorkspaceMetricRow(
    snapshot: GitWorkspaceSnapshot?,
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    val transition = rememberInfiniteTransition(label = "WorkspaceGaugePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gaugePulse"
    )

    val filesCount = snapshot?.files?.size?.toLong() ?: 0L
    val aheadCount = snapshot?.aheadCount?.toLong() ?: 0L
    val netLines = snapshot?.files?.sumOf { (it.insertions - it.deletions).toLong() } ?: 0L

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CleanWorkspaceGaugeCard(
            modifier = Modifier.weight(1f),
            iconContent = { LivingGitBranchNode(tint = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED), size = 18.dp, modifier = Modifier.scale(pulse)) },
            iconBg = if (isDark) Color(0xFF4C1D95).copy(alpha = 0.50f) else Color(0xFFF3E8FF),
            progress = (filesCount / 10f).coerceIn(0.1f, 1f),
            progressColor = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED),
            value = formatMetric(filesCount, isSigned = false),
            label = "Changes",
            isDark = isDark
        )

        CleanWorkspaceGaugeCard(
            modifier = Modifier.weight(1f),
            iconContent = { LivingCommitLayers(tint = if (isDark) Color(0xFF34D399) else Color(0xFF059669), size = 18.dp, modifier = Modifier.scale(pulse)) },
            iconBg = if (isDark) Color(0xFF064E3B).copy(alpha = 0.50f) else Color(0xFFECFDF5),
            progress = ((aheadCount + 1) / 5f).coerceIn(0.2f, 1f),
            progressColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669),
            value = if (aheadCount > 0) "+$aheadCount" else "0",
            label = "Ahead",
            isDark = isDark
        )

        CleanWorkspaceGaugeCard(
            modifier = Modifier.weight(1f),
            iconContent = { LivingPullIcon(tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), size = 18.dp, modifier = Modifier.scale(pulse)) },
            iconBg = if (isDark) Color(0xFF0C4A6E).copy(alpha = 0.50f) else Color(0xFFE0F2FE),
            progress = ((netLines.coerceAtLeast(10)) / 250f).coerceIn(0.15f, 1f),
            progressColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7),
            value = formatMetric(netLines, isSigned = true),
            label = "Net Lines",
            isDark = isDark
        )
    }
}

@Composable
private fun CleanWorkspaceGaugeCard(
    modifier: Modifier = Modifier,
    iconContent: @Composable () -> Unit,
    iconBg: Color,
    progress: Float,
    progressColor: Color,
    value: String,
    label: String,
    isDark: Boolean = true
) {
    val cardShape = RoundedCornerShape(22.dp)
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)
    val cardBorder = if (isDark) Brush.verticalGradient(listOf(Color.White.copy(0.20f), Color(0xFF818CF8).copy(0.15f), Color.Transparent))
                     else Brush.verticalGradient(listOf(Color(0x40CBD5E1), Color(0x2094A3B8), Color.Transparent))

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(cardBg)
            .border(1.2.dp, cardBorder, cardShape)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(iconBg), contentAlignment = Alignment.Center) { iconContent() }
                CircularProgressRing(progress = progress, color = progressColor, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            val fontSize = when {
                value.length > 7 -> 14.sp
                value.length > 5 -> 16.sp
                else -> 19.sp
            }
            Text(
                text = value,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
