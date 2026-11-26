package app.monitor.feature.gitworkspace.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.GitFileChange

/**
 * Clean Minimalist Git Activity & Code Churn HUD (< 170 lines).
 * Displays living animated commit spectrum equalizer bars, churn sparkline, and diff metrics.
 */
@Composable
fun RepoActivitySparklineCard(
    files: List<GitFileChange>,
    currentBranch: String,
    modifier: Modifier = Modifier
) {
    val totalInsertions = files.sumOf { it.insertions }
    val totalDeletions = files.sumOf { it.deletions }
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0xFF0C0E1A).copy(alpha = 0.94f))
            .border(
                1.2.dp,
                Brush.linearGradient(listOf(Color(0x60818CF8), Color(0x30C084FC), Color(0x5000E5FF))),
                cardShape
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: Living Icon + Title + Live Sync Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1738))
                            .border(1.dp, Color(0x80C084FC), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        LivingGitBranchNode(tint = Color(0xFF00E5FF), size = 18.dp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(text = "Working Tree Velocity", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "Branch: $currentBranch • Live Index", fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                    }
                }

                // Diff Pills (+insertions / -deletions)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF064E3B).copy(alpha = 0.50f))
                            .border(1.dp, Color(0x7010B981), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text("+$totalInsertions", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7F1D1D).copy(alpha = 0.40f))
                            .border(1.dp, Color(0x70EF4444), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text("-$totalDeletions", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                    }
                }
            }

            // Real-Time Animated Churn Spectrum Canvas
            Row(modifier = Modifier.fillMaxWidth().height(92.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.height(92.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("100%", fontSize = 7.5.sp, color = Color(0xFF64748B))
                    Text("50%", fontSize = 7.5.sp, color = Color(0xFF64748B))
                    Text("0%", fontSize = 7.5.sp, color = Color(0xFF64748B))
                }
                Spacer(modifier = Modifier.width(8.dp))
                ChurnEqualizerCanvas(files, Modifier.weight(1f).height(92.dp))
            }
        }
    }
}

@Composable
private fun ChurnEqualizerCanvas(files: List<GitFileChange>, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ChurnPulse")
    val laserPulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "laserPulse"
    )

    val points = remember(files) {
        if (files.isEmpty()) emptyList()
        else {
            val totalChurn = files.sumOf { it.insertions + it.deletions }
            files.map { f ->
                val churn = f.insertions + f.deletions
                if (totalChurn > 0) ((churn.toFloat() / totalChurn) * 100f).coerceIn(8f, 95f)
                else 10f
            }.takeLast(30)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        for (i in 0..2) drawLine(Color(0x15818CF8), Offset(0f, (h / 2f) * i), Offset(w, (h / 2f) * i), strokeWidth = 1.dp.toPx())

        val stepX = w / (points.size - 1).coerceAtLeast(1)
        val splinePath = Path()
        var lastX = 0f; var lastY = 0f
        val barWidth = (stepX * 0.55f).coerceAtLeast(2.4f)

        points.forEachIndexed { i, v ->
            val x = i * stepX
            val barH = ((v / 100f).coerceIn(0.08f, 0.94f) * h)
            val topY = h - barH
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFF818CF8).copy(0.45f), Color(0xFF312E81).copy(0.15f)), topY, h),
                topLeft = Offset(x - barWidth / 2f, topY), size = Size(barWidth, barH), cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx())
            )
            if (i == 0) splinePath.moveTo(x, topY)
            else {
                val prevX = (i - 1) * stepX
                val prevTopY = h - ((points[i - 1] / 100f).coerceIn(0.08f, 0.94f) * h)
                val cX = (prevX + x) / 2f
                splinePath.cubicTo(cX, prevTopY, cX, topY, x, topY)
            }
            if (i == points.lastIndex) { lastX = x; lastY = topY }
        }

        if (points.isNotEmpty()) {
            drawPath(splinePath, Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFFC084FC), Color(0xFF00E5FF))), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
            drawLine(Color(0x5000E5FF), Offset(lastX, 0f), Offset(lastX, h), strokeWidth = 1.dp.toPx())
            val rippleR = (3f + laserPulse * 8f).dp.toPx()
            drawCircle(Color(0xFF00E5FF).copy((1f - laserPulse) * 0.75f), radius = rippleR, center = Offset(lastX, lastY), style = Stroke(1.4.dp.toPx()))
            drawCircle(Color(0xFF00E5FF), radius = 3.2.dp.toPx(), center = Offset(lastX, lastY))
            drawCircle(Color.White, radius = 1.6.dp.toPx(), center = Offset(lastX, lastY))
        }
    }
}
