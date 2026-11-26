package app.monitor.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum"
)

val MonitorTypography = Typography(
    displayLarge = type(64, 68, FontWeight.Light, -3f),
    displayMedium = type(48, 52, FontWeight.Light, -2f),
    displaySmall = type(40, 46, FontWeight.Medium, -1.5f),
    headlineLarge = type(34, 40, FontWeight.SemiBold, -1.1f),
    headlineMedium = type(28, 34, FontWeight.SemiBold, -0.7f),
    headlineSmall = type(23, 29, FontWeight.Medium, -0.5f),
    titleLarge = type(21, 27, FontWeight.Medium, -0.3f),
    titleMedium = type(17, 23, FontWeight.Medium, -0.2f),
    titleSmall = type(15, 21, FontWeight.Medium),
    bodyLarge = type(16, 24),
    bodyMedium = type(14, 21),
    bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.SemiBold),
    labelMedium = type(12, 17, FontWeight.Medium, 0.2f),
    labelSmall = type(10, 15, FontWeight.Medium, 0.8f)
)
