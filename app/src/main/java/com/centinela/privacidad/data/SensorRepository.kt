package com.centinela.privacidad.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SensorRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = SensorDatabaseHelper(appContext)
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<ActiveSensorSession>>(emptyList())
    val activeSessions: StateFlow<List<ActiveSensorSession>> = _activeSessions.asStateFlow()

    private val _events = MutableStateFlow<List<SensorEventRecord>>(emptyList())
    val events: StateFlow<List<SensorEventRecord>> = _events.asStateFlow()

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<MonitorPreferences> = _preferences.asStateFlow()

    init {
        reloadEvents()
    }

    private fun loadPreferences(): MonitorPreferences {
        return MonitorPreferences(
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START, true),
            alertNotificationsEnabled = prefs.getBoolean(KEY_ALERT_NOTIFICATIONS, true),
            wasMonitoringEnabled = prefs.getBoolean(KEY_WAS_MONITORING, false)
        )
    }

    @Synchronized
    fun recordSensorStart(
        sessionKey: String,
        sensorType: SensorType,
        sensorDetail: String,
        attribution: AppAttribution,
        startTimeMs: Long = System.currentTimeMillis()
    ): ActiveSensorSession {
        val existing = _activeSessions.value.find { it.sessionKey == sessionKey }
        if (existing != null) return existing

        val recordId = db.insertEvent(sensorType, sensorDetail, attribution, startTimeMs)
        val session = ActiveSensorSession(
            sessionKey = sessionKey,
            recordId = recordId,
            sensorType = sensorType,
            sensorDetail = sensorDetail,
            packageName = attribution.packageName,
            appName = attribution.appName,
            confidence = attribution.confidence,
            attributionReason = attribution.reason,
            startTimeMs = startTimeMs
        )
        _activeSessions.value = _activeSessions.value + session
        reloadEvents()
        return session
    }

    @Synchronized
    fun recordSensorStop(sessionKey: String, endTimeMs: Long = System.currentTimeMillis()) {
        val current = _activeSessions.value
        val target = current.find { it.sessionKey == sessionKey } ?: return
        db.finishEvent(target.recordId, endTimeMs)
        _activeSessions.value = current.filterNot { it.sessionKey == sessionKey }
        reloadEvents()
    }

    @Synchronized
    fun finalizeAllActiveSessions(endTimeMs: Long = System.currentTimeMillis()) {
        db.finalizeOrphanedEvents(endTimeMs)
        _activeSessions.value = emptyList()
        reloadEvents()
    }

    @Synchronized
    fun clearHistory() {
        db.clearAllEvents()
        _activeSessions.value = emptyList()
        reloadEvents()
    }

    @Synchronized
    fun reloadEvents() {
        _events.value = db.getRecentEvents()
    }

    fun setMonitoringRunning(running: Boolean) {
        _isMonitoring.value = running
    }

    fun setDesiredMonitoringState(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAS_MONITORING, enabled).apply()
        _preferences.value = _preferences.value.copy(wasMonitoringEnabled = enabled)
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
        _preferences.value = _preferences.value.copy(autoStartOnBoot = enabled)
    }

    fun setAlertNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALERT_NOTIFICATIONS, enabled).apply()
        _preferences.value = _preferences.value.copy(alertNotificationsEnabled = enabled)
    }

    companion object {
        private const val PREFS_NAME = "centinela_prefs"
        private const val KEY_AUTO_START = "auto_start_on_boot"
        private const val KEY_ALERT_NOTIFICATIONS = "alert_notifications"
        private const val KEY_WAS_MONITORING = "was_monitoring_enabled"
    }
}
