package com.centinela.privacidad.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.centinela.privacidad.MainActivity
import com.centinela.privacidad.R
import com.centinela.privacidad.data.ActiveSensorSession

class SensorNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            "Estado de vigilancia",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene activo el monitoreo en tiempo real de cámara y micrófono"
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Alertas de cámara y micrófono",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisa al instante cuando una aplicación enciende la cámara o el micrófono"
        }

        notificationManager.createNotificationChannel(statusChannel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    fun buildStatusNotification(activeSessions: List<ActiveSensorSession>): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopServiceIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, SensorMonitorService::class.java).apply {
                action = SensorMonitorService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (activeSessions.isEmpty()) {
            "Centinela activo"
        } else {
            val sensors = activeSessions.map { it.sensorType.shortLabel }.distinct().joinToString(" + ")
            "EN USO: $sensors"
        }

        val text = if (activeSessions.isEmpty()) {
            "Cámara y micrófono libres • Vigilando en segundo plano"
        } else {
            activeSessions.joinToString(" | ") { session ->
                "${session.sensorType.displayName}: ${session.appName}"
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Detener", stopServiceIntent)
            .build()
    }

    fun updateStatusNotification(activeSessions: List<ActiveSensorSession>) {
        if (!PermissionAuditor.hasNotificationPermission(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(
                STATUS_NOTIFICATION_ID,
                buildStatusNotification(activeSessions)
            )
        }
    }

    fun postSensorAlert(session: ActiveSensorSession) {
        if (!PermissionAuditor.hasNotificationPermission(context)) return

        val openAppIntent = PendingIntent.getActivity(
            context,
            100 + (session.recordId % 1000).toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle("${session.sensorType.displayName} activado")
            .setContentText("${session.appName} • ${session.confidence.label}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "App detectada: ${session.appName}\n" +
                        "Sensor: ${session.sensorDetail}\n" +
                        "Detalle: ${session.attributionReason}"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        session.packageName?.let { pkg ->
            val appSettingsIntent = PendingIntent.getActivity(
                context,
                200 + (session.recordId % 1000).toInt(),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", pkg, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Ver permisos", appSettingsIntent)
        }

        val alertId = if (session.sensorType.isCamera) ALERT_CAMERA_ID else ALERT_MIC_ID
        runCatching {
            NotificationManagerCompat.from(context).notify(alertId, builder.build())
        }
    }

    companion object {
        const val CHANNEL_STATUS = "centinela_monitor_status"
        const val CHANNEL_ALERTS = "centinela_sensor_alerts"
        const val STATUS_NOTIFICATION_ID = 1001
        const val ALERT_CAMERA_ID = 2001
        const val ALERT_MIC_ID = 2002
    }
}
