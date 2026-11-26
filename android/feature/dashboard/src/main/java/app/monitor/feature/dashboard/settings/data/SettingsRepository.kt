package app.monitor.feature.dashboard.settings.data

import android.content.Context
import android.content.SharedPreferences
import app.monitor.core.network.TokenStorage
import app.monitor.feature.dashboard.settings.model.BrokerSyncFrequency
import app.monitor.feature.dashboard.settings.model.ProcessFilterMode
import app.monitor.feature.dashboard.settings.model.SamplingInterval
import app.monitor.feature.dashboard.settings.model.SettingsPreferences
import app.monitor.feature.dashboard.settings.model.TemperatureUnit
import app.monitor.feature.dashboard.settings.model.ThemeMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

interface SettingsRepository {
    val preferencesFlow: Flow<SettingsPreferences>
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun updateLiveUpdates(enabled: Boolean)
    suspend fun updateSamplingInterval(interval: SamplingInterval)
    suspend fun updateTemperatureUnit(unit: TemperatureUnit)
    suspend fun updateProcessFilterMode(mode: ProcessFilterMode)
    suspend fun updateShowFanImpeller(show: Boolean)
    suspend fun updateNotifications(enabled: Boolean)
    suspend fun updateCpuThreshold(threshold: Int)
    suspend fun updateRamThreshold(threshold: Int)
    suspend fun updateBatteryLowThreshold(threshold: Int)
    suspend fun updateBackgroundSync(enabled: Boolean)
    suspend fun updateGitActivityAlerts(enabled: Boolean)
    suspend fun updateBrokerSyncFrequency(frequency: BrokerSyncFrequency)
    suspend fun updateAutoReconnect(enabled: Boolean)
    suspend fun updateAppLock(enabled: Boolean)
    suspend fun updateBiometricOnResume(enabled: Boolean)
    suspend fun updateBiometricDestructiveOps(enabled: Boolean)
    suspend fun updateRemoteTerminal(enabled: Boolean) {}
    suspend fun updateBrokerUrl(url: String)
    suspend fun resetDefaults()
}

class ReactiveSettingsRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_monitor_settings_preferences", Context.MODE_PRIVATE)

    // NEW-10: the encrypted TokenStorage is the single authoritative source for
    // the app-lock preference. The UI must reflect what AppLockGate enforces.
    private val tokenStorage = TokenStorage(context.applicationContext)

    private val _preferencesFlow = MutableStateFlow(loadFromPrefs())
    override val preferencesFlow: Flow<SettingsPreferences> = _preferencesFlow.asStateFlow()

    private fun loadFromPrefs(): SettingsPreferences {
        val themeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM_DEFAULT.name) ?: ThemeMode.SYSTEM_DEFAULT.name
        val sampStr = prefs.getString(KEY_SAMPLING_INTERVAL, SamplingInterval.FAST_1S.name) ?: SamplingInterval.FAST_1S.name
        val tempStr = prefs.getString(KEY_TEMPERATURE_UNIT, TemperatureUnit.CELSIUS.name) ?: TemperatureUnit.CELSIUS.name
        val procStr = prefs.getString(KEY_PROCESS_FILTER, ProcessFilterMode.ALL.name) ?: ProcessFilterMode.ALL.name
        val freqStr = prefs.getString(KEY_BROKER_FREQ, BrokerSyncFrequency.REALTIME.name) ?: BrokerSyncFrequency.REALTIME.name
        val broker = prefs.getString(KEY_BROKER_URL, "https://your-monitor-broker.workers.dev")
            ?: "https://your-monitor-broker.workers.dev"

        return SettingsPreferences(
            themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM_DEFAULT),
            liveUpdatesEnabled = prefs.getBoolean(KEY_LIVE_UPDATES, true),
            samplingInterval = runCatching { SamplingInterval.valueOf(sampStr) }.getOrDefault(SamplingInterval.FAST_1S),
            temperatureUnit = runCatching { TemperatureUnit.valueOf(tempStr) }.getOrDefault(TemperatureUnit.CELSIUS),
            processFilterMode = runCatching { ProcessFilterMode.valueOf(procStr) }.getOrDefault(ProcessFilterMode.ALL),
            showFanImpeller = prefs.getBoolean(KEY_SHOW_FAN, true),
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            cpuThresholdPercent = prefs.getInt(KEY_CPU_THRESHOLD, 85).coerceIn(0, 100),
            ramThresholdPercent = prefs.getInt(KEY_RAM_THRESHOLD, 85).coerceIn(0, 100),
            batteryLowThresholdPercent = prefs.getInt(KEY_BATTERY_THRESHOLD, 20).coerceIn(0, 100),
            backgroundSyncEnabled = prefs.getBoolean(KEY_BG_SYNC, true),
            gitActivityAlertsEnabled = prefs.getBoolean(KEY_GIT_ALERTS, true),
            brokerSyncFrequency = runCatching { BrokerSyncFrequency.valueOf(freqStr) }.getOrDefault(BrokerSyncFrequency.REALTIME),
            autoReconnectEnabled = prefs.getBoolean(KEY_AUTO_RECONNECT, true),
            appLockEnabled = tokenStorage.isAppLockEnabled(),
            biometricPromptOnResume = prefs.getBoolean(KEY_BIO_RESUME, false),
            biometricGatingDestructiveOps = tokenStorage.isDestructiveOpsBiometricGated(),
            remoteTerminalEnabled = prefs.getBoolean(KEY_REMOTE_TERMINAL, true),
            brokerUrl = broker
        )
    }

    private suspend fun updatePreference(
        action: (SharedPreferences.Editor) -> Unit,
        stateTransform: (SettingsPreferences) -> SettingsPreferences
    ) {
        withContext(ioDispatcher) {
            val editor = prefs.edit()
            action(editor)
            editor.apply()
            _preferencesFlow.update(stateTransform)
        }
    }

    override suspend fun updateThemeMode(mode: ThemeMode) = updatePreference({ it.putString(KEY_THEME_MODE, mode.name) }) { it.copy(themeMode = mode) }
    override suspend fun updateLiveUpdates(enabled: Boolean) = updatePreference({ it.putBoolean(KEY_LIVE_UPDATES, enabled) }) { it.copy(liveUpdatesEnabled = enabled) }
    override suspend fun updateSamplingInterval(interval: SamplingInterval) = updatePreference({ it.putString(KEY_SAMPLING_INTERVAL, interval.name) }) { it.copy(samplingInterval = interval) }
    override suspend fun updateTemperatureUnit(unit: TemperatureUnit) = updatePreference({ it.putString(KEY_TEMPERATURE_UNIT, unit.name) }) { it.copy(temperatureUnit = unit) }
    override suspend fun updateProcessFilterMode(mode: ProcessFilterMode) = updatePreference({ it.putString(KEY_PROCESS_FILTER, mode.name) }) { it.copy(processFilterMode = mode) }
    override suspend fun updateShowFanImpeller(show: Boolean) = updatePreference({ it.putBoolean(KEY_SHOW_FAN, show) }) { it.copy(showFanImpeller = show) }
    override suspend fun updateNotifications(enabled: Boolean) = updatePreference({ it.putBoolean(KEY_NOTIFICATIONS, enabled) }) { it.copy(notificationsEnabled = enabled) }
    override suspend fun updateCpuThreshold(threshold: Int) = updatePreference({ it.putInt(KEY_CPU_THRESHOLD, threshold.coerceIn(0, 100)) }) { it.copy(cpuThresholdPercent = threshold.coerceIn(0, 100)) }
    override suspend fun updateRamThreshold(threshold: Int) = updatePreference({ it.putInt(KEY_RAM_THRESHOLD, threshold.coerceIn(0, 100)) }) { it.copy(ramThresholdPercent = threshold.coerceIn(0, 100)) }
    override suspend fun updateBatteryLowThreshold(threshold: Int) = updatePreference({ it.putInt(KEY_BATTERY_THRESHOLD, threshold.coerceIn(0, 100)) }) { it.copy(batteryLowThresholdPercent = threshold.coerceIn(0, 100)) }
    override suspend fun updateBackgroundSync(enabled: Boolean) = updatePreference({ it.putBoolean(KEY_BG_SYNC, enabled) }) { it.copy(backgroundSyncEnabled = enabled) }
    override suspend fun updateGitActivityAlerts(enabled: Boolean) = updatePreference({ it.putBoolean(KEY_GIT_ALERTS, enabled) }) { it.copy(gitActivityAlertsEnabled = enabled) }
    override suspend fun updateBrokerSyncFrequency(frequency: BrokerSyncFrequency) = updatePreference({ it.putString(KEY_BROKER_FREQ, frequency.name) }) { it.copy(brokerSyncFrequency = frequency) }
    override suspend fun updateAutoReconnect(enabled: Boolean) = updatePreference({ it.putBoolean(KEY_AUTO_RECONNECT, enabled) }) { it.copy(autoReconnectEnabled = enabled) }
    override suspend fun updateAppLock(enabled: Boolean) = withContext(ioDispatcher) {
        tokenStorage.setAppLockEnabled(enabled)
        _preferencesFlow.update { it.copy(appLockEnabled = enabled) }
    }
    override suspend fun updateBiometricOnResume(enabled: Boolean) = updatePreference({ it.putBoolean(KEY_BIO_RESUME, enabled) }) { it.copy(biometricPromptOnResume = enabled) }
    override suspend fun updateBiometricDestructiveOps(enabled: Boolean) = withContext(ioDispatcher) {
        tokenStorage.setDestructiveOpsBiometricGated(enabled)
        _preferencesFlow.update { it.copy(biometricGatingDestructiveOps = enabled) }
    }
    override suspend fun updateRemoteTerminal(enabled: Boolean) = updatePreference({ it.putBoolean(KEY_REMOTE_TERMINAL, enabled) }) { it.copy(remoteTerminalEnabled = enabled) }
    override suspend fun updateBrokerUrl(url: String) = updatePreference({ it.putString(KEY_BROKER_URL, url) }) { it.copy(brokerUrl = url) }

    override suspend fun resetDefaults() {
        withContext(ioDispatcher) {
            prefs.edit().clear().apply()
            tokenStorage.setDestructiveOpsBiometricGated(true)
            _preferencesFlow.value = SettingsPreferences()
        }
    }

    companion object {
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_LIVE_UPDATES = "pref_live_updates"
        private const val KEY_SAMPLING_INTERVAL = "pref_sampling_interval"
        private const val KEY_TEMPERATURE_UNIT = "pref_temperature_unit"
        private const val KEY_PROCESS_FILTER = "pref_process_filter"
        private const val KEY_SHOW_FAN = "pref_show_fan"
        private const val KEY_NOTIFICATIONS = "pref_notifications"
        private const val KEY_CPU_THRESHOLD = "pref_cpu_threshold"
        private const val KEY_RAM_THRESHOLD = "pref_ram_threshold"
        private const val KEY_BATTERY_THRESHOLD = "pref_battery_threshold"
        private const val KEY_BG_SYNC = "pref_bg_sync"
        private const val KEY_GIT_ALERTS = "pref_git_alerts"
        private const val KEY_BROKER_FREQ = "pref_broker_freq"
        private const val KEY_AUTO_RECONNECT = "pref_auto_reconnect"
        private const val KEY_BIO_RESUME = "pref_bio_resume"
        private const val KEY_BIO_OPS = "pref_bio_ops"
        private const val KEY_REMOTE_TERMINAL = "pref_remote_terminal"
        private const val KEY_BROKER_URL = "pref_broker_url"
    }
}
