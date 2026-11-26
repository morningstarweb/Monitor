package app.monitor.feature.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Custom vector path drawing a modern Windows 4-square logo.
 */
@Composable
fun WindowsLogoIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val gap = 1.5.dp.toPx()
        val corner = 1.dp.toPx()

        val halfW = (w - gap) / 2f
        val halfH = (h - gap) / 2f

        // Top Left Square
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(halfW, halfH),
            cornerRadius = CornerRadius(corner, corner)
        )

        // Top Right Square
        drawRoundRect(
            color = tint,
            topLeft = Offset(halfW + gap, 0f),
            size = Size(halfW, halfH),
            cornerRadius = CornerRadius(corner, corner)
        )

        // Bottom Left Square
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, halfH + gap),
            size = Size(halfW, halfH),
            cornerRadius = CornerRadius(corner, corner)
        )

        // Bottom Right Square
        drawRoundRect(
            color = tint,
            topLeft = Offset(halfW + gap, halfH + gap),
            size = Size(halfW, halfH),
            cornerRadius = CornerRadius(corner, corner)
        )
    }
}

/**
 * Custom vector path drawing a Laptop / Desktop Monitor icon.
 */
@Composable
fun LaptopMonitorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Screen outline
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

        // Laptop base
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
 * Custom vector path drawing an Audit Layers icon.
 */
@Composable
fun AuditLayersIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Top rhombus layer
        val path1 = Path().apply {
            moveTo(w * 0.5f, h * 0.2f)
            lineTo(w * 0.85f, h * 0.35f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.15f, h * 0.35f)
            close()
        }
        drawPath(path = path1, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Middle layer arc
        val path2 = Path().apply {
            moveTo(w * 0.15f, h * 0.52f)
            lineTo(w * 0.5f, h * 0.67f)
            lineTo(w * 0.85f, h * 0.52f)
        }
        drawPath(path = path2, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Bottom layer arc
        val path3 = Path().apply {
            moveTo(w * 0.15f, h * 0.70f)
            lineTo(w * 0.5f, h * 0.85f)
            lineTo(w * 0.85f, h * 0.70f)
        }
        drawPath(path = path3, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}
