package app.monitor.feature.dashboard.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO,
    METRIC,
    WARNING,
    ERROR
}

data class RealAuditLog(
    val id: String,
    val timestamp: String,
    val message: String,
    val level: LogLevel
)

enum class MetricIconType {
    CPU,
    RAM
}

fun formatLogTime(timestampUtcMs: Long): String {
    if (timestampUtcMs <= 0) return "00:00:00"
    return try {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.format(Date(timestampUtcMs))
    } catch (e: Exception) {
        "00:00:00"
    }
}
