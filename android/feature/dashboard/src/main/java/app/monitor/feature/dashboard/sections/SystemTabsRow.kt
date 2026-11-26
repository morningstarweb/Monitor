package app.monitor.feature.dashboard.sections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
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

data class SystemTabItem(
    val label: String,
    val icon: ImageVector,
    val countBadge: String? = null
)

/**
 * Pure Minimalist Segmented Tabs for System Screen (< 90 lines).
 * Seamlessly matches the style of Home, Projects, and Git.
 */
@Composable
fun SystemTabsRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    processCount: Int = 0,
    logCount: Int = 0,
    isLive: Boolean = true
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val tabs = listOf(
        SystemTabItem("Overview", Icons.Default.DeveloperBoard),
        SystemTabItem("Processes", Icons.Default.Memory, if (processCount > 0) "$processCount" else null),
        SystemTabItem("Logs", Icons.AutoMirrored.Filled.ListAlt, if (isLive) "Live" else null)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = selectedTab == index
            val bgColor by animateColorAsState(
                if (isSelected) (if (isDark) Color(0x288B5CF6) else Color(0x208B5CF6)) else (if (isDark) Color(0x10FFFFFF) else Color(0x12000000)),
                tween(200),
                label = "tabBg"
            )
            val contentColor by animateColorAsState(
                if (isSelected) (if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                tween(200),
                label = "tabText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(13.dp)
                    )

                    Text(
                        text = tab.label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (tab.countBadge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (tab.countBadge == "Live") Color(0x3500E699)
                                    else if (isSelected) Color(0xFF7C3AED) else Color(0x28818CF8)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.countBadge,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tab.countBadge == "Live") (if (isDark) Color(0xFF00E699) else Color(0xFF059669)) else Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
