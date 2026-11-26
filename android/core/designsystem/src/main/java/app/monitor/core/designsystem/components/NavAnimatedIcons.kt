package app.monitor.core.designsystem.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Highly animated navigation icons. Every single icon is alive at all times,
 * expressing its individual character through continuous semantic motion.
 */

@Composable
fun NavHomeAnimatedIcon(selected: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "HomePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hearth"
    )

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val sW = 1.7.dp.toPx()
        val roof = Path().apply { moveTo(w * 0.14f, h * 0.44f); lineTo(w * 0.50f, h * 0.14f); lineTo(w * 0.86f, h * 0.44f) }
        val walls = Path().apply { moveTo(w * 0.22f, h * 0.40f); lineTo(w * 0.22f, h * 0.84f); lineTo(w * 0.78f, h * 0.84f); lineTo(w * 0.78f, h * 0.40f) }
        val door = Path().apply { moveTo(w * 0.40f, h * 0.84f); lineTo(w * 0.40f, h * 0.54f); lineTo(w * 0.60f, h * 0.54f); lineTo(w * 0.60f, h * 0.84f) }

        drawPath(roof, color, style = Stroke(sW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(walls, color, style = Stroke(sW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(door, color, style = Stroke(sW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Living hearth breathing beacon
        val glowColor = if (selected) Color(0xFF00E5FF).copy(alpha = pulse) else Color(0xFF818CF8).copy(alpha = pulse * 0.65f)
        drawCircle(glowColor, radius = (1.8f + pulse * 1.0f).dp.toPx(), center = Offset(w * 0.50f, h * 0.68f))
        drawCircle(if (selected) Color(0xFFC084FC).copy(alpha = pulse) else Color(0xFF64748B), radius = 1.5.dp.toPx(), center = Offset(w * 0.50f, h * 0.14f))
    }
}

@Composable
fun NavProjectsAnimatedIcon(selected: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ProjectsHover")
    val hover by transition.animateFloat(
        initialValue = -1.0f, targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hover"
    )

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val sW = 1.6.dp.toPx()
        val shiftPx = if (selected) (-3.5f - hover * 0.6f).dp.toPx() else (-hover).dp.toPx()

        // Tab line
        val backTab = Path().apply {
            moveTo(w * 0.14f, h * 0.38f); lineTo(w * 0.14f, h * 0.30f); lineTo(w * 0.40f, h * 0.30f)
            lineTo(w * 0.48f, h * 0.38f); lineTo(w * 0.86f, h * 0.38f)
        }
        drawPath(backTab, color.copy(alpha = 0.60f), style = Stroke(sW, cap = StrokeCap.Round))

        // Project file card sliding and hovering with active code lines
        drawRoundRect(
            color = if (selected) Color(0xFF00E5FF).copy(alpha = 0.40f) else Color(0xFF818CF8).copy(alpha = 0.22f),
            topLeft = Offset(w * 0.28f, h * 0.28f + shiftPx),
            size = Size(w * 0.44f, h * 0.42f),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )
        drawLine(
            if (selected) (if (isDark) Color.White else Color(0xFF0F172A)) else color.copy(alpha = 0.7f),
            Offset(w * 0.34f, h * 0.38f + shiftPx),
            Offset(w * 0.60f, h * 0.38f + shiftPx),
            strokeWidth = 1.2.dp.toPx()
        )

        // Front folder body
        drawRoundRect(color, Offset(w * 0.14f, h * 0.38f), Size(w * 0.72f, h * 0.46f), CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(sW))
    }
}

@Composable
fun NavGitAnimatedIcon(selected: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "GitStream")
    val flow by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "flow"
    )

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val sW = 1.7.dp.toPx()
        val root = Offset(w * 0.32f, h * 0.74f); val mainHead = Offset(w * 0.32f, h * 0.24f); val forkHead = Offset(w * 0.72f, h * 0.38f)
        drawLine(color, root, mainHead, strokeWidth = sW)
        val fork = Path().apply { moveTo(root.x, h * 0.52f); quadraticTo(root.x + w * 0.08f, forkHead.y, forkHead.x, forkHead.y) }
        drawPath(fork, color, style = Stroke(sW, cap = StrokeCap.Round))

        val nodeR = 2.4.dp.toPx()
        drawCircle(color, radius = nodeR, center = root)
        drawCircle(if (selected) Color(0xFFC084FC) else color, radius = nodeR, center = mainHead)
        drawCircle(if (selected) Color(0xFF00E5FF) else color, radius = nodeR, center = forkHead)

        // Live traveling commit packet (always flowing across the fork)
        val t = flow
        val dotX = (1 - t) * (1 - t) * root.x + 2 * (1 - t) * t * (root.x + w * 0.08f) + t * t * forkHead.x
        val dotY = (1 - t) * (1 - t) * (h * 0.52f) + 2 * (1 - t) * t * forkHead.y + t * t * forkHead.y
        drawCircle(
            color = if (selected) Color(0xFF00E5FF) else Color(0xFF38BDF8).copy(alpha = 0.85f),
            radius = (if (selected) 2.4f else 2.0f).dp.toPx(),
            center = Offset(dotX, dotY)
        )
    }
}

@Composable
fun NavSystemAnimatedIcon(selected: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "CardiacSweep")
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val sW = 1.7.dp.toPx()
        val wave = Path().apply {
            moveTo(w * 0.12f, h * 0.50f); lineTo(w * 0.28f, h * 0.50f); lineTo(w * 0.40f, h * 0.20f)
            lineTo(w * 0.54f, h * 0.80f); lineTo(w * 0.66f, h * 0.36f); lineTo(w * 0.74f, h * 0.50f); lineTo(w * 0.88f, h * 0.50f)
        }
        drawPath(wave, color, style = Stroke(sW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Sweeping cardiac oscilloscope laser pulse (always sweeping)
        val scanX = w * (0.12f + 0.76f * sweep)
        val scanY = when {
            scanX < w * 0.28f -> h * 0.50f
            scanX < w * 0.40f -> h * 0.50f - (scanX - w * 0.28f) / (w * 0.12f) * (h * 0.30f)
            scanX < w * 0.54f -> h * 0.20f + (scanX - w * 0.40f) / (w * 0.14f) * (h * 0.60f)
            scanX < w * 0.66f -> h * 0.80f - (scanX - w * 0.54f) / (w * 0.12f) * (h * 0.44f)
            else -> h * 0.50f
        }
        drawCircle(
            color = if (selected) Color(0xFF10B981) else Color(0xFF34D399).copy(alpha = 0.85f),
            radius = (if (selected) 2.6f else 2.0f).dp.toPx(),
            center = Offset(scanX, scanY)
        )
    }
}

@Composable
fun NavSettingsAnimatedIcon(selected: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "GearSpin")
    val gearAngle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "gearAngle"
    )

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    Canvas(modifier = modifier.size(22.dp)) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        rotate(degrees = gearAngle, pivot = center) {
            val r = size.width * 0.30f
            drawCircle(color, radius = r, style = Stroke(1.6.dp.toPx()))
            for (i in 0 until 6) {
                val rad = Math.toRadians((i * 60.0)).toFloat()
                val s = Offset(center.x + r * cos(rad), center.y + r * sin(rad))
                val e = Offset(center.x + (r + 2.4.dp.toPx()) * cos(rad), center.y + (r + 2.4.dp.toPx()) * sin(rad))
                drawLine(color, s, e, strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(if (selected) Color(0xFF00E5FF) else Color(0xFF818CF8).copy(alpha = 0.80f), radius = 1.8.dp.toPx(), center = center)
        }
    }
}
