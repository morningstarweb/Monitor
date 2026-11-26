package app.monitor.feature.gitworkspace.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.monitor.core.designsystem.components.MonitorGlobalBackgroundCanvas

/**
 * Ambient background decorations for Workspace (< 20 lines).
 * Delegates to the unified global animated background canvas.
 */
@Composable
fun WorkspaceBackgroundDecorations(
    modifier: Modifier = Modifier
) {
    MonitorGlobalBackgroundCanvas(modifier = modifier)
}
