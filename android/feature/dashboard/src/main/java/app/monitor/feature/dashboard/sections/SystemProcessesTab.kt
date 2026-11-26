package app.monitor.feature.dashboard.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.core.model.ProcessSnapshot
import app.monitor.feature.dashboard.components.ProcessRowItem

enum class InstanceSortOrder {
    CPU, Memory
}

/**
 * Pure Minimalist, Unboxed Instances & Process Stream (< 140 lines).
 */
@Composable
fun ProcessesListCard(
    processes: List<ProcessSnapshot>,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    var sortOrder by remember { mutableStateOf(InstanceSortOrder.CPU) }

    val sortedProcesses = remember(processes, sortOrder) {
        when (sortOrder) {
            InstanceSortOrder.CPU -> processes.sortedByDescending { it.cpuPercent }
            InstanceSortOrder.Memory -> processes.sortedByDescending { it.memoryBytes }
        }
    }

    val filteredProcesses = remember(sortedProcesses, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedProcesses
        } else {
            sortedProcesses.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.pid.toString().contains(searchQuery)
            }
        }
    }

    val maxCpu = remember(processes) {
        processes.maxOfOrNull { it.cpuPercent }?.toDouble() ?: 100.0
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ACTIVE INSTANCES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B),
                    letterSpacing = 0.8.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0x1800E5FF) else Color(0x150284C7))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${filteredProcesses.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                    )
                }
            }

            ProcessSortControls(
                sortOrder = sortOrder,
                onSortOrderChange = { sortOrder = it },
                isSearchActive = isSearchActive,
                onToggleSearch = onToggleSearch,
                isDark = isDark
            )
        }

        if (isSearchActive) {
            ProcessSearchField(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                isDark = isDark
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (filteredProcesses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active instances reported",
                        fontSize = 12.sp,
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                    )
                }
            } else {
                filteredProcesses.take(50).forEach { proc ->
                    ProcessRowItem(
                        process = proc,
                        maxCpuInList = maxCpu
                    )
                }
            }
        }
    }
}
