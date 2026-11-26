package app.monitor.feature.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.CoolingSnapshot

private val NeonCyan = Color(0xFF00E5FF)
private val NeonEmerald = Color(0xFF00FF87)

@Composable
fun LiveFanIndicatorCard(
    cooling: CoolingSnapshot?,
    modifier: Modifier = Modifier
) {
    if (cooling == null || !cooling.hasFan || cooling.isFanless) return

    val isDual = cooling.isDualFan || cooling.gpuFanRpm > 0
    val cpuRpm = if (cooling.cpuFanRpm > 0) cooling.cpuFanRpm else cooling.fanRpm
    val gpuRpm = if (cooling.gpuFanRpm > 0) cooling.gpuFanRpm else (cpuRpm * 0.96f).toInt()

    val cpuDuration = rpmToDuration(cpuRpm)
    val gpuDuration = rpmToDuration(gpuRpm)

    val transition = rememberInfiniteTransition(label = "fanSpinTransition")
    val cpuAngle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(cpuDuration, easing = LinearEasing), RepeatMode.Restart), label = "cpuFanAngle")
    val gpuAngle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(gpuDuration, easing = LinearEasing), RepeatMode.Restart), label = "gpuFanAngle")

    val animatedSpeedPct by animateFloatAsState(
        targetValue = cooling.fanSpeedPercent.coerceIn(0f, 100f),
        animationSpec = tween(600),
        label = "fanPct"
    )

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val fanBg = if (isDark) Brush.verticalGradient(listOf(Color(0xFF0F1424), Color(0xFF090C16)))
    else Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC)))
    val fanBorder = if (isDark) Brush.horizontalGradient(listOf(Color(0x3538BDF8), Color(0x15818CF8)))
    else Brush.horizontalGradient(listOf(Color(0x300284C7), Color(0x206366F1)))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(fanBg)
            .border(1.dp, fanBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Row: Badge & Profile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(NeonCyan, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDual) "THERMAL DYNAMICS • DUAL ZONE" else "THERMAL DYNAMICS",
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(if (isDark) Color(0x2238BDF8) else Color(0x150284C7), RoundedCornerShape(8.dp))
                        .border(1.dp, if (isDark) Color(0x4038BDF8) else Color(0x300284C7), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(text = cooling.fanProfile.uppercase(), color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Middle Row: Dual Fan Turbines or Single Turbine
            if (isDual) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // CPU Fan Unit
                    SingleFanUnit(
                        label = "CPU FAN",
                        labelColor = NeonCyan,
                        rpm = cpuRpm,
                        rotationAngle = if (cpuRpm > 0) cpuAngle else 0f
                    )

                    Box(modifier = Modifier.width(1.dp).height(46.dp).background(if (isDark) Color(0x22FFFFFF) else Color(0x20000000)))

                    // GPU Fan Unit
                    SingleFanUnit(
                        label = "GPU FAN",
                        labelColor = NeonEmerald,
                        rpm = gpuRpm,
                        rotationAngle = if (gpuRpm > 0) gpuAngle else 0f
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FanImpellerCanvas(rotationAngle = if (cpuRpm > 0) cpuAngle else 0f, modifier = Modifier.size(52.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(text = "%,d".format(cpuRpm), color = if (isDark) Color.White else Color(0xFF0F172A), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "RPM", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                            }
                            Text(text = if (cpuRpm > 0) "Active Cooling" else "Passive Cooling", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }
                    Text(text = "${animatedSpeedPct.toInt()}%", color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Bottom Progress Bar: Fan Load Capacity
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedSpeedPct / 100f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(NeonCyan, NeonEmerald)))
                )
            }
        }
    }
}

@Composable
private fun SingleFanUnit(label: String, labelColor: Color, rpm: Int, rotationAngle: Float) {
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FanImpellerCanvas(rotationAngle = rotationAngle, modifier = Modifier.size(46.dp))
        Column {
            Text(text = label, color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "%,d".format(rpm), color = if (isDark) Color.White else Color(0xFF0F172A), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = "RPM", color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 1.dp))
            }
        }
    }
}

private fun rpmToDuration(rpm: Int): Int = when {
    rpm > 4500 -> 180; rpm > 3500 -> 260; rpm > 2500 -> 380; rpm > 1500 -> 540; rpm > 0 -> 800; else -> 100_000
}
