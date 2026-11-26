package app.monitor.feature.dashboard.settings.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ObsidianGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF13172E).copy(alpha = 0.90f),
        Color(0xFF090C19).copy(alpha = 0.95f)
    )
)

private val FrostedBorderGradient = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = 0.22f),
        Color(0xFF818CF8).copy(alpha = 0.15f),
        Color.White.copy(alpha = 0.08f)
    )
)

private val SpecularSheenGradient = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = 0.18f),
        Color.Transparent
    )
)

@Composable
fun ObsidianGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val bg = if (isDark) ObsidianGradient else Brush.verticalGradient(listOf(Color(0xFFFFFFFF).copy(alpha = 0.94f), Color(0xFFF8FAFC).copy(alpha = 0.96f)))
    val border = if (isDark) FrostedBorderGradient else Brush.verticalGradient(listOf(Color(0x30000000), Color(0x15818CF8), Color(0x10000000)))

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(BorderStroke(borderWidth, border), shape)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        // Specular top-glass reflection sheen line
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.5.dp,
                    brush = SpecularSheenGradient,
                    shape = shape
                )
        )

        content()
    }
}
