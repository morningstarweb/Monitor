package app.monitor.feature.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.dashboard.model.MetricIconType

/**
 * High-Tech Living Telemetry Metric Card (CPU & RAM) (< 180 lines).
 */
@Composable
fun SystemMetricCard(
    modifier: Modifier = Modifier,
    iconType: MetricIconType,
    name: String,
    value: String,
    subtitle: String,
    sparklineData: List<Float>,
    sparklineColor: Color,
    currentPct: Float,
    perCoreUsage: List<Float> = emptyList()
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val cycleDuration = when {
        currentPct > 60f -> 650
        currentPct > 25f -> 1200
        else -> 2200
    }

    val infiniteTransition = rememberInfiniteTransition(label = "MetricCoreOscillator")
    val waveOscillation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(cycleDuration, easing = LinearEasing), RepeatMode.Restart),
        label = "waveOscillation"
    )
    val dotPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.30f,
        animationSpec = infiniteRepeatable(tween(cycleDuration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotPulse"
    )

    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.88f))
            .border(
                1.dp,
                if (isDark) Brush.verticalGradient(listOf(sparklineColor.copy(alpha = 0.45f), Color(0x20818CF8), Color(0x1000E5FF)))
                else Brush.verticalGradient(listOf(Color(0x25000000), sparklineColor.copy(alpha = 0.40f))),
                cardShape
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Living Icon + Name + Live Pulsing Load Beacon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(sparklineColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                            .border(1.dp, sparklineColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconType == MetricIconType.CPU) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = sparklineColor, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = sparklineColor, modifier = Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier.size(7.dp).scale(dotPulse).background(sparklineColor, CircleShape)
                )
            }

            // Big Metric Telemetry Value
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value.removeSuffix("%"),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
                if (value.endsWith("%")) {
                    Text(
                        text = "%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = sparklineColor,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            // Subtitle
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            // Spectrogram or RAM bar
            if (iconType == MetricIconType.CPU) {
                LivingCpuMultiCoreSpectrogramCanvas(
                    perCoreUsage = perCoreUsage,
                    totalUsage = currentPct,
                    wavePhase = waveOscillation,
                    accentColor = sparklineColor,
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                )
            } else {
                LivingRamArchitectureCanvas(
                    usagePercent = currentPct,
                    wavePhase = waveOscillation,
                    accentColor = sparklineColor,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    isDark = isDark
                )
            }
        }
    }
}
