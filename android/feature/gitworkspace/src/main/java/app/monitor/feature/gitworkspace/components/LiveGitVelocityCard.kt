package app.monitor.feature.gitworkspace.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.core.model.GitFileChange
import app.monitor.core.model.GitWorkspaceSnapshot

private fun formatCompactLines(raw: Long): String {
    val abs = kotlin.math.abs(raw)
    return when {
        abs >= 1_000_000 -> "${abs / 1_000_000}.${(abs % 1_000_000) / 100_000}M"
        abs >= 10_000 -> "${abs / 1_000}k"
        else -> "$abs"
    }
}

/**
 * Minimalist Living Git Velocity Graph Card (< 195 lines).
 * Follows the unified Obsidian/Crystal glass design system.
 */
@Composable
fun LiveGitVelocityCard(
    snapshot: GitWorkspaceSnapshot?,
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    val files = snapshot?.files.orEmpty()
    val totalIns = files.sumOf { it.insertions.toLong() }
    val totalDel = files.sumOf { it.deletions.toLong() }
    val cardShape = RoundedCornerShape(24.dp)
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)
    val cardBorder = if (isDark) Brush.linearGradient(listOf(Color(0x60818CF8), Color(0x30C084FC), Color(0x5000E5FF)))
                     else Brush.linearGradient(listOf(Color(0x30CBD5E1), Color(0x2094A3B8)))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(1.2.dp, cardBorder, cardShape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isDark) Color(0xFF1E1738) else Color(0xFFEDE9FE))
                            .border(1.dp, if (isDark) Color(0x70C084FC) else Color(0x40A855F7), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        LivingGitBranchNode(tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF7C3AED), size = 16.dp)
                    }

                    Text(
                        text = "Commit Velocity",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF064E3B).copy(alpha = 0.50f) else Color(0xFFECFDF5))
                        .border(1.dp, if (isDark) Color(0x8010B981) else Color(0x4010B981), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(5.dp).background(if (isDark) Color(0xFF10B981) else Color(0xFF059669), CircleShape))
                        val insStr = formatCompactLines(totalIns)
                        val delStr = formatCompactLines(totalDel)
                        Text(
                            text = "+$insStr / -$delStr",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().height(115.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.height(115.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("100%", fontSize = 8.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B))
                    Text("50%", fontSize = 8.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B))
                    Text("0%", fontSize = 8.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B))
                }
                Spacer(modifier = Modifier.width(8.dp))
                GitVelocityEqualizerCanvas(files, isDark, Modifier.weight(1f).height(115.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(start = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("-7d", fontSize = 8.5.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B))
                Text("-3d", fontSize = 8.5.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B))
                Text("-1d", fontSize = 8.5.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B))
                Text("Now", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7))
            }
        }
    }
}

@Composable
private fun GitVelocityEqualizerCanvas(files: List<*>, isDark: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "GitReticlePulse")
    val reticlePulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "reticlePulse"
    )

    val points = remember(files) {
        if (files.isEmpty()) emptyList()
        else {
            val totalChanges = files.sumOf { (it as? GitFileChange)?.let { f -> f.insertions + f.deletions } ?: 0 }
            files.map { f ->
                val ch = (f as? GitFileChange)?.let { it.insertions + it.deletions } ?: 0
                if (totalChanges > 0) ((ch.toFloat() / totalChanges) * 100f).coerceIn(4f, 96f)
                else 10f
            }.takeLast(36)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val gridColor = if (isDark) Color(0x15818CF8) else Color(0x20CBD5E1)
        for (i in 0..2) drawLine(gridColor, Offset(0f, (h / 2f) * i), Offset(w, (h / 2f) * i), strokeWidth = 1.dp.toPx())

        val stepX = w / (points.size - 1).coerceAtLeast(1)
        val splinePath = Path()
        var lastX = 0f; var lastY = 0f
        val barWidth = (stepX * 0.55f).coerceAtLeast(2.5f)

        points.forEachIndexed { i, v ->
            val x = i * stepX
            val barH = ((v / 100f).coerceIn(0.06f, 0.94f) * h)
            val topY = h - barH
            val barColors = if (isDark) listOf(Color(0xFF00E5FF), Color(0xFF818CF8).copy(0.5f), Color(0xFF312E81).copy(0.2f))
                            else listOf(Color(0xFF0284C7), Color(0xFF818CF8).copy(0.4f), Color(0xFFE0E7FF).copy(0.2f))
            drawRoundRect(
                brush = Brush.verticalGradient(barColors, topY, h),
                topLeft = Offset(x - barWidth / 2f, topY), size = Size(barWidth, barH), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
            if (i == 0) splinePath.moveTo(x, topY)
            else {
                val prevX = (i - 1) * stepX
                val prevTopY = h - ((points[i - 1] / 100f).coerceIn(0.06f, 0.94f) * h)
                val cX = (prevX + x) / 2f
                splinePath.cubicTo(cX, prevTopY, cX, topY, x, topY)
            }
            if (i == points.lastIndex) { lastX = x; lastY = topY }
        }

        if (points.isNotEmpty()) {
            drawPath(splinePath, Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFFC084FC), Color(0xFF00E5FF))), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
            drawLine(Color(0x5000E5FF), Offset(lastX, 0f), Offset(lastX, h), strokeWidth = 1.dp.toPx())
            val rippleR = (3f + reticlePulse * 9f).dp.toPx()
            drawCircle(Color(0xFF00E5FF).copy((1f - reticlePulse) * 0.75f), radius = rippleR, center = Offset(lastX, lastY), style = Stroke(1.5.dp.toPx()))
            drawCircle(Color(0xFF00E5FF), radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))
            drawCircle(Color.White, radius = 1.8.dp.toPx(), center = Offset(lastX, lastY))
        }
    }
}
