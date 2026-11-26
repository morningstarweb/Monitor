package app.monitor.feature.dashboard.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.monitor.core.designsystem.components.MonitorGlobalBackgroundCanvas

/**
 * Signature background decorations for Dashboard and Home screen (< 20 lines).
 * Delegates to the unified global animated background canvas.
 */
@Composable
fun DashboardBackgroundDecorations(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    MonitorGlobalBackgroundCanvas(modifier = modifier, isDarkTheme = isDarkTheme)
}
