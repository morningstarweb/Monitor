package app.monitor.feature.dashboard.settings.model

enum class ThemeMode(val title: String) {
    SYSTEM_DEFAULT("System default"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class SamplingInterval(val seconds: Int, val label: String) {
    FAST_1S(1, "1s (High Fidelity)"),
    BALANCED_2S(2, "2s (Balanced)"),
    ECO_5S(5, "5s (Power Saver)")
}

enum class TemperatureUnit(val symbol: String, val label: String) {
    CELSIUS("°C", "Celsius (°C)"),
    FAHRENHEIT("°F", "Fahrenheit (°F)")
}

enum class ProcessFilterMode(val label: String) {
    ALL("All Processes"),
    TOP_RESOURCE("Top Resource (>1% CPU)"),
    USER_ONLY("User Processes Only")
}

enum class BrokerSyncFrequency(val seconds: Int, val label: String) {
    REALTIME(1, "Realtime Stream (1s)"),
    NORMAL(5, "Normal Interval (5s)"),
    ECO(15, "Power Saver (15s)")
}

enum class SettingsPanel {
    DEVICE,
    ACCOUNT,
    BROKER_SYNC,
    TELEMETRY_PREFS,
    NOTIFICATIONS,
    SECURITY,
    LEGAL
}

data class SettingsPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM_DEFAULT,
    val liveUpdatesEnabled: Boolean = true,
    val samplingInterval: SamplingInterval = SamplingInterval.FAST_1S,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val processFilterMode: ProcessFilterMode = ProcessFilterMode.ALL,
    val showFanImpeller: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val cpuThresholdPercent: Int = 85,
    val ramThresholdPercent: Int = 85,
    val batteryLowThresholdPercent: Int = 20,
    val backgroundSyncEnabled: Boolean = true,
    val gitActivityAlertsEnabled: Boolean = true,
    val brokerSyncFrequency: BrokerSyncFrequency = BrokerSyncFrequency.REALTIME,
    val autoReconnectEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val biometricPromptOnResume: Boolean = false,
    val biometricGatingDestructiveOps: Boolean = true,
    val remoteTerminalEnabled: Boolean = true,
    val brokerUrl: String = "https://your-monitor-broker.workers.dev"
)
