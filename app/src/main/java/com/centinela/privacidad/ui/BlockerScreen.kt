package com.centinela.privacidad.ui

import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centinela.privacidad.blocker.BlockerStatus
import com.centinela.privacidad.blocker.ProtectionTier
import com.centinela.privacidad.blocker.SensorBlockerController
import com.centinela.privacidad.ui.theme.AmberContainer
import com.centinela.privacidad.ui.theme.AmberMic
import com.centinela.privacidad.ui.theme.CrimsonAlert
import com.centinela.privacidad.ui.theme.CrimsonContainer
import com.centinela.privacidad.ui.theme.CyanAccent
import com.centinela.privacidad.ui.theme.EmeraldContainer
import com.centinela.privacidad.ui.theme.EmeraldSafe
import com.centinela.privacidad.ui.theme.SlateBg
import com.centinela.privacidad.ui.theme.SlateBorder
import com.centinela.privacidad.ui.theme.SlateCard
import com.centinela.privacidad.ui.theme.TextSecondary

@Composable
fun BlockerScreen(
    status: BlockerStatus,
    onToggleCameraBlock: (Boolean) -> Unit,
    onToggleMicBlock: (Boolean) -> Unit,
    onCopyAdbCommand: () -> Unit,
    onRequestLegacyDeviceAdmin: () -> Unit,
    onClearDeviceOwner: () -> Unit,
    onOpenPrivacySettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    var showRemoveOwnerDialog by remember { mutableStateOf(false) }

    if (showRemoveOwnerDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveOwnerDialog = false },
            title = { Text("Desactivar Modo Propietario") },
            text = {
                Text(
                    "Se desbloquearán la cámara y el micrófono y se quitará el rol de Propietario del Dispositivo para que puedas desinstalar la app normalmente si lo deseas."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveOwnerDialog = false
                        onClearDeviceOwner()
                    }
                ) {
                    Text("Desactivar", color = CrimsonAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveOwnerDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Current Protection Tier Banner
        item {
            val isElevated = status.isDeviceOwner
            val accent = if (isElevated) EmeraldSafe else AmberMic
            val bg = if (isElevated) EmeraldContainer.copy(alpha = 0.35f) else AmberContainer.copy(alpha = 0.25f)

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isElevated) Icons.Rounded.VerifiedUser else Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isElevated) "Bloqueo total de sistema activo" else "Nivel de bloqueo actual",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = status.protectionTier.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isElevated) EmeraldSafe else AmberMic
                        )
                    }
                }
            }
        }

        // Master Kill-Switch Cards
        item {
            BlockSwitchCard(
                icon = Icons.Rounded.NoPhotography,
                title = "Bloquear Cámara",
                subtitle = when {
                    status.isCameraBlocked ->
                        "BLOQUEADA a nivel de sistema (ninguna app, ni del sistema, puede abrirla)."
                    status.canBlockCameraProgrammatically ->
                        "Actívalo para deshabilitar físicamente todas las cámaras para todas las apps, incluidas apps del sistema."
                    else ->
                        "En Android 10+ requiere activar el Modo Propietario abajo (1 comando ADB, sin root) o usar el interruptor nativo de Android 12+."
                },
                checked = status.isCameraBlocked,
                enabled = status.canBlockCameraProgrammatically,
                activeColor = CrimsonAlert,
                onCheckedChange = onToggleCameraBlock
            )
        }

        item {
            BlockSwitchCard(
                icon = Icons.Rounded.MicOff,
                title = "Bloquear Micrófono",
                subtitle = when {
                    status.isDeviceOwner && status.isMicBlocked ->
                        "BLOQUEADO a nivel de sistema (DISALLOW_UNMUTE_MICROPHONE). Ninguna app, ni del sistema, puede reactivarlo."
                    status.isDeviceOwner ->
                        "Silencia el micrófono a nivel de sistema e impide que cualquier app (incluso de sistema) quite el silencio."
                    status.isMicBlocked ->
                        "Silenciado activo en modo estándar (AudioManager + re-bloqueo al detectar grabación). Activa Modo Propietario para bloqueo absoluto contra apps del sistema."
                    else ->
                        "Silencia el micrófono y vuelve a silenciarlo automáticamente si otra app intenta grabar. Con Modo Propietario el bloqueo es total a nivel de sistema."
                },
                checked = status.isMicBlocked,
                enabled = true,
                activeColor = AmberMic,
                onCheckedChange = onToggleMicBlock
            )
        }

        // Device Owner / System-Level Blocking Guide
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bloqueo total incluso para apps de sistema (sin root)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (status.isDeviceOwner) {
                        Text(
                            text = "Modo Propietario (Device Owner) activo. Ahora puedes bloquear la cámara y el micrófono globalmente para TODAS las apps (incluidas las del fabricante/sistema) y revocar permisos por app desde la pestaña Auditoría.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldSafe
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showRemoveOwnerDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Desactivar Modo Propietario",
                                color = CrimsonAlert,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = "Por seguridad de Android, una app normal no puede desactivar el hardware de cámara o micrófono a apps del sistema. Si otorgas a Centinela el rol oficial de Propietario del Dispositivo (Device Owner) por ADB, Android desbloquea:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Bloqueo HAL de cámara para TODAS las apps (incluidas de sistema).\n• Bloqueo de micrófono inalterable (DISALLOW_UNMUTE_MICROPHONE).\n• Revocación/bloqueo de permisos por app desde Auditoría.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = SlateBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = SensorBlockerController.ADB_DEVICE_OWNER_COMMAND,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = CyanAccent,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = onCopyAdbCommand,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanAccent,
                                contentColor = Color(0xFF082F49)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copiar comando ADB", fontWeight = FontWeight.Bold)
                        }

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            status.protectionTier != ProtectionTier.DEVICE_ADMIN_LEGACY
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onRequestLegacyDeviceAdmin,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Activar Administrador de Dispositivo (Android 8-9)")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nota: Android pide quitar temporalmente cuentas de Google/usuario antes de ejecutar el comando dpm (luego puedes volver a iniciar sesión).",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Native Android 12+ Hardware Kill Switches (No PC / No ADB)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = EmeraldSafe,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bloqueo nativo de Android (sin PC ni ADB)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "En Android 12 o superior, el propio sistema incluye interruptores maestros que cortan cámara y micrófono incluso a apps del sistema (entregan pantalla negra y silencio absoluto):",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. En Ajustes de Privacidad, desactiva «Acceso a la cámara» y «Acceso al micrófono» (también puedes agregarlos a tu barra rápida superior).\n2. O activa «Sensores desactivados» dentro de Opciones de Desarrollador → Mosaicos de ajustes rápidos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onOpenPrivacySettings,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EmeraldSafe.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = EmeraldSafe,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Abrir Ajustes de Privacidad",
                            color = EmeraldSafe,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onOpenDeveloperOptions,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeveloperMode,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Abrir Opciones de Desarrollador",
                            color = TextSecondary
                        )
                    }

                    HorizontalDivider(
                        color = SlateBorder,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Text(
                        text = "Tip rápido: desliza la barra superior de Android, toca el lápiz de editar y añade el botón «Bloquear Cám/Mic» de Centinela para bloquear o desbloquear con un toque desde cualquier pantalla.",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockSwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    activeColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) activeColor.copy(alpha = 0.14f) else SlateCard
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) activeColor else SlateBorder
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (checked) activeColor.copy(alpha = 0.25f)
                        else SlateBorder.copy(alpha = 0.45f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (checked) activeColor else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (checked) Color.White else TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
