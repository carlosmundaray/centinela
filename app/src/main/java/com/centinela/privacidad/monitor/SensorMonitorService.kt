package com.centinela.privacidad.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.centinela.privacidad.CentinelaApp
import com.centinela.privacidad.blocker.SensorBlockerController
import com.centinela.privacidad.data.SensorRepository
import com.centinela.privacidad.data.SensorType

class SensorMonitorService : Service() {

    private lateinit var repository: SensorRepository
    private lateinit var attributionResolver: AppAttributionResolver
    private lateinit var notificationHelper: SensorNotificationHelper
    private lateinit var blockerController: SensorBlockerController

    private var cameraManager: CameraManager? = null
    private var audioManager: AudioManager? = null
    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null

    private val activeCameraIds = mutableSetOf<String>()
    private val activeAudioSessionIds = mutableSetOf<Int>()

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            synchronized(activeCameraIds) {
                if (!activeCameraIds.add(cameraId)) return
            }
            val sensorType = resolveCameraType(cameraId)
            val detail = "${sensorType.displayName} (ID $cameraId)"
            val attribution = attributionResolver.resolve(sensorType)
            val session = repository.recordSensorStart(
                sessionKey = cameraSessionKey(cameraId),
                sensorType = sensorType,
                sensorDetail = detail,
                attribution = attribution
            )
            notificationHelper.updateStatusNotification(repository.activeSessions.value)
            if (repository.preferences.value.alertNotificationsEnabled) {
                notificationHelper.postSensorAlert(session)
            }
        }

        override fun onCameraAvailable(cameraId: String) {
            val removed = synchronized(activeCameraIds) {
                activeCameraIds.remove(cameraId)
            }
            if (!removed) return
            repository.recordSensorStop(cameraSessionKey(cameraId))
            notificationHelper.updateStatusNotification(repository.activeSessions.value)
        }
    }

    private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            val currentConfigsById = configs.associateBy { it.clientAudioSessionId }
            val currentIds = currentConfigsById.keys

            val startedIds: List<Int>
            val stoppedIds: List<Int>

            synchronized(activeAudioSessionIds) {
                startedIds = currentIds.filter { it !in activeAudioSessionIds }
                stoppedIds = activeAudioSessionIds.filter { it !in currentIds }
                activeAudioSessionIds.addAll(startedIds)
                activeAudioSessionIds.removeAll(stoppedIds.toSet())
            }

            startedIds.forEach { sessionId ->
                val config = currentConfigsById[sessionId] ?: return@forEach
                blockerController.enforceMicGuardIfActive()
                val sourceLabel = describeAudioSource(config)
                val attribution = attributionResolver.resolve(SensorType.MICROPHONE)
                val session = repository.recordSensorStart(
                    sessionKey = audioSessionKey(sessionId),
                    sensorType = SensorType.MICROPHONE,
                    sensorDetail = sourceLabel,
                    attribution = attribution
                )
                if (repository.preferences.value.alertNotificationsEnabled) {
                    notificationHelper.postSensorAlert(session)
                }
            }

            stoppedIds.forEach { sessionId ->
                repository.recordSensorStop(audioSessionKey(sessionId))
            }

            if (startedIds.isNotEmpty() || stoppedIds.isNotEmpty()) {
                notificationHelper.updateStatusNotification(repository.activeSessions.value)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as CentinelaApp).repository
        attributionResolver = AppAttributionResolver(this)
        notificationHelper = SensorNotificationHelper(this)
        blockerController = SensorBlockerController(this)
        notificationHelper.ensureChannels()

        callbackThread = HandlerThread("CentinelaSensorCallbacks").apply { start() }
        callbackHandler = Handler(callbackThread!!.looper)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            repository.setDesiredMonitoringState(false)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = notificationHelper.buildStatusNotification(repository.activeSessions.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SensorNotificationHelper.STATUS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(SensorNotificationHelper.STATUS_NOTIFICATION_ID, notification)
        }

        if (!repository.isMonitoring.value) {
            repository.finalizeAllActiveSessions()
            registerSensorCallbacks()
            repository.setMonitoringRunning(true)
            repository.setDesiredMonitoringState(true)
        }

        return START_STICKY
    }

    private fun registerSensorCallbacks() {
        val handler = callbackHandler ?: return
        runCatching {
            cameraManager?.registerAvailabilityCallback(cameraCallback, handler)
        }
        runCatching {
            audioManager?.registerAudioRecordingCallback(audioRecordingCallback, handler)
            // Capture any recording already active when monitoring starts
            val existing = audioManager?.activeRecordingConfigurations.orEmpty()
            if (existing.isNotEmpty()) {
                handler.post { audioRecordingCallback.onRecordingConfigChanged(existing) }
            }
        }
    }

    private fun unregisterSensorCallbacks() {
        runCatching {
            cameraManager?.unregisterAvailabilityCallback(cameraCallback)
        }
        runCatching {
            audioManager?.unregisterAudioRecordingCallback(audioRecordingCallback)
        }
    }

    private fun resolveCameraType(cameraId: String): SensorType {
        return runCatching {
            val chars = cameraManager?.getCameraCharacteristics(cameraId)
            when (chars?.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> SensorType.CAMERA_FRONT
                CameraCharacteristics.LENS_FACING_BACK -> SensorType.CAMERA_BACK
                CameraCharacteristics.LENS_FACING_EXTERNAL -> SensorType.CAMERA_EXTERNAL
                else -> SensorType.CAMERA_BACK
            }
        }.getOrDefault(SensorType.CAMERA_BACK)
    }

    private fun describeAudioSource(config: AudioRecordingConfiguration): String {
        val base = when (config.clientAudioSource) {
            MediaRecorder.AudioSource.MIC -> "Micrófono estándar"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "Llamada / VoIP"
            MediaRecorder.AudioSource.CAMCORDER -> "Audio de cámara"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "Reconocimiento de voz"
            MediaRecorder.AudioSource.UNPROCESSED -> "Audio sin procesar"
            else -> "Grabación de audio"
        }
        val silenced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && config.isClientSilenced) {
            " (silenciado por Android)"
        } else {
            ""
        }
        return "$base$silenced"
    }

    override fun onDestroy() {
        unregisterSensorCallbacks()
        synchronized(activeCameraIds) { activeCameraIds.clear() }
        synchronized(activeAudioSessionIds) { activeAudioSessionIds.clear() }
        repository.finalizeAllActiveSessions()
        repository.setMonitoringRunning(false)
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.centinela.privacidad.ACTION_START"
        const val ACTION_STOP = "com.centinela.privacidad.ACTION_STOP"

        private fun cameraSessionKey(cameraId: String) = "cam_$cameraId"
        private fun audioSessionKey(audioSessionId: Int) = "mic_$audioSessionId"

        fun start(context: Context) {
            val intent = Intent(context, SensorMonitorService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SensorMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
