package app.monitor.feature.gitworkspace.model

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 100% Authentic VS Code Git Graph 12-Color Palette (matching official Git Graph visual sequence)
val VsCodeBlue = Color(0xFF0078D4)      // 0: #0078d4 (Official VS Code Electric Blue)
val VsCodeOrange = Color(0xFFEA5C00)    // 1: #ea5c00 (Official VS Code Vivid Orange)
val VsCodeTeal = Color(0xFF3B9D94)      // 2: #3b9d94 (Official VS Code Cyan / Teal)
val VsCodePurple = Color(0xFF7B4DA8)    // 3: #7b4da8 (Official VS Code Royal Violet / Purple)
val VsCodeGold = Color(0xFFFFB000)      // 4: #ffb000 (Official VS Code Warm Amber / Gold)
val VsCodeMagenta = Color(0xFFD9008F)   // 5: #d9008f (Vivid Magenta / Rose)
val VsCodeGreen = Color(0xFF00D90A)     // 6: #00d90a (Vivid Green)
val VsCodeRed = Color(0xFFFF0000)       // 7: #ff0000 (Pure Red)
val VsCodePink = Color(0xFFE138E8)      // 8: #e138e8 (Neon Pink)
val VsCodeLime = Color(0xFF85D900)      // 9: #85d900 (Chartreuse Lime)
val VsCodeIndigo = Color(0xFF6F24D6)    // 10: #6f24d6 (Deep Indigo)
val VsCodeCyan = Color(0xFF00E5FF)      // 11: #00e5ff (Electric Cyan)

val VsCodeAmber = VsCodeGold
val VsCodeYellow = VsCodeGold

// Backward-compatible aliases
val NeonEmerald = VsCodeGreen
val ElectricCyan = VsCodeCyan
val CyberViolet = VsCodePurple
val VividAmber = VsCodeAmber
val RadiantRose = VsCodeMagenta
val CosmicSapphire = VsCodeBlue
val NeonLime = VsCodeLime
val FluorescentOrange = VsCodeOrange

val MainBranchGreen = VsCodeBlue
val FeatureBranchCyan = VsCodeOrange
val FeatureBranchAmber = VsCodeAmber
val FeatureBranchPurple = VsCodePurple
val FeatureBranchPink = VsCodeMagenta
val FeatureBranchTeal = VsCodeTeal
val FeatureBranchLime = VsCodeLime
val FeatureBranchRose = VsCodeMagenta

val GraphPalette = listOf(
    VsCodeBlue,      // 0: Blue
    VsCodeOrange,    // 1: Orange
    VsCodeTeal,      // 2: Teal
    VsCodePurple,    // 3: Purple
    VsCodeGold,      // 4: Gold
    VsCodeMagenta,   // 5: Magenta
    VsCodeGreen,     // 6: Green
    VsCodeRed,       // 7: Red
    VsCodePink,      // 8: Pink
    VsCodeLime,      // 9: Lime
    VsCodeIndigo,    // 10: Indigo
    VsCodeCyan       // 11: Cyan
)

fun getLaneColor(colorIndex: Int): Color {
    val idx = if (colorIndex >= 0) colorIndex % GraphPalette.size else 0
    return GraphPalette[idx]
}

fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "Just now"
    val diff = System.currentTimeMillis() - timestamp
    val days = diff / (1000 * 60 * 60 * 24)
    val hours = diff / (1000 * 60 * 60)
    val minutes = diff / (1000 * 60)
    return when {
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}

fun formatFullDate(timestamp: Long): String {
    if (timestamp <= 0) return "Unknown"
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy · HH:mm:ss", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "Unknown"
    }
}
