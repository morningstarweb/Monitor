package app.monitor.feature.dashboard.settings.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TelemetryPulseBeacon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    isPulsing: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "pulseBeacon")
    val scale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        if (isPulsing) {
            Canvas(modifier = Modifier.size(size * scale)) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = this.size.minDimension / 2f
                )
            }
        }
        Canvas(modifier = Modifier.size(size)) {
            drawCircle(
                color = color,
                radius = this.size.minDimension / 2f
            )
        }
    }
}
