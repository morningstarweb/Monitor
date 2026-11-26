package app.monitor.feature.onboarding.model

import android.os.Build

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object InProgress : PairingUiState
    data class Success(val role: String) : PairingUiState
    data class Error(val message: String) : PairingUiState
}

fun getDeviceDisplayName(): String {
    val manufacturer = try { Build.MANUFACTURER?.trim() ?: "" } catch (_: Throwable) { "" }
    val model = try { Build.MODEL?.trim() ?: "" } catch (_: Throwable) { "" }
    return if (model.startsWith(manufacturer, ignoreCase = true)) {
        model
    } else if (manufacturer.isNotBlank() && model.isNotBlank()) {
        "$manufacturer $model"
    } else if (model.isNotBlank()) {
        model
    } else {
        "Android Phone"
    }
}

data class OnboardingFormState(
    val host: String = "",
    val port: String = "9480",
    val pairingCode: String = "",
    val deviceName: String = getDeviceDisplayName(),
    val pairingState: PairingUiState = PairingUiState.Idle,
    val isGoogleSignedIn: Boolean = false,
    val signedInEmail: String? = null,
    val desktopName: String? = null,
    val desktopHostId: String? = null
)
