package app.monitor.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.theme.ChartGridLine
import app.monitor.core.designsystem.theme.PrimaryAccent

@Composable
fun MetricChartCanvas(
    seriesList: List<MetricSeries>,
    selectedMetricName: String?,
    animProgress: Float,
    activeTouchX: Float,
    onTouchXChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        onTouchXChange(offset.x)
                        tryAwaitRelease()
                        onTouchXChange(-1f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onTouchXChange(offset.x) },
                    onDragEnd = { onTouchXChange(-1f) },
                    onDragCancel = { onTouchXChange(-1f) },
                    onDrag = { change, _ -> onTouchXChange(change.position.x) }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val paddingBottom = 16f
        val chartHeight = h - paddingBottom

        // Draw Horizontal Gridlines (100%, 75%, 50%, 25%, 0%)
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = (chartHeight / gridLines) * i
            drawLine(
                color = ChartGridLine,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        }

        // Render Each Series
        seriesList.forEach { series: MetricSeries ->
            val isHighlighted = selectedMetricName == null || selectedMetricName == series.name
            if (isHighlighted) {
                val data = series.data
                if (data.size >= 2) {
                    val stepX = w / (data.size - 1)

                    val strokePath = Path()
                    val fillPath = Path()

                    val points = data.mapIndexed { idx, value ->
                        val x = idx * stepX
                        val clampedVal = value.coerceIn(0f, 100f)
                        val normalizedY = 1f - (clampedVal / 100f)
                        val y = chartHeight * normalizedY
                        val animY = chartHeight - (chartHeight - y) * animProgress
                        Offset(x, animY)
                    }

                    strokePath.moveTo(points[0].x, points[0].y)
                    fillPath.moveTo(points[0].x, chartHeight)
                    fillPath.lineTo(points[0].x, points[0].y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx1 = p0.x + (p1.x - p0.x) / 2f
                        val cy1 = p0.y
                        val cx2 = cx1
                        val cy2 = p1.y

                        strokePath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                        fillPath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                    }

                    fillPath.lineTo(points.last().x, chartHeight)
                    fillPath.close()

                    // Gradient Area Fill
                    val areaBrush = Brush.verticalGradient(
                        colors = listOf(
                            series.primaryColor.copy(alpha = 0.35f * animProgress),
                            series.secondaryColor.copy(alpha = 0.05f * animProgress),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = chartHeight
                    )
                    drawPath(fillPath, brush = areaBrush)

                    // Glowing Main Line
                    val lineBrush = Brush.horizontalGradient(
                        colors = listOf(series.primaryColor, series.secondaryColor),
                        startX = 0f,
                        endX = w
                    )
                    drawPath(
                        strokePath,
                        brush = lineBrush,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Draw Scrubbing Touch Guide Line & Indicator Dot
        if (activeTouchX in 0f..w) {
            drawLine(
                color = PrimaryAccent.copy(alpha = 0.8f),
                start = Offset(activeTouchX, 0f),
                end = Offset(activeTouchX, chartHeight),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )

            seriesList.forEach { series: MetricSeries ->
                if (series.data.size >= 2) {
                    val stepX = w / (series.data.size - 1)
                    val index = (activeTouchX / stepX).toInt().coerceIn(0, series.data.size - 2)
                    val frac = ((activeTouchX - index * stepX) / stepX).coerceIn(0f, 1f)
                    val val0 = series.data[index]
                    val val1 = series.data[index + 1]
                    val currentVal = val0 + (val1 - val0) * frac
                    val y = chartHeight * (1f - currentVal / 100f)

                    drawCircle(
                        color = series.primaryColor.copy(alpha = 0.25f),
                        radius = 10.dp.toPx(),
                        center = Offset(activeTouchX, y)
                    )
                    drawCircle(
                        color = series.primaryColor,
                        radius = 4.5.dp.toPx(),
                        center = Offset(activeTouchX, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(activeTouchX, y)
                    )
                }
            }
        }
    }
}
