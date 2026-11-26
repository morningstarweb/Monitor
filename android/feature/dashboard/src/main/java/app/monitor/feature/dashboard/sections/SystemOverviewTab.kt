package app.monitor.feature.dashboard.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.RepoSummary
import app.monitor.core.model.SystemSnapshot
import app.monitor.feature.dashboard.components.LiveFanIndicatorCard
import app.monitor.feature.dashboard.components.SystemBatteryCard
import app.monitor.feature.dashboard.components.SystemDiskCard
import app.monitor.feature.dashboard.components.SystemMetricCard
import app.monitor.feature.dashboard.model.MetricIconType
import java.util.Locale

private val NeonEmerald = Color(0xFF00E699)
private val ElectricCyan = Color(0xFF00E5FF)
private val CyberViolet = Color(0xFFC084FC)

/**
 * Pure Minimalist, System-Synchronized Telemetry Overview Tab.
 * Features living quantum battery reactor, multi-core spectrogram equalizer,
 * segmented memory architecture canvas, and distinct mounted storage matrix.
 */
@Composable
fun SystemOverviewTab(
    snapshot: SystemSnapshot?,
    isOnline: Boolean,
    projects: List<RepoSummary>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Living Quantum Energy Core & Battery Telemetry
        SystemBatteryCard(snapshot = snapshot, isOnline = isOnline)

        // 2. CPU & RAM Multi-Core Telemetry Cards Grid
        val cpuPct = snapshot?.cpu?.totalUsagePercent?.toInt() ?: 0
        val ghz = snapshot?.let { String.format(Locale.US, "%.1f", it.cpu.frequencyMhz / 1000f) } ?: "—"
        val cores = snapshot?.cpu?.perCoreUsagePercent?.size ?: 0
        val perCoreUsage = snapshot?.cpu?.perCoreUsagePercent.orEmpty()
        val cpuHistory = snapshot?.cpu?.history60mPercent.orEmpty()

        val ramPct = snapshot?.ram?.usagePercent?.toInt() ?: 0
        val usedRamGb = snapshot?.let { String.format(Locale.US, "%.1f", it.ram.usedBytes.toDouble() / (1024 * 1024 * 1024)) } ?: "—"
        val totalRamGb = snapshot?.let { String.format(Locale.US, "%.0f", it.ram.totalBytes.toDouble() / (1024 * 1024 * 1024)) } ?: "—"
        val ramHistory = snapshot?.ram?.history60mPercent.orEmpty()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // CPU Card with Real Multi-Core Equalizer Spectrogram
            SystemMetricCard(
                modifier = Modifier.weight(1f),
                iconType = MetricIconType.CPU,
                name = "CPU Core",
                value = if (snapshot != null) "$cpuPct%" else "—",
                subtitle = if (snapshot != null) "$ghz GHz · $cores cores" else "Waiting for CPU...",
                sparklineData = cpuHistory,
                sparklineColor = NeonEmerald,
                currentPct = cpuPct.toFloat(),
                perCoreUsage = perCoreUsage
            )

            // RAM Card with Real Memory Architecture Bandwidth
            SystemMetricCard(
                modifier = Modifier.weight(1f),
                iconType = MetricIconType.RAM,
                name = "RAM Memory",
                value = if (snapshot != null) "$ramPct%" else "—",
                subtitle = if (snapshot != null) "$usedRamGb / $totalRamGb GB" else "Waiting for RAM...",
                sparklineData = ramHistory,
                sparklineColor = ElectricCyan,
                currentPct = ramPct.toFloat()
            )
        }

        // 3. Optional Living Cooling Telemetry (only if physical cooling hardware is reported by desktop)
        val cooling = snapshot?.cooling
        if (cooling != null && cooling.hasFan && !cooling.isFanless) {
            LiveFanIndicatorCard(cooling = cooling)
        }

        // 4. Storage Disks Grid bound directly to physical telemetry
        val disks = snapshot?.disks.orEmpty()
        if (disks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                disks.take(2).forEachIndexed { idx, disk ->
                    val color = if (idx == 0) ElectricCyan else CyberViolet
                    SystemDiskCard(
                        disk = disk,
                        barColor = color,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C0E1A).copy(alpha = 0.94f))
                    .border(1.dp, Color(0x30818CF8), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No physical disks reported by desktop agent",
                    fontSize = 12.5.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
