package app.monitor.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Raw Static Accents
val NeonEmeraldRaw = Color(0xFF00FF87)
val EmeraldLightRaw = Color(0xFF00A859)
val NeonEmeraldGlow = Color(0x3300FF87)
val EmeraldDeep = Color(0xFF004D28)
val PrimaryAccent = Color(0xFF00FF87)
val PrimarySoft = Color(0x2600FF87)
val AccentMint = Color(0xFF00FF87)
val SuccessGreen = Color(0xFF00FF87)
val SuccessSoft = Color(0x2600FF87)
val InfoBlue = Color(0xFF00E5FF)
val InfoSoft = Color(0x2600E5FF)
val WarningAmber = Color(0xFFFFB300)
val WarningSoft = Color(0x26FFB300)
val ErrorRed = Color(0xFFFF453A)
val ErrorSoft = Color(0x26FF453A)

// Topological DAG Palette
val DagPalette = listOf(
    Color(0xFF00FF87), Color(0xFF00E5FF), Color(0xFFFFB300), Color(0xFFA855F7),
    Color(0xFFFF3366), Color(0xFF00D2FF), Color(0xFF84CC16), Color(0xFFEAB308)
)

// Vibrant High-Quality Chart & Glow Palette
val NeonCyan = Color(0xFF00E5FF)
val NeonBlue = Color(0xFF38BDF8)
val NeonPurple = Color(0xFFA855F7)
val NeonPink = Color(0xFFEC4899)
val NeonAmber = Color(0xFFFFB300)
val ChartGridLine = Color(0xFF16231A)
val GlassSurfaceDark = Color(0xFF0A120D)
val GlassBorder = Color(0x26FFFFFF)

data class MonitorColors(
    val surfaceBackground: Color,
    val surfaceBackgroundMesh: Color,
    val cardSurface: Color,
    val cardSurfaceElevated: Color,
    val cardSurfaceGlass: Color,
    val borderStroke: Color,
    val borderStrokeGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val neonEmerald: Color,
    val dockBackground: Color,
    val dockBorder: Color,
    val innerCardBg: Color,
    val isDark: Boolean
)

val DarkMonitorColors = MonitorColors(
    surfaceBackground = Color(0xFF060A08),
    surfaceBackgroundMesh = Color(0xFF0A130E),
    cardSurface = Color(0xFF0D1611),
    cardSurfaceElevated = Color(0xFF132219),
    cardSurfaceGlass = Color(0xEE0D1611),
    borderStroke = Color(0xFF1B2E22),
    borderStrokeGlow = Color(0x4D00FF87),
    textPrimary = Color(0xFFF9FAFB),
    textSecondary = Color(0xFFA1B5A8),
    textMuted = Color(0xFF5D7065),
    neonEmerald = Color(0xFF00FF87),
    dockBackground = Color(0xF00A130E),
    dockBorder = Color(0xFF1B2E22),
    innerCardBg = Color(0xFF09100C),
    isDark = true
)

val LightMonitorColors = MonitorColors(
    surfaceBackground = Color(0xFFF4F6F5),
    surfaceBackgroundMesh = Color(0xFFE8ECE9),
    cardSurface = Color(0xFFFFFFFF),
    cardSurfaceElevated = Color(0xFFEAEFEB),
    cardSurfaceGlass = Color(0xEEFFFFFF),
    borderStroke = Color(0xFFD6DDD8),
    borderStrokeGlow = Color(0x4D00A859),
    textPrimary = Color(0xFF0F1713),
    textSecondary = Color(0xFF47554E),
    textMuted = Color(0xFF7A8B82),
    neonEmerald = Color(0xFF00A859),
    dockBackground = Color(0xF5FFFFFF),
    dockBorder = Color(0xFFD6DDD8),
    innerCardBg = Color(0xFFF0F4F1),
    isDark = false
)

val LocalMonitorColors = staticCompositionLocalOf { DarkMonitorColors }

val SurfaceBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.surfaceBackground

val SurfaceBackgroundMesh: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.surfaceBackgroundMesh

val CardSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.cardSurface

val CardSurfaceElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.cardSurfaceElevated

val CardSurfaceGlass: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.cardSurfaceGlass

val BorderStroke: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.borderStroke

val BorderStrokeGlow: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.borderStrokeGlow

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.textSecondary

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.textMuted

val NeonEmerald: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.neonEmerald

val DockBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.dockBackground

val InnerCardBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMonitorColors.current.innerCardBg
