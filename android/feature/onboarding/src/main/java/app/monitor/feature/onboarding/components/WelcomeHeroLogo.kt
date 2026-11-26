package app.monitor.feature.onboarding.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Monitor Hero Logo without movement/bobbing.
 * Features an electric animated glowing border around the squircle container.
 */
@Composable
fun WelcomeHeroLogo(modifier: Modifier = Modifier) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val transition = rememberInfiniteTransition(label = "BorderGlowMotion")

    // Continuous rotation angle for the squircle glowing border
    val borderPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderPhase"
    )

    val shape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier.size(116.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle static ambient glow behind container
        val haloColors = if (isDark) {
            listOf(Color(0xFF6366F1).copy(alpha = 0.28f), Color(0xFF00E5FF).copy(alpha = 0.15f), Color.Transparent)
        } else {
            listOf(Color(0xFF38BDF8).copy(alpha = 0.35f), Color(0xFF818CF8).copy(alpha = 0.20f), Color.Transparent)
        }
        Box(
            modifier = Modifier
                .size(130.dp)
                .background(brush = Brush.radialGradient(colors = haloColors), shape = CircleShape)
        )

        // Main Squircle Body
        val squircleBrush = if (isDark) {
            Brush.verticalGradient(listOf(Color(0xFF141726), Color(0xFF090A12)))
        } else {
            Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
        }
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(shape)
                .background(brush = squircleBrush),
            contentAlignment = Alignment.Center
        ) {
            // Animated Glowing Border Canvas
            val borderColors = if (isDark) {
                listOf(Color(0xFF00E5FF), Color(0xFF818CF8), Color(0xFFC084FC), Color(0x33FFFFFF), Color(0xFF00E5FF))
            } else {
                listOf(Color(0xFF0284C7), Color(0xFF6366F1), Color(0xFFA855F7), Color(0x40CBD5E1), Color(0xFF0284C7))
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p = borderPhase.toDouble()
                val w = size.width
                val h = size.height
                val radius = w * 0.7f

                val startX = w * 0.5f + radius * cos(p).toFloat()
                val startY = h * 0.5f + radius * sin(p).toFloat()
                val endX = w * 0.5f - radius * cos(p).toFloat()
                val endY = h * 0.5f - radius * sin(p).toFloat()

                // Animated glowing border stroke
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = borderColors,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY)
                    ),
                    cornerRadius = CornerRadius(32.dp.toPx(), 32.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Fixed Git DAG Brand Icon (Zero shake, completely stable)
            Canvas(modifier = Modifier.size(62.dp)) {
                val w = size.width
                val h = size.height
                val strokeW = 4.5.dp.toPx()
                val cyan = Color(0xFF00E5FF)
                val blue = Color(0xFF38BDF8)
                val violet = Color(0xFF818CF8)

                val root = Offset(w * 0.44f, h * 0.74f)
                val mainBranch = Offset(w * 0.44f, h * 0.26f)
                val forkBranch = Offset(w * 0.76f, h * 0.42f)

                // Main trunk
                drawLine(
                    brush = Brush.verticalGradient(listOf(cyan, blue)),
                    start = root,
                    end = mainBranch,
                    strokeWidth = strokeW
                )

                // Fork branch curve
                val path = Path().apply {
                    moveTo(root.x, h * 0.52f)
                    quadraticTo(root.x, forkBranch.y, forkBranch.x, forkBranch.y)
                }
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(listOf(cyan, violet)),
                    style = Stroke(width = strokeW)
                )

                val nodeR = 6.dp.toPx()
                drawCircle(color = blue, radius = nodeR, center = mainBranch)
                drawCircle(color = violet, radius = nodeR, center = forkBranch)

                // Root hollow node
                drawCircle(color = if (isDark) Color(0xFF090A12) else Color.White, radius = nodeR + 2.5f, center = root)
                drawCircle(
                    color = cyan,
                    radius = nodeR + 2.5f,
                    center = root,
                    style = Stroke(width = strokeW)
                )
            }
        }
    }
}
