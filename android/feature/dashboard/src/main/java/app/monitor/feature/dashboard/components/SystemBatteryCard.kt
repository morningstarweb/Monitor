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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.ChargingState
import app.monitor.core.model.SystemSnapshot
import java.util.Locale

private val NeonEmerald = Color(0xFF00E699)
private val ElectricCyan = Color(0xFF00E5FF)

/**
 * Living Holographic Quantum Energy Core & Battery Telemetry (< 170 lines).
 */
@Composable
fun SystemBatteryCard(
    snapshot: SystemSnapshot?,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val hasBattery = snapshot?.battery?.hasBattery == true
    val battPct = snapshot?.battery?.percentage?.toInt() ?: 0
    val isCharging = snapshot?.battery?.chargingState == ChargingState.Charging
    val estMinutes = snapshot?.battery?.estimatedRemainingMinutes
    val powerWatts = snapshot?.battery?.powerDrawWatts
    val healthPct = snapshot?.battery?.healthPercentage?.toInt()

    val infiniteTransition = rememberInfiniteTransition(label = "ReactorCoreMotion")
    val plasmaPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "plasmaPhase"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.70f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse"
    )

    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.88f))
            .border(
                1.dp,
                if (isDark) Brush.horizontalGradient(listOf(Color(0x5000E699), Color(0x3000E5FF), Color(0x20818CF8)))
                else Brush.horizontalGradient(listOf(Color(0x25000000), Color(0x3000E699), Color(0x2000E5FF))),
                cardShape
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top Row: Category Pill + Health Telemetry + Big Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0x2000E699) else Color(0xFFE6FBF2))
                            .border(1.dp, if (isDark) Color(0x4000E699) else Color(0x6000E699), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (hasBattery) "POWER CORE" else "MAINS GRID",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) NeonEmerald else Color(0xFF059669)
                        )
                    }

                    if (hasBattery && healthPct != null) {
                        Text(
                            text = "$healthPct% Health",
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }

                if (hasBattery) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "$battPct",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDark) NeonEmerald else Color(0xFF059669)
                        )
                        Text(
                            text = "%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) NeonEmerald.copy(alpha = 0.75f) else Color(0xFF059669).copy(alpha = 0.75f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0x2500E5FF) else Color(0xFFE0F2FE))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "AC WORKSTATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) ElectricCyan else Color(0xFF0284C7))
                    }
                }
            }

            // Middle Row: Living Animated Energy Reactor Chamber Canvas
            LivingPlasmaReactorCanvas(
                batteryPercent = if (snapshot == null) 0 else if (hasBattery) battPct else 100,
                isCharging = isCharging,
                isAcMains = !hasBattery,
                plasmaPhase = plasmaPhase,
                glowPulse = glowPulse,
                modifier = Modifier.fillMaxWidth().height(38.dp),
                isDark = isDark
            )

            // Bottom Status Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val stateText = when {
                    !isOnline && snapshot == null -> "Waiting for desktop telemetry..."
                    hasBattery && isCharging -> {
                        val rem = if (estMinutes != null && estMinutes > 0) " · ~${estMinutes}m to 100%" else ""
                        val wattText = if (powerWatts != null && powerWatts > 0f) String.format(Locale.US, " (+%.1fW)", powerWatts) else ""
                        "Charging Active (Fast-Charge)$wattText$rem"
                    }
                    hasBattery -> {
                        val rem = if (estMinutes != null && estMinutes > 0) " · ~${estMinutes}m remaining" else ""
                        "Discharging on battery$rem"
                    }
                    else -> "Mains Powered · Desktop Workstation (Unlimited Capacity)"
                }

                Text(
                    text = stateText,
                    fontSize = 12.sp,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                )

                // Living Pulse Halo Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isCharging) ElectricCyan else NeonEmerald, CircleShape)
                )
            }
        }
    }
}
