package app.monitor.core.designsystem.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun GitNodeIcon(modifier: Modifier = Modifier.size(24.dp)) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val rootCenter = Offset(w * 0.43f, h * 0.72f)
        val rootRadius = w * 0.082f
        val strokeW = w * 0.045f

        val topLeftCenter = Offset(w * 0.43f, h * 0.28f)
        val topLeftRadius = w * 0.085f

        val topRightCenter = Offset(w * 0.77f, h * 0.46f)
        val topRightRadius = w * 0.085f

        val branchJunction = Offset(w * 0.43f, h * 0.58f)
        val cyanColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)

        drawLine(color = cyanColor, start = rootCenter, end = topLeftCenter, strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color = cyanColor, start = branchJunction, end = topRightCenter, strokeWidth = strokeW, cap = StrokeCap.Round)
        drawCircle(color = cyanColor, radius = topLeftRadius, center = topLeftCenter)
        drawCircle(color = cyanColor, radius = topRightRadius, center = topRightCenter)
        drawCircle(color = if (isDark) Color(0xFF0F172A) else Color.White, radius = rootRadius, center = rootCenter)
        drawCircle(color = cyanColor, radius = rootRadius, center = rootCenter, style = Stroke(width = strokeW))
    }
}

@Composable
fun BranchForkIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawLine(color = tint, start = Offset(w * 0.35f, h * 0.2f), end = Offset(w * 0.35f, h * 0.8f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.35f, h * 0.5f), end = Offset(w * 0.75f, h * 0.25f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.35f, h * 0.2f))
        drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.75f, h * 0.25f))
        drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.35f, h * 0.8f))
    }
}

@Composable
fun CodeFileIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()

        drawLine(color = tint, start = Offset(w * 0.35f, h * 0.3f), end = Offset(w * 0.15f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.15f, h * 0.5f), end = Offset(w * 0.35f, h * 0.7f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.42f, h * 0.72f), end = Offset(w * 0.58f, h * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.65f, h * 0.3f), end = Offset(w * 0.85f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.85f, h * 0.5f), end = Offset(w * 0.65f, h * 0.7f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun SwitchArrowsIcon(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawLine(color = tint, start = Offset(w * 0.15f, h * 0.35f), end = Offset(w * 0.85f, h * 0.35f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.65f, h * 0.18f), end = Offset(w * 0.85f, h * 0.35f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.85f, h * 0.65f), end = Offset(w * 0.15f, h * 0.65f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.35f, h * 0.82f), end = Offset(w * 0.15f, h * 0.65f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun FilterFunnelIcon(tint: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()

        drawLine(color = tint, start = Offset(0f, h * 0.2f), end = Offset(w, h * 0.2f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.2f, h * 0.5f), end = Offset(w * 0.8f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.4f, h * 0.8f), end = Offset(w * 0.6f, h * 0.8f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
