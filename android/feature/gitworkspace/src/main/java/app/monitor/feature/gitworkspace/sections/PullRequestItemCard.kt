package app.monitor.feature.gitworkspace.sections

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.model.PullRequestSummary

@Composable
fun PullRequestItemCard(
    pr: PullRequestSummary,
    glowPulse: Float,
    beaconPulse: Float,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(20.dp)
    val cardBackground = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)
    val isOpen = pr.state.equals("OPEN", ignoreCase = true)
    val isMerged = pr.state.equals("MERGED", ignoreCase = true)
    val isDraft = pr.isDraft

    val borderBrush = when {
        isOpen -> if (isDark) Brush.linearGradient(listOf(Color(0xFF00E699).copy(alpha = glowPulse), Color(0xFF00E5FF), Color(0xFF818CF8)))
                  else Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF0284C7)))
        isMerged -> if (isDark) Brush.linearGradient(listOf(Color(0x758B5CF6), Color(0x35C084FC), Color(0x50818CF8)))
                    else Brush.linearGradient(listOf(Color(0x508B5CF6), Color(0x30A855F7)))
        else -> if (isDark) Brush.linearGradient(listOf(Color(0x70F59E0B), Color(0x35FBBF24), Color(0x4000E5FF)))
                else Brush.linearGradient(listOf(Color(0x50F59E0B), Color(0x30E2E8F0)))
    }

    val authorInitial = pr.author.take(1).uppercase().ifBlank { "D" }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBackground)
            .border(1.3.dp, borderBrush, cardShape)
            .clickable(onClick = onClick)
            .padding(15.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Status Badge + PR # + Updated Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isOpen) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isDark) Color(0x2200E699) else Color(0xFFECFDF5))
                                .border(1.dp, if (isDark) Color(0xFF00E699).copy(alpha = glowPulse) else Color(0x5010B981), RoundedCornerShape(7.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(modifier = Modifier.size(6.dp * beaconPulse).background(if (isDark) Color(0xFF00E699) else Color(0xFF059669), CircleShape))
                                Text(if (isDraft) "DRAFT" else "OPEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF00E699) else Color(0xFF059669))
                            }
                        }
                    } else if (isMerged) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isDark) Color(0x228B5CF6) else Color(0xFFF3E8FF))
                                .border(1.dp, if (isDark) Color(0x608B5CF6) else Color(0x408B5CF6), RoundedCornerShape(7.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED), modifier = Modifier.size(11.dp))
                                Text("MERGED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED))
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isDark) Color(0x20F43F5E) else Color(0xFFFFE4E6))
                                .border(1.dp, if (isDark) Color(0x60F43F5E) else Color(0x40F43F5E), RoundedCornerShape(7.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(pr.state, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFF43F5E) else Color(0xFFE11D48))
                        }
                    }

                    Text(
                        text = "#${pr.number}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                    )
                }

                Text(pr.updatedAt, fontSize = 11.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
            }

            // PR Title
            Text(
                text = pr.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Branch Routing: head -> base
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF13182C) else Color(0xFFF1F5F9))
                    .border(1.dp, if (isDark) Color(0x25818CF8) else Color(0x20CBD5E1), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = pr.headBranch,
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8), modifier = Modifier.size(11.dp))
                    Text(
                        text = pr.baseBranch,
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Author & Impact Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFF00E5FF)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(authorInitial, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(pr.author, fontSize = 11.5.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569), fontWeight = FontWeight.Medium)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pr.changedFilesCount} files", fontSize = 11.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                    Text("+${pr.additions}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF00E699) else Color(0xFF059669))
                    if (pr.deletions > 0) {
                        Text("-${pr.deletions}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFF43F5E) else Color(0xFFE11D48))
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
