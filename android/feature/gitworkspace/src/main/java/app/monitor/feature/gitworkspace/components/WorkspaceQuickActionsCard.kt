package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.components.icons.LightningBoltIcon
import app.monitor.core.designsystem.components.icons.SwitchArrowsIcon

/**
 * Living Obsidian Quick Actions Dock (< 130 lines).
 * Features living animated micro-icons for Branching, Switching, Pulling, and Pushing.
 */
@Composable
fun WorkspaceQuickActionsCard(
    onNewBranchClick: () -> Unit,
    onSwitchBranchClick: () -> Unit,
    onPullClick: () -> Unit,
    onPushClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(22.dp)

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
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LightningBoltIcon(tint = Color(0xFFC084FC), modifier = Modifier.size(18.dp))
                Text(
                    text = "Quick Actions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LivingActionButton(
                    modifier = Modifier.weight(1f),
                    label = "New",
                    iconContent = { LivingGitBranchNode(tint = Color(0xFFC084FC), size = 20.dp) },
                    onClick = onNewBranchClick
                )
                LivingActionButton(
                    modifier = Modifier.weight(1f),
                    label = "Switch",
                    iconContent = { SwitchArrowsIcon(tint = Color(0xFF818CF8), modifier = Modifier.size(20.dp)) },
                    onClick = onSwitchBranchClick
                )
                LivingActionButton(
                    modifier = Modifier.weight(1f),
                    label = "Pull",
                    iconContent = { LivingPullIcon(tint = Color(0xFF10B981), size = 20.dp) },
                    onClick = onPullClick
                )
                LivingActionButton(
                    modifier = Modifier.weight(1f),
                    label = "Push",
                    iconContent = { LivingPushIcon(tint = Color(0xFF00E5FF), size = 20.dp) },
                    onClick = onPushClick
                )
            }
        }
    }
}

@Composable
private fun LivingActionButton(
    label: String,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btnShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(btnShape)
            .background(Color(0xFF131728))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.15f), Color(0x35818CF8))), btnShape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            iconContent()
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}
