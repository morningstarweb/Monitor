package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.core.designsystem.theme.LocalMonitorColors

/**
 * Living Obsidian/Crystal Commit Changes Card (< 130 lines).
 */
@Composable
fun CommitChangesCard(
    commitMessage: String,
    onCommitMessageChange: (String) -> Unit,
    selectedCount: Int,
    isCommitting: Boolean,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalMonitorColors.current.isDark
    val enabled = selectedCount > 0 && !isCommitting
    val cardShape = RoundedCornerShape(22.dp)
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)
    val cardBorder = if (isDark) Brush.verticalGradient(listOf(Color.White.copy(0.18f), Color(0xFF818CF8).copy(0.14f), Color.Transparent))
                     else Brush.verticalGradient(listOf(Color(0x40CBD5E1), Color(0x2094A3B8), Color.Transparent))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(1.2.dp, cardBorder, cardShape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED), modifier = Modifier.size(17.dp))
                Text(
                    text = "Commit Changes",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
            }

            OutlinedTextField(
                value = commitMessage,
                onValueChange = onCommitMessageChange,
                placeholder = { Text("Enter commit message...", color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), fontSize = 13.5.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isDark) Color(0xFF8B5CF6) else Color(0xFF7C3AED),
                    unfocusedBorderColor = if (isDark) Color(0x35818CF8) else Color(0x30CBD5E1),
                    focusedContainerColor = if (isDark) Color(0xFF080A14) else Color(0xFFF8FAFC),
                    unfocusedContainerColor = if (isDark) Color(0xFF080A14) else Color(0xFFF8FAFC),
                    focusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                    unfocusedTextColor = if (isDark) Color.White else Color(0xFF0F172A)
                ),
                textStyle = TextStyle(color = if (isDark) Color.White else Color(0xFF0F172A), fontSize = 13.5.sp),
                singleLine = true
            )

            val btnShape = RoundedCornerShape(24.dp)
            val disabledBg = if (isDark) Brush.linearGradient(listOf(Color(0xFF1E1738), Color(0xFF1E1738)))
                             else Brush.linearGradient(listOf(Color(0xFFF1F5F9), Color(0xFFF1F5F9)))
            val disabledBorder = if (isDark) Brush.linearGradient(listOf(Color(0x30818CF8), Color(0x30818CF8)))
                                 else Brush.linearGradient(listOf(Color(0x20CBD5E1), Color(0x20CBD5E1)))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(btnShape)
                    .background(
                        if (enabled) Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF4F46E5)))
                        else disabledBg
                    )
                    .border(
                        1.2.dp,
                        if (enabled) Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFC084FC)))
                        else disabledBorder,
                        btnShape
                    )
                    .clickable(enabled = enabled, onClick = onCommit),
                contentAlignment = Alignment.Center
            ) {
                if (isCommitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LivingGitBranchNode(tint = if (enabled) Color.White else Color(0xFF94A3B8), size = 18.dp)
                        Text(
                            text = if (selectedCount > 0) "Commit ($selectedCount file${if (selectedCount > 1) "s" else ""})" else "Commit",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}
