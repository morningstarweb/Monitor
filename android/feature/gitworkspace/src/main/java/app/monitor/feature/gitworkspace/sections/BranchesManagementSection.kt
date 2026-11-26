package app.monitor.feature.gitworkspace.sections

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors
import app.monitor.core.model.BranchInfo
import app.monitor.feature.gitworkspace.dialogs.SmartMergeAllDialog

/**
 * Clean & Minimalist Branch Operations Studio.
 * Follows the unified Obsidian/Crystal glass design system.
 */
@Composable
fun BranchesManagementSection(
    branches: List<BranchInfo>,
    currentBranch: String,
    onNewBranchClick: () -> Unit,
    onSwitchBranch: (String) -> Unit,
    onMergeBranch: (String) -> Unit,
    onDeleteBranch: (String) -> Unit,
    onMergeAllBranches: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    val localBranches = branches.filter { !it.isRemote }
    val effectiveLocalBranches = if (localBranches.isNotEmpty()) {
        localBranches
    } else if (currentBranch.isNotBlank()) {
        listOf(BranchInfo(name = currentBranch, isCurrent = true, isRemote = false))
    } else {
        emptyList()
    }
    val unmergedBranches = effectiveLocalBranches.filter {
        !it.isCurrent && !it.isRemote && !it.isMerged && it.unmergedCommits > 0 && it.name != currentBranch
    }
    var showMergeAllDialog by remember { mutableStateOf(false) }
    val remoteBranches = branches.filter { it.isRemote }
    val cardBackground = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.90f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section Header with status dot and "+ New Branch"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), CircleShape)
                        .border(1.5.dp, if (isDark) Color(0x8000E5FF) else Color(0x400284C7), CircleShape)
                )
                Text(
                    text = "Branch Operations",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(cardBackground)
                    .border(
                        1.2.dp,
                        if (isDark) Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFF00E5FF)))
                        else Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF0284C7))),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onNewBranchClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "New Branch",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                }
            }
        }

        // Local Branches Heading
        Text(
            text = "LOCAL BRANCHES (${effectiveLocalBranches.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
            letterSpacing = 0.8.sp
        )

        if (effectiveLocalBranches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBackground)
                    .border(1.dp, if (isDark) Color(0x30818CF8) else Color(0x20CBD5E1), RoundedCornerShape(20.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No local branches found",
                    fontSize = 13.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }
        }

        effectiveLocalBranches.forEach { branch ->
            val isCurrent = branch.name == currentBranch || branch.isCurrent
            val isProtected = branch.name.equals("main", ignoreCase = true) || branch.name.equals("master", ignoreCase = true)

            if (isCurrent) {
                ActiveBranchCard(
                    branch = branch,
                    unmergedBranches = unmergedBranches,
                    isDark = isDark,
                    onMergeAllClick = { showMergeAllDialog = true }
                )
            } else {
                InactiveBranchCard(
                    branch = branch,
                    isProtected = isProtected,
                    isDark = isDark,
                    onSwitchBranch = onSwitchBranch,
                    onMergeBranch = onMergeBranch,
                    onDeleteBranch = onDeleteBranch
                )
            }
        }

        // Remote Tracking Section
        if (remoteBranches.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "REMOTE TRACKING (${remoteBranches.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                letterSpacing = 0.8.sp
            )
            remoteBranches.forEach { r ->
                RemoteBranchCard(branch = r, isDark = isDark)
            }
        }

        if (showMergeAllDialog) {
            SmartMergeAllDialog(
                targetBranch = currentBranch.ifBlank { effectiveLocalBranches.firstOrNull { it.isCurrent }?.name ?: "main" },
                unmergedBranches = unmergedBranches,
                isOperating = false,
                onConfirm = {
                    onMergeAllBranches(unmergedBranches.map { it.name })
                },
                onDismiss = { showMergeAllDialog = false }
            )
        }
    }
}
