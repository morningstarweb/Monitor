package app.monitor.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkGlassColorScheme = darkColorScheme(
    primary = NeonEmeraldRaw,
    secondary = InfoBlue,
    tertiary = NeonEmeraldRaw,
    background = Color(0xFF060A08),
    surface = Color(0xFF0D1611),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF9FAFB),
    onSurface = Color(0xFFF9FAFB),
    outline = Color(0xFF1B2E22),
    surfaceVariant = Color(0xFF132219),
    onSurfaceVariant = Color(0xFFA1B5A8),
    surfaceContainer = Color(0xFF0D1611),
    surfaceContainerHigh = Color(0xFF132219),
    error = ErrorRed,
    onError = Color.White
)

private val LightGlassColorScheme = lightColorScheme(
    primary = EmeraldLightRaw,
    secondary = InfoBlue,
    tertiary = EmeraldLightRaw,
    background = Color(0xFFF4F6F5),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F1713),
    onSurface = Color(0xFF0F1713),
    outline = Color(0xFFD6DDD8),
    surfaceVariant = Color(0xFFEAEFEB),
    onSurfaceVariant = Color(0xFF47554E),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAEFEB),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MonitorTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val monitorColors = if (darkTheme) DarkMonitorColors else LightMonitorColors
    val colorScheme = if (darkTheme) DarkGlassColorScheme else LightGlassColorScheme

    CompositionLocalProvider(
        LocalMonitorColors provides monitorColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MonitorTypography,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(32.dp)
            ),
            content = content
        )
    }
}
