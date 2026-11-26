package app.monitor.feature.gitworkspace.sections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class GitSegment(val label: String, val icon: ImageVector) {
    Graph("Graph", Icons.Default.Timeline),
    Branches("Branches", Icons.AutoMirrored.Filled.CallMerge),
    PullRequests("PRs", Icons.Default.Commit),
    Sync("Remote", Icons.Default.CloudSync)
}

/**
 * Clean Minimalist Segmented Pill Row for Git Workspace.
 * Eliminates heavy borders in favor of sleek, soft pills.
 */
@Composable
fun GitWorkspaceSegmentRow(
    selectedSegment: GitSegment,
    onSelectSegment: (GitSegment) -> Unit,
    modifier: Modifier = Modifier,
    prCount: Int = 0
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GitSegment.entries.forEach { segment ->
            val isSelected = segment == selectedSegment
            val bgColor by animateColorAsState(
                if (isSelected) (if (isDark) Color(0x288B5CF6) else Color(0x208B5CF6)) else (if (isDark) Color(0x10FFFFFF) else Color(0x12000000)),
                tween(200),
                label = "segBg"
            )
            val contentColor by animateColorAsState(
                if (isSelected) (if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                tween(200),
                label = "segText"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onSelectSegment(segment) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(segment.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
                    Text(
                        text = segment.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor
                    )
                    if (segment == GitSegment.PullRequests && prCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color(0xFF7C3AED) else Color(0x30818CF8))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("$prCount", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
