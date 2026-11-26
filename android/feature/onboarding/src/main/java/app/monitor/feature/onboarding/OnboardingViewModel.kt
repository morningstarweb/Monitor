package app.monitor.feature.onboarding

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.monitor.core.model.PairingRequest
import app.monitor.core.model.PairingResponse
import app.monitor.core.network.MonitorApiClient
import app.monitor.core.network.MonitorApiException
import app.monitor.core.network.TokenStorage
import app.monitor.feature.onboarding.data.CloudAuthService
import app.monitor.feature.onboarding.model.OnboardingFormState
import app.monitor.feature.onboarding.model.PairingUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

typealias PairingUiState = PairingUiState
typealias OnboardingFormState = OnboardingFormState

class OnboardingViewModel(
    private val apiClient: MonitorApiClient,
    private val tokenStorage: TokenStorage
) : ViewModel() {
    private val _formState = MutableStateFlow(
        OnboardingFormState(
            host = tokenStorage.getServerHost(),
            port = tokenStorage.getServerPort().let { if (it > 0) it.toString() else "9480" },
            isGoogleSignedIn = !tokenStorage.getCloudSessionToken().isNullOrBlank(),
            signedInEmail = tokenStorage.getUserEmail(),
            desktopHostId = tokenStorage.getCloudHostDeviceId()
        )
    )
    val formState = _formState.asStateFlow()

    init {
        if (!tokenStorage.getCloudSessionToken().isNullOrBlank() && !tokenStorage.isAuthenticated()) {
            apiClient.announceUnverifiedDevice(tokenStorage.getCloudHostDeviceId())
        }
    }

    fun updateHost(host: String) {
        _formState.update {
            it.copy(
                host = host.trim(),
                pairingState = if (it.pairingState is PairingUiState.Error) PairingUiState.Idle else it.pairingState
            )
        }
    }

    fun updatePort(port: String) {
        val digitsOnly = port.filter { it.isDigit() }.take(5)
        _formState.update {
            it.copy(
                port = digitsOnly,
                pairingState = if (it.pairingState is PairingUiState.Error) PairingUiState.Idle else it.pairingState
            )
        }
    }

    fun updatePairingCode(code: String) {
        _formState.update {
            it.copy(
                pairingCode = code.filter { c -> c in '0'..'9' }.take(6),
                pairingState = if (it.pairingState is PairingUiState.Error) PairingUiState.Idle else it.pairingState
            )
        }
    }

    fun clearError() {
        _formState.update {
            if (it.pairingState is PairingUiState.Error) it.copy(pairingState = PairingUiState.Idle) else it
        }
    }

    fun setError(message: String) {
        _formState.update { it.copy(pairingState = PairingUiState.Error(message)) }
    }

    fun processQrCode(raw: String, onSuccess: () -> Unit) {
        val trimmed = raw.trim()
        var host = _formState.value.host
        var port = _formState.value.port.ifBlank { "9480" }
        var code = ""
        var scannedHostId = _formState.value.desktopHostId

        try {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = JSONObject(trimmed)
                if (json.has("host")) host = json.optString("host", host)
                if (json.has("port")) port = json.optString("port", port)
                if (json.has("code")) code = json.optString("code", "")
                val hId = json.optString("host_id").ifBlank { json.optString("hostId") }
                if (hId.isNotBlank()) scannedHostId = hId
            } else if (trimmed.contains("code=")) {
                val normalized = if (trimmed.contains("://") || trimmed.startsWith("?")) trimmed else "https://monitor.local/?$trimmed"
                val uri = Uri.parse(normalized)
                uri.getQueryParameter("host")?.takeIf { it.isNotBlank() }?.let { host = it }
                uri.getQueryParameter("port")?.takeIf { it.isNotBlank() }?.let { port = it }
                uri.getQueryParameter("code")?.let { code = it }
                (uri.getQueryParameter("host_id") ?: uri.getQueryParameter("hostId"))
                    ?.takeIf { it.isNotBlank() }
                    ?.let { scannedHostId = it }
            } else if (trimmed.length == 6 && trimmed.all { it.isDigit() }) {
                code = trimmed
            }
        } catch (_: Exception) {}

        if (code.length != 6 || code.any { it !in '0'..'9' }) {
            _formState.update { it.copy(pairingState = PairingUiState.Error("Invalid QR code format. Please scan a Monitor pairing QR code.")) }
            return
        }

        if (!scannedHostId.isNullOrBlank()) {
            tokenStorage.saveCloudHostDeviceId(scannedHostId)
        }
        _formState.update {
            it.copy(
                host = host,
                port = port,
                pairingCode = code,
                desktopHostId = scannedHostId
            )
        }
        pair(onSuccess)
    }

    fun pair(onSuccess: () -> Unit) {
        val state = _formState.value
        if (state.pairingState is PairingUiState.InProgress) return
        val portNum = state.port.toIntOrNull() ?: 9480
        if (state.pairingCode.length != 6) {
            _formState.update { it.copy(pairingState = PairingUiState.Error("Enter the 6-digit code shown on your desktop.")) }
            return
        }

        val rawHost = state.host.trim()
        val hasCloudToken = !tokenStorage.getCloudSessionToken().isNullOrBlank()
        _formState.update { it.copy(pairingState = PairingUiState.InProgress) }

        viewModelScope.launch {
            val cloudDeviceId = tokenStorage.getOrCreateCloudDeviceId()
            val publicKeyHex = tokenStorage.getOrCreateNoiseStaticKeyPair().publicKey
                .joinToString("") { "%02x".format(it) }
            val pairingReq = PairingRequest(
                pairingCode = state.pairingCode,
                deviceName = state.deviceName,
                publicKey = publicKeyHex,
                cloudDeviceId = cloudDeviceId
            )

            var result: Result<PairingResponse>? = null
            var resolvedLanHost: String? = null

            val lanHost = rawHost.ifBlank { if (!hasCloudToken) "127.0.0.1" else "" }
            if (lanHost.isNotBlank()) {
                var lanResult = apiClient.pair(lanHost, portNum, pairingReq)
                var candidateHost = lanHost
                if (lanResult.isFailure && !lanResult.exceptionOrNull().isExplicitAuthRejection() &&
                    (lanHost == "127.0.0.1" || lanHost == "localhost")
                ) {
                    val emulatorFallback = apiClient.pair("10.0.2.2", portNum, pairingReq)
                    if (emulatorFallback.isSuccess || emulatorFallback.exceptionOrNull().isExplicitAuthRejection()) {
                        lanResult = emulatorFallback
                        candidateHost = "10.0.2.2"
                    }
                }
                result = lanResult
                if (lanResult.isSuccess) {
                    resolvedLanHost = candidateHost
                }
            }

            val shouldTryRemote = hasCloudToken && (
                result == null || (result.isFailure && !result.exceptionOrNull().isExplicitAuthRejection())
            )
            if (shouldTryRemote) {
                val remoteResult = apiClient.verifyDeviceOtpRemote(
                    pairingCode = state.pairingCode,
                    deviceName = state.deviceName,
                    publicKeyHex = publicKeyHex,
                    cloudDeviceId = cloudDeviceId
                )
                if (remoteResult.isSuccess || result == null || remoteResult.exceptionOrNull().isExplicitAuthRejection()) {
                    result = remoteResult
                    resolvedLanHost = null
                }
            }

            val finalResult = result ?: Result.failure(IllegalStateException("Unable to reach desktop"))
            finalResult.fold(
                onSuccess = { response ->
                    if (resolvedLanHost != null) {
                        tokenStorage.savePairing(resolvedLanHost, portNum, response.deviceToken)
                    } else if (response.deviceToken.isNotBlank()) {
                        tokenStorage.saveDeviceToken(response.deviceToken)
                    }
                    tokenStorage.markCloudVerified()
                    _formState.update { it.copy(pairingState = PairingUiState.Success(response.role.name)) }
                    onSuccess()
                },
                onFailure = { error ->
                    val errorMsg = error.message?.takeIf { it.isNotBlank() }
                        ?: "Verification failed. Ensure Monitor Desktop is running and the 6-digit OTP is active."
                    _formState.update { it.copy(pairingState = PairingUiState.Error(errorMsg)) }
                }
            )
        }
    }

    fun signInWithGoogle(
        idToken: String,
        photoUrl: String? = null,
        @Suppress("UNUSED_PARAMETER") onSuccess: () -> Unit = {}
    ) {
        val state = _formState.value
        if (state.pairingState is PairingUiState.InProgress) return

        _formState.update { it.copy(pairingState = PairingUiState.InProgress) }

        viewModelScope.launch {
            val result = CloudAuthService.verifyGoogleToken(idToken, state.deviceName, tokenStorage)
            result.fold(
                onSuccess = { authResult ->
                    tokenStorage.saveCloudAuth(
                        cloudToken = authResult.token,
                        email = authResult.email,
                        name = authResult.name,
                        host = authResult.detectedHost,
                        port = authResult.detectedPort,
                        picture = authResult.picture ?: photoUrl,
                        desktopHostId = authResult.detectedDesktopDeviceId,
                        verified = false
                    )
                    // Announce this unverified phone right away so it shows up in Desktop Tray -> Mobile & Devices.
                    apiClient.announceUnverifiedDevice(authResult.detectedDesktopDeviceId)
                    _formState.update {
                        it.copy(
                            host = authResult.detectedHost ?: it.host,
                            port = (authResult.detectedPort ?: 9480).toString(),
                            pairingCode = "",
                            isGoogleSignedIn = true,
                            signedInEmail = authResult.email,
                            desktopName = authResult.detectedDesktopName,
                            desktopHostId = authResult.detectedDesktopDeviceId,
                            pairingState = PairingUiState.Idle
                        )
                    }
                },
                onFailure = { error ->
                    val errorMsg = error.message?.takeIf { it.isNotBlank() }
                        ?: "Google Cloud authentication failed. Check your network connection."
                    _formState.update { it.copy(pairingState = PairingUiState.Error(errorMsg)) }
                }
            )
        }
    }

    fun signOutGoogle() {
        apiClient.disconnectTransport()
        tokenStorage.clearAccountData()
        _formState.update {
            OnboardingFormState(deviceName = it.deviceName)
        }
    }

    private fun Throwable?.isExplicitAuthRejection(): Boolean {
        val status = (this as? MonitorApiException)?.status ?: return false
        return status == 400 || status == 401 || status == 403 || status == 429
    }
}
