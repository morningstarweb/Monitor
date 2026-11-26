package app.monitor.feature.gitworkspace.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
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

/**
 * High-tech Working Tree Status HUD (< 130 lines).
 * Features living orbital branch node, dynamic branch status pill, and glowing diff count chips.
 */
@Composable
fun WorkingTreeStatusCard(
    currentBranch: String,
    addedCount: Int,
    modifiedCount: Int,
    deletedCount: Int,
    onBranchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(22.dp)
    val transition = rememberInfiniteTransition(label = "StatusGlow")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.40f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "dotAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0xFF0C0E1A).copy(alpha = 0.94f))
            .border(
                1.2.dp,
                Brush.verticalGradient(listOf(Color.White.copy(0.18f), Color(0xFF818CF8).copy(0.14f), Color.Transparent)),
                cardShape
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable(onClick = onBranchClick)
            ) {
                // Living Orbital Branch Node Container
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF14182E))
                        .border(1.2.dp, Brush.linearGradient(listOf(Color(0x8000E5FF), Color(0x40818CF8))), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    LivingGitBranchNode(tint = Color(0xFF00E5FF), size = 24.dp)
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(text = currentBranch, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFA1A1AA), modifier = Modifier.size(16.dp))
                    }

                    // Living Branch Status Tag
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981).copy(alpha = dotAlpha), CircleShape))
                        Text(text = "Working Tree • Synchronized", fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                    }
                }
            }

            // High-Contrast Glowing Diff Badges
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DiffCountPill(label = "A", count = addedCount, color = Color(0xFF10B981), bg = Color(0xFF064E3B))
                DiffCountPill(label = "M", count = modifiedCount, color = Color(0xFFC084FC), bg = Color(0xFF4C1D95))
                DiffCountPill(label = "D", count = deletedCount, color = Color(0xFFEF4444), bg = Color(0xFF7F1D1D))
            }
        }
    }
}

@Composable
private fun DiffCountPill(label: String, count: Int, color: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg.copy(alpha = 0.45f))
            .border(1.dp, color.copy(alpha = 0.50f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = "$count", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
