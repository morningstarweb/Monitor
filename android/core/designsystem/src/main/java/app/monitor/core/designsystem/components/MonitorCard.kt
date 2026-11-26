package app.monitor.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.theme.BorderStrokeGlow

private val OxygenGlassBg = Brush.verticalGradient(
    listOf(
        Color(0xFF161720).copy(alpha = 0.90f),
        Color(0xFF0E1016).copy(alpha = 0.95f)
    )
)

private val OxygenGlassBorder = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = 0.20f),
        Color(0xFF94A3B8).copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.08f)
    )
)

/**
 * Universal OxygenOS Glass Card Component.
 * Implements the dark acrylic glass look with subtle translucent white border.
 */
@Composable
fun MonitorCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    contentPadding: Dp = 16.dp,
    glowingBorder: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val shape = RoundedCornerShape(cornerRadius)
    val defaultBg = if (isDark) {
        OxygenGlassBg
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.94f), Color(0xFFF8FAFC).copy(alpha = 0.96f)))
    }
    val defaultBorder = if (isDark) {
        OxygenGlassBorder
    } else {
        Brush.verticalGradient(listOf(Color(0xFFCBD5E1).copy(alpha = 0.8f), Color(0xFFE2E8F0).copy(alpha = 0.65f)))
    }
    val borderStroke = when {
        glowingBorder -> BorderStroke(
            1.dp,
            Brush.verticalGradient(listOf(BorderStrokeGlow, if (isDark) Color(0xFF1B2E22) else Color(0xFFBBF7D0)))
        )
        borderColor != null -> BorderStroke(1.dp, borderColor)
        else -> BorderStroke(1.dp, defaultBorder)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (backgroundColor != null) {
                    Modifier.background(backgroundColor, shape)
                } else {
                    Modifier.background(defaultBg, shape)
                }
            )
            .border(borderStroke, shape)
    ) {
        // Specular top-glass sheen line
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.16f else 0.70f),
                            Color.Transparent
                        )
                    ),
                    shape = shape
                )
        )

        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
