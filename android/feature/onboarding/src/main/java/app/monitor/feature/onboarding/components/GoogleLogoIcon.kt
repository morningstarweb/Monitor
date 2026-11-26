package app.monitor.feature.onboarding.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale

/**
 * Pure Compose Google 'G' Vector Logo with exact 4-color branding (Official Google Specification)
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f

        scale(scaleX, scaleY, pivot = Offset.Zero) {
            // 1. Blue Segment (Horizontal crossbar & lower-right arc)
            val bluePath = Path().apply {
                moveTo(23.49f, 12.28f)
                cubicTo(23.49f, 11.43f, 23.42f, 10.61f, 23.28f, 9.82f)
                lineTo(12.0f, 9.82f)
                lineTo(12.0f, 14.47f)
                lineTo(18.45f, 14.47f)
                cubicTo(18.17f, 15.95f, 17.34f, 17.20f, 16.09f, 18.05f)
                lineTo(16.09f, 21.02f)
                lineTo(19.91f, 21.02f)
                cubicTo(22.14f, 18.96f, 23.49f, 15.93f, 23.49f, 12.28f)
                close()
            }
            drawPath(path = bluePath, color = Color(0xFF4285F4))

            // 2. Green Segment (Bottom arc)
            val greenPath = Path().apply {
                moveTo(12.0f, 24.0f)
                cubicTo(15.24f, 24.0f, 17.96f, 22.92f, 19.95f, 21.09f)
                lineTo(16.13f, 18.12f)
                cubicTo(15.05f, 18.84f, 13.68f, 19.28f, 12.0f, 19.28f)
                cubicTo(8.82f, 19.28f, 6.13f, 17.13f, 5.17f, 14.24f)
                lineTo(1.04f, 14.24f)
                lineTo(1.04f, 17.31f)
                cubicTo(3.05f, 21.30f, 7.21f, 24.0f, 12.0f, 24.0f)
                close()
            }
            drawPath(path = greenPath, color = Color(0xFF34A853))

            // 3. Yellow Segment (Left arc)
            val yellowPath = Path().apply {
                moveTo(5.17f, 14.24f)
                cubicTo(4.92f, 13.52f, 4.79f, 12.75f, 4.79f, 12.0f)
                cubicTo(4.79f, 11.25f, 4.92f, 10.48f, 5.17f, 9.76f)
                lineTo(5.17f, 6.69f)
                lineTo(1.04f, 6.69f)
                cubicTo(0.38f, 8.01f, 0.0f, 9.96f, 0.0f, 12.0f)
                cubicTo(0.0f, 14.04f, 0.38f, 15.99f, 1.04f, 17.31f)
                lineTo(5.17f, 14.24f)
                close()
            }
            drawPath(path = yellowPath, color = Color(0xFFFBBC05))

            // 4. Red Segment (Top arc with open right gap)
            val redPath = Path().apply {
                moveTo(12.0f, 4.75f)
                cubicTo(13.77f, 4.75f, 15.35f, 5.36f, 16.60f, 6.55f)
                lineTo(20.02f, 3.13f)
                cubicTo(17.95f, 1.19f, 15.24f, 0.0f, 12.0f, 0.0f)
                cubicTo(7.21f, 0.0f, 3.05f, 2.70f, 1.04f, 6.69f)
                lineTo(5.17f, 9.76f)
                cubicTo(6.13f, 6.87f, 8.82f, 4.75f, 12.0f, 4.75f)
                close()
            }
            drawPath(path = redPath, color = Color(0xFFEA4335))
        }
    }
}
