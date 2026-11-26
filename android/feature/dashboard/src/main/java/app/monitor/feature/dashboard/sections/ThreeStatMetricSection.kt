package app.monitor.feature.dashboard.sections

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.SystemSnapshot
import app.monitor.core.designsystem.components.CircularProgressRing
import app.monitor.feature.dashboard.components.LivingBatteryCell
import app.monitor.feature.dashboard.components.LivingCpuProcessor
import app.monitor.feature.dashboard.components.LivingMemoryRam

/**
 * Clean Minimalist 3-Stat Gauge Row (< 140 lines).
 * Zero crammed or truncated text: living icon, circular arc, bold percentage, and clean label.
 */
@Composable
fun ThreeStatMetricRow(
    snapshot: SystemSnapshot?,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "GaugePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gaugePulse"
    )

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val battPct = snapshot?.battery?.takeIf { it.hasBattery }?.percentage?.toInt()
        val cpuPct = snapshot?.cpu?.totalUsagePercent?.toInt()
        val memPct = snapshot?.ram?.usagePercent?.toInt()

        val battText = if (snapshot == null) "--%" else if (snapshot.battery.hasBattery) "${battPct ?: 0}%" else "N/A"
        val battProgress = battPct?.let { (it / 100f).coerceIn(0f, 1f) } ?: 0f
        val cpuText = cpuPct?.let { "$it%" } ?: "--%"
        val cpuProgress = cpuPct?.let { (it / 100f).coerceIn(0f, 1f) } ?: 0f
        val memText = memPct?.let { "$it%" } ?: "--%"
        val memProgress = memPct?.let { (it / 100f).coerceIn(0f, 1f) } ?: 0f

        val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark

        // Clean Battery Gauge Card
        CleanGaugeCard(
            modifier = Modifier.weight(1f),
            iconContent = { LivingBatteryCell(modifier = Modifier.scale(pulse)) },
            iconBg = if (isDark) Color(0xFF064E3B).copy(alpha = 0.50f) else Color(0xFFD1FAE5),
            progress = battProgress,
            progressColor = Color(0xFF10B981),
            value = battText,
            label = "Battery"
        )

        // Clean CPU Gauge Card
        CleanGaugeCard(
            modifier = Modifier.weight(1f),
            iconContent = { LivingCpuProcessor(modifier = Modifier.scale(pulse)) },
            iconBg = if (isDark) Color(0xFF4C1D95).copy(alpha = 0.50f) else Color(0xFFEDE9FE),
            progress = cpuProgress,
            progressColor = Color(0xFFC084FC),
            value = cpuText,
            label = "CPU"
        )

        // Clean RAM Gauge Card
        CleanGaugeCard(
            modifier = Modifier.weight(1f),
            iconContent = { LivingMemoryRam(modifier = Modifier.scale(pulse)) },
            iconBg = if (isDark) Color(0xFF0C4A6E).copy(alpha = 0.50f) else Color(0xFFE0F2FE),
            progress = memProgress,
            progressColor = Color(0xFF00E5FF),
            value = memText,
            label = "RAM"
        )
    }
}

@Composable
fun CleanGaugeCard(
    modifier: Modifier = Modifier,
    iconContent: @Composable () -> Unit,
    iconBg: Color,
    progress: Float,
    progressColor: Color,
    value: String,
    label: String
) {
    val cardShape = RoundedCornerShape(22.dp)
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color(0xFFFFFFFF).copy(alpha = 0.92f)
    val cardBorder = if (isDark) Brush.verticalGradient(listOf(Color.White.copy(0.20f), Color(0xFF818CF8).copy(0.15f), Color.Transparent))
    else Brush.verticalGradient(listOf(Color(0x30000000), Color(0x15818CF8), Color.Transparent))

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(cardBg)
            .border(1.2.dp, cardBorder, cardShape)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(iconBg), contentAlignment = Alignment.Center) { iconContent() }
                CircularProgressRing(progress = progress, color = progressColor, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MetricStatCard(
    modifier: Modifier = Modifier, iconContent: @Composable () -> Unit, iconBgColor: Color,
    progress: Float, progressColor: Color, subtext: String? = null, value: String, label: String
) {
    CleanGaugeCard(modifier, iconContent, iconBgColor, progress, progressColor, value, label)
}
