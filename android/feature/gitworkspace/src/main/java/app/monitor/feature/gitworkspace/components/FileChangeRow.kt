package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.components.MonitorCard
import app.monitor.core.designsystem.theme.AccentMint
import app.monitor.core.designsystem.theme.BorderStroke
import app.monitor.core.designsystem.theme.ErrorRed
import app.monitor.core.designsystem.theme.PrimaryAccent
import app.monitor.core.designsystem.theme.TextMuted
import app.monitor.core.designsystem.theme.TextPrimary
import app.monitor.core.designsystem.theme.WarningAmber
import app.monitor.core.model.FileChangeCategory
import app.monitor.core.model.GitFileChange

@Composable
fun FileChangeRow(file: GitFileChange, selected: Boolean, onClick: () -> Unit) {
    val (badge, color) = when (file.category) {
        FileChangeCategory.Modified -> "M" to WarningAmber
        FileChangeCategory.Added -> "A" to AccentMint
        FileChangeCategory.Deleted -> "D" to ErrorRed
        FileChangeCategory.Untracked -> "?" to PrimaryAccent
        FileChangeCategory.Renamed -> "R" to PrimaryAccent
        FileChangeCategory.Copied -> "C" to PrimaryAccent
        FileChangeCategory.Conflict -> "!" to ErrorRed
    }
    MonitorCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        borderColor = if (selected) PrimaryAccent.copy(alpha = .6f) else BorderStroke
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(30.dp).background(color.copy(alpha = .12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text(badge, color = color, style = MaterialTheme.typography.labelMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(file.path.substringAfterLast('/'), color = TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(file.path, color = TextMuted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (file.staged) "Staged" else "Working tree", color = if (file.staged) AccentMint else TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
