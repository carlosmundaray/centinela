package com.centinela.privacidad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.centinela.privacidad.data.AttributionConfidence
import com.centinela.privacidad.data.SensorEventRecord
import com.centinela.privacidad.ui.theme.AmberContainer
import com.centinela.privacidad.ui.theme.AmberMic
import com.centinela.privacidad.ui.theme.CrimsonAlert
import com.centinela.privacidad.ui.theme.CrimsonContainer
import com.centinela.privacidad.ui.theme.CyanAccent
import com.centinela.privacidad.ui.theme.EmeraldSafe
import com.centinela.privacidad.ui.theme.SlateBorder
import com.centinela.privacidad.ui.theme.SlateCard
import com.centinela.privacidad.ui.theme.TextSecondary

@Composable
fun SensorEventCard(
    event: SensorEventRecord,
    onOpenAppSettings: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appIcon = rememberAppIcon(context, event.packageName)
    val sensorColor = if (event.sensorType.isCamera) CrimsonAlert else AmberMic
    val sensorBg = if (event.sensorType.isCamera) CrimsonContainer else AmberContainer

    val confidenceColor = when (event.confidence) {
        AttributionConfidence.HIGH -> EmeraldSafe
        AttributionConfidence.MEDIUM -> CyanAccent
        AttributionConfidence.LOW -> AmberMic
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(
            width = 1.dp,
            color = if (event.isActive) sensorColor.copy(alpha = 0.7f) else SlateBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateBorder.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = event.appName,
                            modifier = Modifier.size(34.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = event.appName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (event.isActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = sensorColor,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "EN USO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (!event.packageName.isNullOrBlank()) {
                        Text(
                            text = event.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!event.packageName.isNullOrBlank()) {
                    IconButton(onClick = { onOpenAppSettings(event.packageName) }) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Abrir ajustes de la app",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = sensorBg.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (event.sensorType.isCamera) {
                                Icons.Rounded.CameraAlt
                            } else {
                                Icons.Rounded.Mic
                            },
                            contentDescription = null,
                            tint = sensorColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = event.sensorDetail,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = confidenceColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = event.confidence.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = confidenceColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = event.attributionReason,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Inicio: ${Formatters.formatTimestamp(event.startTimeMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = if (event.isActive) {
                        "Activo ahora"
                    } else {
                        "Duración: ${Formatters.formatDuration(event.durationMs())}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.isActive) sensorColor else TextSecondary,
                    fontWeight = if (event.isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
