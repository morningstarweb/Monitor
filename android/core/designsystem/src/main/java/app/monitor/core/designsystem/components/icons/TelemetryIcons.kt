package app.monitor.core.designsystem.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun MemoryCylinderIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()

        drawOval(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(w, h * 0.35f),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(0f, h * 0.32f),
            size = Size(w, h * 0.35f),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(0f, h * 0.65f),
            size = Size(w, h * 0.35f),
            style = Stroke(width = stroke)
        )
        drawLine(color = tint, start = Offset(0f, h * 0.17f), end = Offset(0f, h * 0.82f), strokeWidth = stroke)
        drawLine(color = tint, start = Offset(w, h * 0.17f), end = Offset(w, h * 0.82f), strokeWidth = stroke)
    }
}

@Composable
fun LayersIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()

        val topPath = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h * 0.28f)
            lineTo(w / 2f, h * 0.56f)
            lineTo(0f, h * 0.28f)
            close()
        }
        drawPath(topPath, color = tint, style = Stroke(width = stroke))

        val bottomPath = Path().apply {
            moveTo(0f, h * 0.58f)
            lineTo(w / 2f, h * 0.86f)
            lineTo(w, h * 0.58f)
        }
        drawPath(bottomPath, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun PulseChartIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        val path = Path().apply {
            moveTo(0f, h * 0.6f)
            lineTo(w * 0.25f, h * 0.6f)
            lineTo(w * 0.42f, h * 0.15f)
            lineTo(w * 0.62f, h * 0.88f)
            lineTo(w * 0.78f, h * 0.45f)
            lineTo(w, h * 0.45f)
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun FolderIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.2f)
            lineTo(w * 0.4f, h * 0.2f)
            lineTo(w * 0.5f, h * 0.35f)
            lineTo(w, h * 0.35f)
            lineTo(w, h * 0.85f)
            lineTo(0f, h * 0.85f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun CloudIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()

        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.7f)
            cubicTo(0f, h * 0.7f, 0f, h * 0.4f, w * 0.25f, h * 0.35f)
            cubicTo(w * 0.25f, h * 0.15f, w * 0.6f, h * 0.1f, w * 0.7f, h * 0.3f)
            cubicTo(w, h * 0.25f, w, h * 0.65f, w * 0.8f, h * 0.7f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun CloudUploadIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()

        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.75f)
            cubicTo(0f, h * 0.75f, 0f, h * 0.45f, w * 0.25f, h * 0.4f)
            cubicTo(w * 0.25f, h * 0.2f, w * 0.6f, h * 0.15f, w * 0.7f, h * 0.35f)
            cubicTo(w, h * 0.3f, w, h * 0.7f, w * 0.8f, h * 0.75f)
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawLine(color = tint, start = Offset(w / 2f, h * 0.85f), end = Offset(w / 2f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.38f, h * 0.62f), end = Offset(w / 2f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.62f, h * 0.62f), end = Offset(w / 2f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun LightningBoltIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.6f, 0f)
            lineTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.5f, h * 0.55f)
            lineTo(w * 0.4f, h)
            lineTo(w * 0.85f, h * 0.45f)
            lineTo(w * 0.5f, h * 0.45f)
            close()
        }
        drawPath(path, color = tint)
    }
}
