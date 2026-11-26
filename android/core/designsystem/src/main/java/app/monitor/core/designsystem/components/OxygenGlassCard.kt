package app.monitor.core.designsystem.components

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

/**
 * OxygenOS & Apple Dark Glass Inspired Card Surface.
 * Features an ultra-dark frosted acrylic glass body with an elegant,
 * razor-thin translucent white/silver border accent inspired by OxygenOS.
 */
@Composable
fun OxygenGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val shape = RoundedCornerShape(cornerRadius)
    val bgBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF161720).copy(alpha = 0.90f), Color(0xFF0E1016).copy(alpha = 0.95f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.94f), Color(0xFFF8FAFC).copy(alpha = 0.96f)))
    }
    val borderBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color(0xFF94A3B8).copy(alpha = 0.12f), Color.White.copy(alpha = 0.08f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFCBD5E1).copy(alpha = 0.85f), Color(0xFFE2E8F0).copy(alpha = 0.7f)))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = bgBrush)
            .border(border = BorderStroke(width = borderWidth, brush = borderBrush), shape = shape)
    ) {
        // Specular top-glass sheen line for authentic depth
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.16f else 0.75f),
                            Color.Transparent
                        )
                    ),
                    shape = shape
                )
        )

        content()
    }
}
