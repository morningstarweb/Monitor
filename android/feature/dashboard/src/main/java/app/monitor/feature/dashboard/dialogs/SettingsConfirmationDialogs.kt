package app.monitor.feature.dashboard.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DestructiveRed = Color(0xFFEF4444)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondaryLight = Color(0xFFA1A1AA)

@Composable
fun SignOutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sign out from Monitor HQ?", color = TextWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Your authenticated session will be terminated and telemetry stream stopped. You will need to sign in again to access your workstation.",
                color = TextSecondaryLight,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed)
            ) {
                Text("Sign out", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondaryLight)
            }
        },
        containerColor = Color(0xFF151426)
    )
}

@Composable
fun UnpairConfirmationDialog(
    hostname: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Unpair this Desktop?", color = TextWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Disconnect \"$hostname\" from this mobile app? You will need to scan a new QR code or enter an OTP from the Windows tray to reconnect.",
                color = TextSecondaryLight,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed)
            ) {
                Text("Unpair", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondaryLight)
            }
        },
        containerColor = Color(0xFF151426)
    )
}
