package app.monitor.feature.gitworkspace.dialogs

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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.monitor.core.designsystem.theme.LocalMonitorColors

/**
 * Obsidian Glass Git Author Identity Configuration Dialog (< 180 lines).
 * Aligns 1:1 with CommitChangesCard and ActiveRepoHeroCard design language.
 */
@Composable
fun GitAuthorIdentityDialog(
    initialName: String = "", initialEmail: String = "", isSaving: Boolean = false,
    onSave: (name: String, email: String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    val isDark = LocalMonitorColors.current.isDark
    val isValid = name.trim().isNotBlank() && email.trim().contains("@")
    val cardShape = RoundedCornerShape(24.dp)
    val cardBg = if (isDark) Color(0xFF0C0E1A).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f)
    val cardBorder = if (isDark) {
        Brush.verticalGradient(listOf(Color.White.copy(0.22f), Color(0xFF818CF8).copy(0.18f), Color(0xFF00FF87).copy(0.12f), Color.Transparent))
    } else {
        Brush.verticalGradient(listOf(Color(0x40CBD5E1), Color(0x2094A3B8), Color.Transparent))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxWidth(0.92f).clip(cardShape).background(cardBg).border(1.2.dp, cardBorder, cardShape).padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header Station
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF7C3AED).copy(0.28f), Color(0xFF00FF87).copy(0.20f))))
                            .border(1.dp, Brush.linearGradient(listOf(Color(0xFFC084FC).copy(0.5f), Color(0xFF00FF87).copy(0.4f))), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF00FF87), modifier = Modifier.size(22.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Git Author Profile", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A))
                        Text("Signature stamped on your Git commits", fontSize = 12.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                    }
                }

                // Name & Email Fields
                ObsidianAuthorField("AUTHOR NAME", name, { name = it }, "e.g. Shamim Akhter", Icons.Default.Person, isDark)
                ObsidianAuthorField("AUTHOR EMAIL", email, { email = it }, "e.g. user@example.com", Icons.Default.Email, isDark)

                // Actions Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (isDark) Color(0xFF14192B) else Color(0xFFF1F5F9))
                            .border(1.dp, if (isDark) Color(0x30818CF8) else Color(0x30CBD5E1), RoundedCornerShape(20.dp))
                            .clickable(onClick = onDismiss), contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                    }

                    val btnEnabled = isValid && !isSaving
                    val saveBg = if (btnEnabled) Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF4F46E5)))
                                 else Brush.linearGradient(listOf(Color(0xFF1E1738), Color(0xFF1E1738)))
                    val saveBorder = if (btnEnabled) Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFC084FC)))
                                     else Brush.linearGradient(listOf(Color(0x30818CF8), Color(0x30818CF8)))

                    Box(
                        modifier = Modifier.weight(1.4f).height(44.dp).clip(RoundedCornerShape(20.dp)).background(saveBg).border(1.2.dp, saveBorder, RoundedCornerShape(20.dp))
                            .clickable(enabled = btnEnabled) { onSave(name.trim(), email.trim()) }, contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save Profile", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (btnEnabled) Color.White else Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsidianAuthorField(
    label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, icon: ImageVector, isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFF818CF8) else Color(0xFF6366F1), letterSpacing = 0.5.sp)
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8), fontSize = 13.5.sp) },
            leadingIcon = { Icon(icon, contentDescription = null, tint = if (isDark) Color(0xFF818CF8) else Color(0xFF6366F1), modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
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
    }
}
