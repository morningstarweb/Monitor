package app.monitor.feature.dashboard.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.components.LogRowItem
import app.monitor.feature.dashboard.model.RealAuditLog

/**
 * Pure Minimalist, Unboxed System Logs List Card.
 * Replaces heavy bordered card container with an open, fluid log stream.
 */
@Composable
fun LogsListCard(
    logs: List<RealAuditLog>,
    modifier: Modifier = Modifier
) {
    if (logs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No audit logs available for current filter.\nConnected to desktop telemetry broker.",
                fontSize = 12.5.sp,
                color = Color(0xFF64748B)
            )
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            logs.forEachIndexed { index, log ->
                LogRowItem(
                    log = log,
                    isLatest = index == 0
                )

                // Hairline Divider
                if (index < logs.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0x0CFFFFFF))
                    )
                }
            }
        }
    }
}
