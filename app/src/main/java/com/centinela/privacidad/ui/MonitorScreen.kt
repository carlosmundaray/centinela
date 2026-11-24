package com.centinela.privacidad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.centinela.privacidad.data.ActiveSensorSession
import com.centinela.privacidad.data.MonitorPreferences
import com.centinela.privacidad.data.SensorEventRecord
import com.centinela.privacidad.ui.theme.AmberContainer
import com.centinela.privacidad.ui.theme.AmberMic
import com.centinela.privacidad.ui.theme.CrimsonAlert
import com.centinela.privacidad.ui.theme.CrimsonContainer
import com.centinela.privacidad.ui.theme.CyanAccent
import com.centinela.privacidad.ui.theme.EmeraldContainer
import com.centinela.privacidad.ui.theme.EmeraldSafe
import com.centinela.privacidad.ui.theme.SlateBorder
import com.centinela.privacidad.ui.theme.SlateCard
import com.centinela.privacidad.ui.theme.TextSecondary

data class PermissionCheckState(
    val notificationsGranted: Boolean,
    val usageAccessGranted: Boolean,
    val accessibilityEnabled: Boolean
)

@Composable
fun MonitorScreen(
    isMonitoring: Boolean,
    activeSessions: List<ActiveSensorSession>,
    recentEvents: List<SensorEventRecord>,
    permissionState: PermissionCheckState,
    preferences: MonitorPreferences,
    onToggleMonitoring: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenPrivacyDashboard: () -> Unit,
    onOpenAppSettings: (String) -> Unit,
    onToggleAlerts: (Boolean) -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onViewAllHistory: () -> Unit
) {
    val activeCamera = activeSessions.firstOrNull { it.sensorType.isCamera }
    val activeMic = activeSessions.firstOrNull { !it.sensorType.isCamera }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Monitoring Control Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMonitoring) {
                        EmeraldContainer.copy(alpha = 0.35f)
                    } else {
                        SlateCard
                    }
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isMonitoring) EmeraldSafe.copy(alpha = 0.6f) else SlateBorder
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMonitoring) EmeraldSafe.copy(alpha = 0.2f)
                                    else SlateBorder.copy(alpha = 0.5f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isMonitoring) Icons.Rounded.GppGood else Icons.Rounded.Security,
                                contentDescription = null,
                                tint = if (isMonitoring) EmeraldSafe else TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMonitoring) "Vigilancia activa" else "Vigilancia detenida",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isMonitoring) {
                                    "Detectando al instante cualquier acceso a cámara o micrófono"
                                } else {
                                    "Activa el servicio para recibir alertas y registrar accesos"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onToggleMonitoring,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMonitoring) CrimsonAlert else EmeraldSafe,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isMonitoring) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMonitoring) "Detener vigilancia" else "Activar vigilancia en tiempo real",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PrivacyTip,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Privacidad verificable: esta app NO pide permiso de cámara ni micrófono.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent
                        )
                    }
                }
            }
        }

        // Live Sensor Status Row (Camera + Microphone)
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LiveSensorTile(
                    title = "Cámara",
                    icon = Icons.Rounded.CameraAlt,
                    activeSession = activeCamera,
                    activeColor = CrimsonAlert,
                    activeContainer = CrimsonContainer,
                    isMonitoring = isMonitoring,
                    modifier = Modifier.weight(1f)
                )
                LiveSensorTile(
                    title = "Micrófono",
                    icon = Icons.Rounded.Mic,
                    activeSession = activeMic,
                    activeColor = AmberMic,
                    activeContainer = AmberContainer,
                    isMonitoring = isMonitoring,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Permission & Accuracy Setup Checklist
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Precisión de detección",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Activa estos permisos para saber exactamente qué app encendió el sensor:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SetupRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Notificaciones de alerta",
                        subtitle = "Te avisa al instante si algo usa cámara o micro",
                        granted = permissionState.notificationsGranted,
                        actionLabel = "Permitir",
                        onClick = onRequestNotificationPermission
                    )
                    HorizontalDivider(
                        color = SlateBorder,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    SetupRow(
                        icon = Icons.Rounded.QueryStats,
                        title = "Acceso de uso (Recomendado)",
                        subtitle = "Detecta apps recientes y servicios en segundo plano",
                        granted = permissionState.usageAccessGranted,
                        actionLabel = "Activar",
                        onClick = onOpenUsageAccess
                    )
                    HorizontalDivider(
                        color = SlateBorder,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    SetupRow(
                        icon = Icons.Rounded.AccessibilityNew,
                        title = "Servicio de Accesibilidad",
                        subtitle = "Identifica en milisegundos la app abierta en pantalla",
                        granted = permissionState.accessibilityEnabled,
                        actionLabel = "Activar",
                        onClick = onOpenAccessibility
                    )

                    HorizontalDivider(
                        color = SlateBorder,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Alertas emergentes",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Enviar notificación cada vez que se active un sensor",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = preferences.alertNotificationsEnabled,
                            onCheckedChange = onToggleAlerts
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Iniciar al encender el teléfono",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Reanuda la vigilancia automáticamente tras reiniciar",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = preferences.autoStartOnBoot,
                            onCheckedChange = onToggleAutoStart
                        )
                    }
                }
            }
        }

        // Android Native Privacy Dashboard Shortcut
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Verificación oficial del sistema (Android 12+)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Si un proceso oculto en segundo plano activa el micrófono o la cámara, puedes contrastar el registro interno del sistema operativo aquí:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenPrivacyDashboard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Abrir Panel de Privacidad de Android",
                            color = CyanAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Recent Activity Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Actividad reciente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (recentEvents.isNotEmpty()) {
                    TextButton(onClick = onViewAllHistory) {
                        Text("Ver todo (${recentEvents.size})")
                    }
                }
            }
        }

        if (recentEvents.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Text(
                        text = "Aún no se han registrado accesos. Activa la vigilancia y abre tu cámara o graba una nota de voz para probarlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(recentEvents.take(5), key = { it.id }) { event ->
                SensorEventCard(
                    event = event,
                    onOpenAppSettings = onOpenAppSettings
                )
            }
        }
    }
}

