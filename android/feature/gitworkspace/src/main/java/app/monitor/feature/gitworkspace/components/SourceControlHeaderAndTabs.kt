package app.monitor.feature.gitworkspace.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.monitor.core.designsystem.theme.TextMuted
import app.monitor.core.designsystem.theme.TextPrimary

@Composable
fun WorkspaceHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                subtitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }
    }
}

@Composable
fun WorkspaceTabs(
    graphSelected: Boolean,
    onChanges: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (graphSelected) {
            OutlinedButton(onClick = onChanges, modifier = Modifier.weight(1f).height(48.dp)) { Text("Changes") }
            Button(onClick = onHistory, modifier = Modifier.weight(1f).height(48.dp)) { Text("Timeline") }
        } else {
            Button(onClick = onChanges, modifier = Modifier.weight(1f).height(48.dp)) { Text("Changes") }
            OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f).height(48.dp)) { Text("Timeline") }
        }
    }
}
