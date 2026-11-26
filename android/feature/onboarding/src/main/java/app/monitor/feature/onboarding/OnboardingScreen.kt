package app.monitor.feature.onboarding

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor.feature.onboarding.data.GmsAvailability
import app.monitor.feature.onboarding.model.PairingUiState
import app.monitor.feature.onboarding.screens.InteractiveLoginScreen
import app.monitor.feature.onboarding.screens.WelcomeScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

enum class OnboardingStep {
    Welcome,
    Pairing
}

enum class LoginAuthMode {
    GoogleCloud,
    LocalDevicePair
}

/**
 * Clean Root Orchestrator for the Onboarding Journey.
 * Direct instant launch to Welcome screen, with animated transitions to Pairing.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onPairingSuccess: () -> Unit
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    var step by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(if (form.isGoogleSignedIn) OnboardingStep.Pairing else OnboardingStep.Welcome)
    }
    androidx.compose.runtime.LaunchedEffect(form.isGoogleSignedIn) {
        if (form.isGoogleSignedIn) step = OnboardingStep.Pairing
    }
    val inProgress = form.pairingState is PairingUiState.InProgress
    val context = LocalContext.current
    // GMS is optional: a device without Play services still pairs manually.
    val scanner = remember(context) { runCatching { GmsBarcodeScanning.getClient(context) }.getOrNull() }

    androidx.activity.compose.BackHandler(enabled = step == OnboardingStep.Pairing && !inProgress) {
        viewModel.clearError()
        step = OnboardingStep.Welcome
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (!idToken.isNullOrBlank()) {
                viewModel.signInWithGoogle(idToken, account.photoUrl?.toString(), onPairingSuccess)
            } else {
                viewModel.setError("Google sign-in token empty. Try again.")
            }
        } catch (e: ApiException) {
            if (e.statusCode == 12501) {
                viewModel.clearError()
                return@rememberLauncherForActivityResult
            }
            val friendlyReason = when (e.statusCode) {
                10 -> "Google Sign-In mismatch (10): signing certificate SHA-1 is not registered in Google Cloud Console. Use Manual/LAN pairing or register this APK's SHA-1."
                12500 -> "Sign-in error (12500): Google Play Services check failed."
                7 -> "Network error (7): Cannot reach Google servers."
                else -> "Google sign-in failed: status ${e.statusCode} ${e.message.orEmpty()}"
            }
            viewModel.setError(friendlyReason)
        } catch (e: Exception) {
            if (result.resultCode == Activity.RESULT_CANCELED) {
                viewModel.clearError()
            } else {
                viewModel.setError("Google sign-in error: ${e.message}")
            }
        }
    }

    val googleSignInClient = remember(context) {
        runCatching {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("YOUR_GOOGLE_ANDROID_CLIENT_ID.apps.googleusercontent.com")
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso)
        }.getOrNull()
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (targetState == OnboardingStep.Pairing) {
                (slideInHorizontally(tween(480, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(tween(480, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(280)))
            } else if (initialState == OnboardingStep.Pairing && targetState == OnboardingStep.Welcome) {
                (slideInHorizontally(tween(480, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(tween(480, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(280)))
            } else {
                fadeIn(tween(400)) togetherWith fadeOut(tween(300))
            }
        },
        label = "OnboardingStepTransition"
    ) { targetStep ->
        when (targetStep) {
            OnboardingStep.Welcome -> {
                WelcomeScreen(onGetStarted = { step = OnboardingStep.Pairing })
            }
            OnboardingStep.Pairing -> {
                InteractiveLoginScreen(
                    form = form,
                    inProgress = inProgress,
                    onBack = {
                        if (!inProgress) {
                            viewModel.clearError()
                            step = OnboardingStep.Welcome
                        }
                    },
                    onCodeChange = viewModel::updatePairingCode,
                    onHostChange = viewModel::updateHost,
                    onPortChange = viewModel::updatePort,
                    onPair = { viewModel.pair(onPairingSuccess) },
                    onScanQr = {
                        viewModel.clearError()
                        val gms = GmsAvailability.check(context)
                        if (!gms.available || scanner == null) {
                            viewModel.setError(gms.message ?: "QR scanning is unavailable. Enter the code manually.")
                        } else {
                            scanner.startScan()
                                .addOnSuccessListener { code ->
                                    val raw = code.rawValue
                                    if (!raw.isNullOrBlank()) {
                                        viewModel.processQrCode(raw, onPairingSuccess)
                                    } else {
                                        viewModel.setError("Empty QR code scanned. Please try again.")
                                    }
                                }
                                .addOnFailureListener { failure ->
                                    viewModel.setError(failure.message ?: "QR scanning unavailable. Enter code manually.")
                                }
                        }
                    },
                    onGoogleSignIn = {
                        viewModel.clearError()
                        val gms = GmsAvailability.check(context)
                        if (!gms.available || googleSignInClient == null) {
                            viewModel.setError(gms.message ?: "Google sign-in is unavailable. Use manual pairing.")
                        } else {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    },
                    onGoogleSignOut = {
                        googleSignInClient?.signOut()
                        viewModel.signOutGoogle()
                    }
                )
            }
        }
    }
}
