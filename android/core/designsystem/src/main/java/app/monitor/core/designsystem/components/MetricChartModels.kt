package app.monitor.core.designsystem.components

import androidx.compose.ui.graphics.Color

enum class ChartTimeRange(val label: String) {
    Range15m("15m"),
    Range1h("1h"),
    Range6h("6h"),
    Range24h("24h")
}

data class MetricSeries(
    val name: String,
    val data: List<Float>,
    val primaryColor: Color,
    val secondaryColor: Color
)
