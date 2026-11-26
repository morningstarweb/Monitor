package app.monitor.feature.dashboard.components

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
import androidx.compose.ui.unit.dp

/**
 * Living Character Icons for Home Page (< 170 lines).
 */
@Composable
fun LivingMonitorLogo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "MonitorScreenLive")
    val wavePhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )

    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF2A2E42), Color(0xFF101322))),
            Offset(w * 0.08f, h * 0.08f), Size(w * 0.84f, h * 0.58f), CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(1.6.dp.toPx())
        )
        drawRoundRect(Color(0xFF070B18), Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.50f), CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()))

        val wavePath = Path().apply {
            moveTo(w * 0.14f, h * 0.37f); lineTo(w * 0.32f, h * 0.37f); lineTo(w * 0.44f, h * 0.22f)
            lineTo(w * 0.56f, h * 0.52f); lineTo(w * 0.68f, h * 0.37f); lineTo(w * 0.84f, h * 0.37f)
        }
        drawPath(wavePath, Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFC084FC))), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color(0xFF00E5FF), radius = 2.dp.toPx(), center = Offset(w * (0.14f + 0.70f * wavePhase), h * 0.37f))

        drawLine(Color(0xFF64748B), Offset(w * 0.50f, h * 0.66f), Offset(w * 0.50f, h * 0.82f), strokeWidth = 2.dp.toPx())
        drawLine(Color(0xFF818CF8), Offset(w * 0.30f, h * 0.82f), Offset(w * 0.70f, h * 0.82f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color(0xFF10B981), radius = 1.2.dp.toPx(), center = Offset(w * 0.86f, h * 0.61f))
    }
}

@Composable
fun LivingBatteryCell(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "BatteryBubbles")
    val bubbleY by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "bubbleY"
    )
    val flashPulse by transition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flashPulse"
    )

    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(Color(0xFF10B981), Offset(w * 0.18f, h * 0.12f), Size(w * 0.64f, h * 0.76f), CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = Stroke(1.6.dp.toPx()))
        drawRoundRect(Color(0xFF10B981), Offset(w * 0.38f, h * 0.04f), Size(w * 0.24f, h * 0.08f), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF34D399).copy(0.45f), Color(0xFF064E3B).copy(0.60f))),
            Offset(w * 0.22f, h * 0.35f), Size(w * 0.56f, h * 0.49f), CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawCircle(Color(0xFF34D399), radius = 1.3.dp.toPx(), center = Offset(w * 0.36f, h * (0.80f - 0.45f * bubbleY)))
        drawCircle(Color(0xFF34D399), radius = 1.1.dp.toPx(), center = Offset(w * 0.62f, h * (0.75f - 0.35f * ((bubbleY + 0.5f) % 1f))))

        val bolt = Path().apply {
            moveTo(w * 0.54f, h * 0.25f); lineTo(w * 0.42f, h * 0.50f); lineTo(w * 0.52f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.72f); lineTo(w * 0.62f, h * 0.44f); lineTo(w * 0.50f, h * 0.44f); close()
        }
        drawPath(bolt, Color(0xFF00E5FF).copy(alpha = flashPulse))
    }
}

@Composable
fun LivingCpuProcessor(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "CpuDataPulse")
    val busPulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "busPulse"
    )

    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95))), Offset(w * 0.22f, h * 0.22f), Size(w * 0.56f, h * 0.56f), CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = Stroke(1.6.dp.toPx()))
        drawRoundRect(Color(0xFFC084FC).copy(0.40f), Offset(w * 0.34f, h * 0.34f), Size(w * 0.32f, h * 0.32f), CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()))

        val pins = listOf(
            Offset(w * 0.34f, h * 0.10f) to Offset(w * 0.34f, h * 0.22f), Offset(w * 0.66f, h * 0.10f) to Offset(w * 0.66f, h * 0.22f),
            Offset(w * 0.34f, h * 0.78f) to Offset(w * 0.34f, h * 0.90f), Offset(w * 0.66f, h * 0.78f) to Offset(w * 0.66f, h * 0.90f),
            Offset(w * 0.10f, h * 0.34f) to Offset(w * 0.22f, h * 0.34f), Offset(w * 0.10f, h * 0.66f) to Offset(w * 0.22f, h * 0.66f),
            Offset(w * 0.78f, h * 0.34f) to Offset(w * 0.90f, h * 0.34f), Offset(w * 0.78f, h * 0.66f) to Offset(w * 0.90f, h * 0.66f)
        )
        pins.forEach { (s, e) -> drawLine(Color(0xFFC084FC), s, e, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round) }

        val pOff = busPulse * 0.14f * w
        drawCircle(Color(0xFF00E5FF), radius = 1.3.dp.toPx(), center = Offset(w * 0.50f, h * 0.50f - pOff))
        drawCircle(Color(0xFF00E5FF), radius = 1.3.dp.toPx(), center = Offset(w * 0.50f + pOff, h * 0.50f))
    }
}

@Composable
fun LivingMemoryRam(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "RamCycle")
    val cycle by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "cycle"
    )

    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(Color(0xFF00E5FF), Offset(w * 0.10f, h * 0.26f), Size(w * 0.80f, h * 0.48f), CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        for (i in 0..2) {
            val chipX = w * (0.18f + i * 0.24f)
            val isCurrent = ((cycle * 3).toInt() == i)
            drawRoundRect(if (isCurrent) Color(0xFF00E5FF) else Color(0xFF0C4A6E), Offset(chipX, h * 0.34f), Size(w * 0.18f, h * 0.32f), CornerRadius(1.dp.toPx(), 1.dp.toPx()))
        }
        for (i in 0..5) drawLine(Color(0xFF38BDF8), Offset(w * (0.18f + i * 0.12f), h * 0.74f), Offset(w * (0.18f + i * 0.12f), h * 0.84f), strokeWidth = 1.2.dp.toPx())
    }
}
