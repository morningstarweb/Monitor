package app.monitor.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

enum class MonitorTab(val label: String) {
    Home("Home"),
    Projects("Projects"),
    Git("Git"),
    System("System"),
    Settings("Settings")
}

/**
 * 3D Frosted Obsidian Acrylic Glass Dock.
 * Deep dark tone matching the header without bleeding underlying list content.
 */
@Composable
fun BottomNavigationBar(
    currentTab: MonitorTab,
    onTabSelected: (MonitorTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = RoundedCornerShape(32.dp)
    val transition = rememberInfiniteTransition(label = "DockBorderGlow")
    val borderPhase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "borderPhase"
    )

    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val dockBg = if (isDark) listOf(Color(0xFF131728), Color(0xFF090B14).copy(alpha = 0.98f))
    else listOf(Color(0xFFFFFFFF).copy(alpha = 0.96f), Color(0xFFF1F5F9).copy(alpha = 0.98f))
    val rimColors = if (isDark) listOf(Color(0xFF00E5FF), Color(0xFF818CF8), Color(0xFFC084FC), Color(0x35FFFFFF), Color(0xFF00E5FF))
    else listOf(Color(0xFF0284C7), Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0x25000000), Color(0xFF0284C7))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Deep Frosted Glass Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(dockShape)
                .background(brush = Brush.verticalGradient(dockBg))
        ) {
            // Continuous Looping Animated 3D Neon Glass Rim
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p = borderPhase.toDouble(); val w = size.width; val h = size.height; val r = w * 0.6f
                val startX = w * 0.5f + r * cos(p).toFloat(); val startY = h * 0.5f + r * sin(p).toFloat()
                val endX = w * 0.5f - r * cos(p).toFloat(); val endY = h * 0.5f - r * sin(p).toFloat()
                drawRoundRect(
                    brush = Brush.linearGradient(colors = rimColors, start = Offset(startX, startY), end = Offset(endX, endY)),
                    cornerRadius = CornerRadius(32.dp.toPx(), 32.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }

            // Glass Chamfered Top-Rim Light
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .border(
                        0.8.dp,
                        Brush.verticalGradient(if (isDark) listOf(Color.White.copy(0.25f), Color.Transparent, Color.Black.copy(0.50f)) else listOf(Color.White.copy(0.6f), Color.Transparent, Color.Black.copy(0.10f))),
                        dockShape
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MonitorTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val activeColor = if (isDark) Color.White else Color(0xFF7C3AED)
                    val inactiveColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    val iconTint by animateColorAsState(targetValue = if (selected) activeColor else inactiveColor, animationSpec = tween(220), label = "iconTint")
                    val labelColor by animateColorAsState(targetValue = if (selected) activeColor else inactiveColor, animationSpec = tween(220), label = "labelColor")

                    val haptic = LocalHapticFeedback.current
                    val itemShape = RoundedCornerShape(24.dp)
                    val itemBg = if (selected) (if (isDark) Color(0xFF7C3AED).copy(alpha = 0.28f) else Color(0xFF7C3AED).copy(alpha = 0.12f)) else Color.Transparent
                    val itemBorder = if (selected) (if (isDark) Color(0x90C084FC) else Color(0xFF7C3AED)) else Color.Transparent
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(itemShape)
                            .background(itemBg)
                            .then(if (selected) Modifier.border(1.dp, itemBorder, itemShape) else Modifier)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true)
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(tab)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            when (tab) {
                                MonitorTab.Home -> NavHomeAnimatedIcon(selected, iconTint)
                                MonitorTab.Projects -> NavProjectsAnimatedIcon(selected, iconTint)
                                MonitorTab.Git -> NavGitAnimatedIcon(selected, iconTint)
                                MonitorTab.System -> NavSystemAnimatedIcon(selected, iconTint)
                                MonitorTab.Settings -> NavSettingsAnimatedIcon(selected, iconTint)
                            }
                            Text(
                                text = tab.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = labelColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
