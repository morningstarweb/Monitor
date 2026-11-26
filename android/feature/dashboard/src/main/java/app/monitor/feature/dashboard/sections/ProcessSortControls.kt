package app.monitor.feature.dashboard.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
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

@Composable
fun ProcessSortControls(
    sortOrder: InstanceSortOrder,
    onSortOrderChange: (InstanceSortOrder) -> Unit,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Sort by CPU pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (sortOrder == InstanceSortOrder.CPU) (if (isDark) Color(0x30818CF8) else Color(0xFFEDE9FE))
                    else (if (isDark) Color(0x10FFFFFF) else Color(0xFFF1F5F9))
                )
                .clickable { onSortOrderChange(InstanceSortOrder.CPU) }
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = if (sortOrder == InstanceSortOrder.CPU) (if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "CPU",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sortOrder == InstanceSortOrder.CPU) (if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                )
            }
        }

        // Sort by RAM pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (sortOrder == InstanceSortOrder.Memory) (if (isDark) Color(0x30818CF8) else Color(0xFFEDE9FE))
                    else (if (isDark) Color(0x10FFFFFF) else Color(0xFFF1F5F9))
                )
                .clickable { onSortOrderChange(InstanceSortOrder.Memory) }
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (sortOrder == InstanceSortOrder.Memory) (if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "RAM",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sortOrder == InstanceSortOrder.Memory) (if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                )
            }
        }

        // Search Icon
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isSearchActive) (if (isDark) Color(0x3000E5FF) else Color(0x250284C7))
                    else (if (isDark) Color(0x10FFFFFF) else Color(0xFFF1F5F9))
                )
                .clickable(onClick = onToggleSearch),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = if (isSearchActive) (if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7))
                       else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
