package app.monitor.feature.gitworkspace.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.feature.gitworkspace.components.LivingGitBranchNode

/**
 * Clean Spacious Header for the Changed Files section with Collapse/Fold Toggle.
 * Follows the unified Obsidian/Crystal glass design system.
 */
@Composable
fun ChangedFilesSectionHeader(
    filesCount: Int,
    isAllSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    isFolded: Boolean = false,
    onToggleFold: (() -> Unit)? = null
) {
    val isDark = LocalMonitorColors.current.isDark

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF1E1738) else Color(0xFFEDE9FE))
                    .border(1.dp, if (isDark) Color(0x60C084FC) else Color(0x40A855F7), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                LivingGitBranchNode(tint = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED), size = 16.dp)
            }

            Text(
                text = "Changed Files",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF161B33) else Color(0xFFE0F2FE))
                    .border(1.dp, if (isDark) Color(0x35818CF8) else Color(0x300284C7), RoundedCornerShape(8.dp))
                    .clickable(enabled = onToggleFold != null, onClick = { onToggleFold?.invoke() })
                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "$filesCount",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                    )
                    if (onToggleFold != null) {
                        Icon(
                            imageVector = if (isFolded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isFolded) "Expand" else "Collapse",
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleSelectAll)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (isAllSelected) Color(0xFF7C3AED)
                        else if (isDark) Color(0xFF131728) else Color(0xFFF1F5F9)
                    )
                    .border(
                        1.dp,
                        if (isAllSelected) Color(0xFFC084FC)
                        else if (isDark) Color(0x40818CF8) else Color(0x40CBD5E1),
                        RoundedCornerShape(5.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isAllSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }

            Text(
                text = "Select All",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isAllSelected) {
                    if (isDark) Color.White else Color(0xFF7C3AED)
                } else {
                    if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                }
            )
        }
    }
}
