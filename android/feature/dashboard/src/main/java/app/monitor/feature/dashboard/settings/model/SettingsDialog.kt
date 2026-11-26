package app.monitor.feature.dashboard.settings.model

sealed interface SettingsDialog {
    data object SignOutConfirm : SettingsDialog
    data object UnpairConfirm : SettingsDialog
    data object ProfileDetailsSheet : SettingsDialog
    data object ManagePairingSheet : SettingsDialog
    data object AppearancePicker : SettingsDialog
    data object SamplingIntervalPicker : SettingsDialog
    data object TemperatureUnitPicker : SettingsDialog
    data object ProcessFilterPicker : SettingsDialog
    data object BrokerFrequencyPicker : SettingsDialog
    data object TermsViewer : SettingsDialog
    data object PrivacyPolicyViewer : SettingsDialog
    data object AboutAppDialog : SettingsDialog
}
