package app.monitor.feature.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

import app.monitor.core.designsystem.components.MonitorGlobalBackgroundCanvas

/**
 * Pure Compose Google "G" logo vector
 */
@Composable
fun GoogleGLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.22f

        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(w * 0.5f, h * 0.5f),
            end = Offset(w * 0.95f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Pure Compose Laptop Icon
 */
@Composable
fun SettingsLaptopIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        val screenLeft = w * 0.15f
        val screenTop = h * 0.15f
        val screenRight = w * 0.85f
        val screenBottom = h * 0.65f
        val radius = 2.dp.toPx()

        drawRoundRect(
            color = tint,
            topLeft = Offset(screenLeft, screenTop),
            size = Size(screenRight - screenLeft, screenBottom - screenTop),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = stroke)
        )

        val baseLeft = w * 0.08f
        val baseRight = w * 0.92f
        val baseY = h * 0.82f

        drawLine(
            color = tint,
            start = Offset(baseLeft, baseY),
            end = Offset(baseRight, baseY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Ambient Sweeping Background Decorations (< 20 lines).
 * Delegates to the unified global animated background canvas.
 */
@Composable
fun SettingsBackgroundDecorations(modifier: Modifier = Modifier) {
    MonitorGlobalBackgroundCanvas(modifier = modifier)
}
