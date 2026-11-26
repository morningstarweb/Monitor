package app.monitor.feature.onboarding.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.monitor.core.designsystem.components.MonitorGlobalBackgroundCanvas

/**
 * Clean delegate to the unified [MonitorGlobalBackgroundCanvas].
 * Guarantees identical animated wave ribbons, radial glows, and luminous crests
 * across all onboarding and dashboard screens.
 */
@Composable
fun WelcomeBackgroundAura(modifier: Modifier = Modifier) {
    MonitorGlobalBackgroundCanvas(modifier = modifier)
}
