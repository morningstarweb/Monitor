package app.monitor.feature.gitworkspace.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Living animated vector icons for Git Workspace (< 190 lines).
 */
@Composable
fun LivingFolderLogo(size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "FolderMotion")
    val pulse by tr.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pulse")
    val beaconAlpha by tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), "beacon")

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width * pulse; val h = this.size.height * pulse
        val offX = (this.size.width - w) / 2f; val offY = (this.size.height - h) / 2f

        val backPath = Path().apply {
            moveTo(offX + w * 0.10f, offY + h * 0.22f); lineTo(offX + w * 0.42f, offY + h * 0.22f)
            lineTo(offX + w * 0.52f, offY + h * 0.35f); lineTo(offX + w * 0.88f, offY + h * 0.35f)
            lineTo(offX + w * 0.88f, offY + h * 0.80f); lineTo(offX + w * 0.10f, offY + h * 0.80f)
            close()
        }
        drawPath(backPath, Brush.verticalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95))))

        val frontPath = Path().apply {
            moveTo(offX + w * 0.08f, offY + h * 0.38f); lineTo(offX + w * 0.92f, offY + h * 0.38f)
            lineTo(offX + w * 0.86f, offY + h * 0.86f); lineTo(offX + w * 0.08f, offY + h * 0.86f)
            close()
        }
        drawPath(frontPath, Brush.verticalGradient(listOf(Color(0xFFC084FC), Color(0xFF7C3AED))))
        drawPath(frontPath, Color.White.copy(0.35f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))

        drawCircle(Color(0xFF10B981).copy(alpha = beaconAlpha * 0.5f), radius = 3.5.dp.toPx(), center = Offset(offX + w * 0.82f, offY + h * 0.44f))
        drawCircle(Color(0xFF34D399), radius = 2.dp.toPx(), center = Offset(offX + w * 0.82f, offY + h * 0.44f))
    }
}

@Composable
fun LivingGitBranchNode(tint: Color = Color(0xFF00E5FF), size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "BranchMotion")
    val orbitPhase by tr.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), "orbit")

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val mainX = w * 0.32f; val branchX = w * 0.72f
        val topY = h * 0.24f; val midY = h * 0.52f; val bottomY = h * 0.80f

        drawLine(tint, Offset(mainX, topY), Offset(mainX, bottomY), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val fork = Path().apply {
            moveTo(mainX, midY)
            cubicTo(mainX + w * 0.14f, midY, branchX, midY + h * 0.04f, branchX, topY)
        }
        drawPath(fork, tint, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))

        drawCircle(Color(0xFF131728), radius = 3.2.dp.toPx(), center = Offset(mainX, bottomY))
        drawCircle(tint, radius = 2.2.dp.toPx(), center = Offset(mainX, bottomY))

        drawCircle(Color(0xFF131728), radius = 3.2.dp.toPx(), center = Offset(mainX, topY))
        drawCircle(tint, radius = 2.2.dp.toPx(), center = Offset(mainX, topY))

        drawCircle(Color(0xFF131728), radius = 3.8.dp.toPx(), center = Offset(branchX, topY))
        drawCircle(tint, radius = 2.5.dp.toPx(), center = Offset(branchX, topY))

        val orbR = 5.2.dp.toPx()
        val orbX = branchX + orbR * cos(orbitPhase.toDouble()).toFloat()
        val orbY = topY + orbR * sin(orbitPhase.toDouble()).toFloat()
        drawCircle(Color(0xFFC084FC), radius = 1.3.dp.toPx(), center = Offset(orbX, orbY))
    }
}

@Composable
fun LivingCommitLayers(tint: Color = Color.White, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "LayersLevitation")
    val levitate by tr.animateFloat(-1.2f, 1.2f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "levitate")

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height; val cX = w * 0.5f
        for (i in 0..2) {
            val yOffset = h * (0.28f + i * 0.24f) + (if (i == 0) levitate.dp.toPx() else 0f)
            val p = Path().apply {
                moveTo(cX, yOffset - h * 0.12f); lineTo(cX + w * 0.38f, yOffset)
                lineTo(cX, yOffset + h * 0.12f); lineTo(cX - w * 0.38f, yOffset); close()
            }
            val alpha = when (i) { 0 -> 1f; 1 -> 0.70f; else -> 0.40f }
            drawPath(p, tint.copy(alpha = alpha), style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun LivingCloudSync(tint: Color = Color.White, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "CloudBreeze")
    val breeze by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), "breeze")

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawCircle(tint.copy(0.85f), radius = w * 0.18f, center = Offset(w * 0.38f, h * 0.42f))
        drawCircle(tint.copy(0.85f), radius = w * 0.24f, center = Offset(w * 0.60f, h * 0.38f))
        drawRoundRect(tint.copy(0.85f), topLeft = Offset(w * 0.20f, h * 0.44f), size = Size(w * 0.62f, h * 0.26f), cornerRadius = CornerRadius(h * 0.13f, h * 0.13f))
        val partY = h * (0.90f - breeze * 0.45f)
        drawCircle(Color(0xFF00E5FF).copy(1f - breeze), radius = 1.4.dp.toPx(), center = Offset(w * 0.5f, partY))
    }
}

@Composable
fun LivingPullIcon(tint: Color = Color(0xFF10B981), size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "PullCascade")
    val drop by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), "drop")

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height; val cX = w * 0.5f
        drawLine(tint, Offset(cX, h * 0.20f), Offset(cX, h * 0.72f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val arrow = Path().apply {
            moveTo(cX - w * 0.24f, h * 0.52f); lineTo(cX, h * 0.76f); lineTo(cX + w * 0.24f, h * 0.52f)
        }
        drawPath(arrow, tint, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color.White.copy(1f - drop), radius = 1.6.dp.toPx(), center = Offset(cX, h * (0.20f + drop * 0.50f)))
    }
}

@Composable
fun LivingPushIcon(tint: Color = Color(0xFF00E5FF), size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "PushCascade")
    val thrust by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), "thrust")

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height; val cX = w * 0.5f
        drawLine(tint, Offset(cX, h * 0.80f), Offset(cX, h * 0.28f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val arrow = Path().apply {
            moveTo(cX - w * 0.24f, h * 0.48f); lineTo(cX, h * 0.24f); lineTo(cX + w * 0.24f, h * 0.48f)
        }
        drawPath(arrow, tint, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color.White.copy(1f - thrust), radius = 1.6.dp.toPx(), center = Offset(cX, h * (0.80f - thrust * 0.50f)))
    }
}
