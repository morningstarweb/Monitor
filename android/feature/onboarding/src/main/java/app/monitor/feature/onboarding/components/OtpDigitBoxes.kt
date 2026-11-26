package app.monitor.feature.onboarding.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pure Dark Obsidian Glass OTP Digit Cells with Glowing Neon Focus.
 */
@Composable
fun OtpDigitBoxes(
    pairingCode: String,
    onCodeChange: (String) -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
    focusManager: FocusManager,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val boxShape = RoundedCornerShape(14.dp)
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    var isFieldFocused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "6-digit pairing code input, ${pairingCode.length} of 6 digits entered" }
            .clickable(enabled = enabled, onClickLabel = "Enter 6-digit pairing code") {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
    ) {
        // Hidden Native Text Field for IME Input
        BasicTextField(
            value = pairingCode,
            enabled = enabled,
            onValueChange = { newText ->
                val digits = newText.filter { it.isDigit() }
                if (digits.length <= 6) {
                    if (digits.length != pairingCode.length) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onCodeChange(digits)
                    if (digits.length == 6) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onDone()
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
                if (pairingCode.length == 6) onDone()
            }),
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { isFieldFocused = it.isFocused },
            singleLine = true
        )

        // Pulsing cursor alpha
        val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(550, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursorAlpha"
        )

        val isDark = app.monitor.core.designsystem.theme.LocalMonitorColors.current.isDark

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 6) {
                    val char = pairingCode.getOrNull(i)?.toString() ?: ""
                    val isCurrentFocus = isFieldFocused && (pairingCode.length == i || (pairingCode.length == 6 && i == 5))
                    val isFilled = char.isNotEmpty()

                    val cellBg = if (isDark) {
                        if (isCurrentFocus) Color(0xFF141828) else Color.Black.copy(alpha = 0.65f)
                    } else {
                        if (isCurrentFocus) Color(0xFFE2E8F0) else Color(0xFFF1F5F9)
                    }
                    val borderColor = when {
                        isCurrentFocus -> if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                        isFilled -> if (isDark) Color(0xFF818CF8).copy(alpha = 0.7f) else Color(0xFF6366F1).copy(alpha = 0.8f)
                        else -> if (isDark) Color.White.copy(alpha = 0.12f) else Color(0x60CBD5E1)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(boxShape)
                            .background(cellBg)
                            .border(
                                width = if (isCurrentFocus) 1.6.dp else 1.dp,
                                color = borderColor,
                                shape = boxShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isFilled) {
                            Text(
                                text = char,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                        } else if (isCurrentFocus) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(20.dp)
                                    .background((if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)).copy(alpha = cursorAlpha))
                            )
                        }
                    }
                }
            }
        }
    }
}
