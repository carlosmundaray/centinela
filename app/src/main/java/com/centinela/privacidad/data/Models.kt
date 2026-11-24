package com.centinela.privacidad.data

enum class SensorType(val displayName: String, val shortLabel: String) {
    CAMERA_FRONT("Cámara frontal", "Cámara"),
    CAMERA_BACK("Cámara trasera", "Cámara"),
    CAMERA_EXTERNAL("Cámara externa", "Cámara"),
    MICROPHONE("Micrófono", "Micrófono");

    val isCamera: Boolean
        get() = this != MICROPHONE
}

enum class AttributionConfidence(val label: String) {
    HIGH("App en pantalla"),
    MEDIUM("Actividad reciente"),
    LOW("Segundo plano / Desconocida")
}

data class AppAttribution(
    val packageName: String?,
    val appName: String,
    val confidence: AttributionConfidence,
    val reason: String
)

data class SensorEventRecord(
    val id: Long,
    val sensorType: SensorType,
    val sensorDetail: String,
    val packageName: String?,
    val appName: String,
    val confidence: AttributionConfidence,
    val attributionReason: String,
    val startTimeMs: Long,
    val endTimeMs: Long?
) {
    val isActive: Boolean
        get() = endTimeMs == null

    fun durationMs(nowMs: Long = System.currentTimeMillis()): Long {
        val end = endTimeMs ?: nowMs
        return (end - startTimeMs).coerceAtLeast(0L)
    }
}

data class ActiveSensorSession(
    val sessionKey: String,
    val recordId: Long,
    val sensorType: SensorType,
    val sensorDetail: String,
    val packageName: String?,
    val appName: String,
    val confidence: AttributionConfidence,
    val attributionReason: String,
    val startTimeMs: Long
)

data class AuditedAppInfo(
    val packageName: String,
    val appName: String,
    val hasCameraPermission: Boolean,
    val hasMicPermission: Boolean,
    val isSystemApp: Boolean,
    val cameraBlockedByPolicy: Boolean = false,
    val micBlockedByPolicy: Boolean = false
)

data class MonitorPreferences(
    val autoStartOnBoot: Boolean = true,
    val alertNotificationsEnabled: Boolean = true,
    val wasMonitoringEnabled: Boolean = false
)
