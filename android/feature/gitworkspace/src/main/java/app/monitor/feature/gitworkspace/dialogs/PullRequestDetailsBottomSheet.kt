package app.monitor.feature.gitworkspace.dialogs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.GitFileChange
import app.monitor.core.model.PullRequestSummary
import app.monitor.feature.gitworkspace.components.CommitFilesChangedSection
import app.monitor.feature.gitworkspace.components.PullRequestRoutingCard

/**
 * Clean & Minimalist Pull Request Inspector Sheet (< 160 lines).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRequestDetailsBottomSheet(
    pr: PullRequestSummary,
    workspaceFiles: List<GitFileChange> = emptyList(),
    onMergePr: (() -> Unit)? = null,
    onViewFullDiff: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val effectiveFiles = workspaceFiles
    val isOpen = pr.state.equals("OPEN", ignoreCase = true)
    val cardBackground = Color(0xFF0C0E1A).copy(alpha = 0.94f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0C0E1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: #PR + Status + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pull Request #${pr.number}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isOpen) Color(0x2000E699) else Color(0x208B5CF6))
                            .border(1.dp, if (isOpen) Color(0x6000E699) else Color(0x608B5CF6), RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (isOpen) Color(0xFF00E699) else Color(0xFFC084FC), CircleShape)
                            )
                            Text(pr.state, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (isOpen) Color(0xFF00E699) else Color(0xFFC084FC))
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF13182C))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFA1A1AA), modifier = Modifier.size(16.dp))
                }
            }

            // Title
            Text(pr.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, lineHeight = 22.sp)

            // Branch Routing & Author Card (Structured & Compact)
            PullRequestRoutingCard(pr = pr)

            // Clean & Elegant Merge Card
            if (isOpen) {
                val activeBorder = Brush.linearGradient(
                    listOf(Color(0xFF00E699), Color(0xFF00E5FF), Color(0xFF818CF8))
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBackground)
                        .border(1.3.dp, activeBorder, RoundedCornerShape(18.dp))
                        .clickable {
                            onMergePr?.invoke()
                            onDismiss()
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF064E3B).copy(alpha = 0.50f))
                                    .border(1.dp, Color(0x6010B981), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = Color(0xFF00E699), modifier = Modifier.size(18.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Merge Pull Request", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Fast-forward into ${pr.baseBranch}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2200E699))
                                .border(1.dp, Color(0x6000E699), RoundedCornerShape(8.dp))
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Text("Merge", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E699))
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBackground)
                        .border(1.dp, Color(0x408B5CF8), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(17.dp))
                        Text("This Pull Request is merged into ${pr.baseBranch}.", fontSize = 12.5.sp, color = Color(0xFFE2E8F0), fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Changed Files Section
            CommitFilesChangedSection(
                files = effectiveFiles
            )
        }
    }
}