@Composable
private fun LiveSensorTile(
    title: String,
    icon: ImageVector,
    activeSession: ActiveSensorSession?,
    activeColor: Color,
    activeContainer: Color,
    isMonitoring: Boolean,
    modifier: Modifier = Modifier
) {
    val isInUse = activeSession != null
    val containerColor = when {
        isInUse -> activeContainer.copy(alpha = 0.55f)
        else -> SlateCard
    }
    val borderColor = when {
        isInUse -> activeColor
        isMonitoring -> EmeraldSafe.copy(alpha = 0.45f)
        else -> SlateBorder
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isInUse) activeColor else if (isMonitoring) EmeraldSafe else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = when {
                        isInUse -> activeColor
                        isMonitoring -> EmeraldSafe.copy(alpha = 0.2f)
                        else -> SlateBorder
                    }
                ) {
                    Text(
                        text = when {
                            isInUse -> "EN USO"
                            isMonitoring -> "LIBRE"
                            else -> "OFF"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isInUse -> Color.White
                            isMonitoring -> EmeraldSafe
                            else -> TextSecondary
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = activeSession?.appName
                    ?: if (isMonitoring) "Sin actividad" else "Vigilancia pausada",
                style = MaterialTheme.typography.bodySmall,
                color = if (isInUse) Color.White else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (activeSession != null) {
                Text(
                    text = activeSession.sensorDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = activeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SetupRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) EmeraldSafe else AmberMic,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Activado",
                tint = EmeraldSafe,
                modifier = Modifier.size(22.dp)
            )
        } else {
            OutlinedButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AmberMic)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = AmberMic,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = actionLabel,
                    color = AmberMic,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
