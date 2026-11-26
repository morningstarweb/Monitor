package app.monitor.feature.onboarding.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor.feature.onboarding.components.GoogleLogoIcon
import app.monitor.feature.onboarding.components.OtpDigitBoxes
import app.monitor.feature.onboarding.components.PairingDeviceLinkBanner
import app.monitor.feature.onboarding.components.PairingErrorBanner
import app.monitor.feature.onboarding.components.WelcomeBackgroundAura
import app.monitor.feature.onboarding.model.OnboardingFormState
import app.monitor.feature.onboarding.model.PairingUiState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun InteractiveLoginScreen(
    form: OnboardingFormState,
    inProgress: Boolean,
    onBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPair: () -> Unit,
    onScanQr: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var showAdvancedHost by remember { mutableStateOf(false) }
    val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark
    val boxShape = RoundedCornerShape(26.dp)
    val googleShape = RoundedCornerShape(16.dp)
    val transition = rememberInfiniteTransition(label = "CardBorderLoop")
    val cardGlowPhase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "cardGlowPhase"
    )

    Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF07080E) else Color.Transparent)) {
        WelcomeBackgroundAura()
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().displayCutoutPadding()
                .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val navBtnBg = if (isDark) Color.Black.copy(0.50f) else Color.White.copy(0.90f)
            val navBtnBorder = if (isDark) Color.White.copy(0.15f) else Color(0x40CBD5E1)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(navBtnBg)
                        .border(1.dp, navBtnBorder, CircleShape)
                        .alpha(if (inProgress) 0.45f else 1f)
                        .clickable(enabled = !inProgress, role = Role.Button) {
                            focusManager.clearFocus()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (isDark) Color.White else Color(0xFF0F172A), modifier = Modifier.size(18.dp)) }

                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(navBtnBg)
                        .border(1.dp, navBtnBorder, CircleShape)
                        .alpha(if (inProgress) 0.45f else 1f)
                        .clickable(enabled = !inProgress, role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScanQr()
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.QrCodeScanner, "Scan QR", tint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7), modifier = Modifier.size(18.dp)) }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            val cardBg = if (isDark) Color.Black.copy(alpha = 0.44f) else Color.White.copy(alpha = 0.92f)
            val cardGlowColors = if (isDark) {
                listOf(Color(0xFF00E5FF), Color(0xFF818CF8), Color(0xFFC084FC), Color(0x35FFFFFF), Color(0xFF00E5FF))
            } else {
                listOf(Color(0xFF0284C7), Color(0xFF6366F1), Color(0xFFA855F7), Color(0x30CBD5E1), Color(0xFF0284C7))
            }
            Box(modifier = Modifier.fillMaxWidth().clip(boxShape).background(cardBg)) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val p = cardGlowPhase.toDouble(); val w = size.width; val h = size.height; val r = w * 0.7f
                    val startX = w * 0.5f + r * cos(p).toFloat(); val startY = h * 0.5f + r * sin(p).toFloat()
                    val endX = w * 0.5f - r * cos(p).toFloat(); val endY = h * 0.5f - r * sin(p).toFloat()
                    drawRoundRect(
                        brush = Brush.linearGradient(colors = cardGlowColors, start = Offset(startX, startY), end = Offset(endX, endY)),
                        cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx()), style = Stroke(width = 1.8.dp.toPx())
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PairingDeviceLinkBanner()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (form.isGoogleSignedIn) "Verify This Device" else "Pair Device",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        letterSpacing = (-0.4).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (form.isGoogleSignedIn) {
                            "Open Monitor on ${form.desktopName ?: "your Windows PC"} -> Mobile & Devices -> tap ${form.deviceName} for OTP or QR"
                        } else {
                            "Enter the 6-digit code or sign in with Google"
                        },
                        fontSize = 12.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    val gBg = if (isDark) Color(0xFF131726).copy(alpha = 0.85f) else Color.White
                    val gBorder = if (isDark) Brush.verticalGradient(listOf(Color.White.copy(0.20f), Color(0xFF64748B).copy(0.12f))) else Brush.verticalGradient(listOf(Color(0x60CBD5E1), Color(0x3094A3B8)))

                    if (form.isGoogleSignedIn) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(gBg)
                                .border(1.dp, gBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GoogleLogoIcon(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = form.signedInEmail ?: "Google Account Connected",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color.White else Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Switch",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !inProgress, role = Role.Button) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onGoogleSignOut()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(googleShape)
                                .background(if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7))
                                .alpha(if (inProgress) 0.55f else 1f)
                                .clickable(enabled = !inProgress, role = Role.Button) {
                                    focusManager.clearFocus()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onScanQr()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF060A08) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Scan QR Code",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF060A08) else Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(48.dp).clip(googleShape).background(gBg)
                                .border(1.dp, gBorder, googleShape)
                                .alpha(if (inProgress) 0.55f else 1f)
                                .clickable(enabled = !inProgress, role = Role.Button) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onGoogleSignIn()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                GoogleLogoIcon(modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Continue with Google Cloud",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    val divLineColor = if (isDark) Color.White.copy(0.12f) else Color(0x40CBD5E1)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, divLineColor))))
                        Text(
                            if (form.isGoogleSignedIn) "OR ENTER 6-DIGIT OTP" else "OR 6-DIGIT CODE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.3.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(divLineColor, Color.Transparent))))
                    }

                    OtpDigitBoxes(form.pairingCode, onCodeChange, onPair, focusRequester, focusManager, enabled = !inProgress)

                    if (form.pairingCode.length == 6 && !inProgress) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val primaryColor = if (form.isGoogleSignedIn) {
                            if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
                        } else {
                            if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(primaryColor)
                                .clickable(role = Role.Button) {
                                    focusManager.clearFocus()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPair()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (form.isGoogleSignedIn) "Verify OTP & Unlock" else "Pair Device",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF060A08) else Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !inProgress) { showAdvancedHost = !showAdvancedHost }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), modifier = Modifier.size(13.dp))
                        Text(
                            text = if (showAdvancedHost) "Hide Manual LAN IP / Port" else "Manual LAN IP / Port",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedHost) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val fieldColors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7),
                                unfocusedBorderColor = if (isDark) Color.White.copy(0.18f) else Color(0xFFCBD5E1),
                                focusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                                unfocusedTextColor = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                            OutlinedTextField(
                                value = form.host,
                                onValueChange = onHostChange,
                                enabled = !inProgress,
                                label = { Text("Desktop IP", fontSize = 11.sp) },
                                placeholder = { Text("192.168.1.10", fontSize = 12.sp) },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier.weight(1.7f)
                            )
                            OutlinedTextField(
                                value = form.port,
                                onValueChange = onPortChange,
                                enabled = !inProgress,
                                label = { Text("Port", fontSize = 11.sp) },
                                placeholder = { Text("9480", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    AnimatedVisibility(visible = inProgress) {
                        val accentProgress = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                        Row(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentProgress)
                            Text("Connecting to desktop...", fontSize = 13.sp, color = accentProgress, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (form.isGoogleSignedIn) {
                            "Verified once as T3 Admin until sign-out or uninstall"
                        } else {
                            "Tip: Find code in your Windows PC taskbar tray"
                        },
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )
                }
            }

            AnimatedVisibility(visible = form.pairingState is PairingUiState.Error) {
                if (form.pairingState is PairingUiState.Error) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        PairingErrorBanner(errorMessage = (form.pairingState as PairingUiState.Error).message)
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1.0f))
        }
    }
}
