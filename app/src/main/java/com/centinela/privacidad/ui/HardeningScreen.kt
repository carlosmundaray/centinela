package com.centinela.privacidad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.centinela.privacidad.hardening.BloatwareCatalog
import com.centinela.privacidad.hardening.BloatwarePackageStatus
import com.centinela.privacidad.hardening.HardeningSnapshot
import com.centinela.privacidad.hardening.PackageActiveState
import com.centinela.privacidad.hardening.PrivateDnsPreset
import com.centinela.privacidad.hardening.RecommendationLevel
import com.centinela.privacidad.hardening.SystemHardeningController
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

private enum class PackageFilter(val label: String) {
    ALL("Todos"),
    RECOMMENDED("Seguros"),
    ACTIVE("Activos"),
    DISABLED("Desactivados")
}

@Composable
fun HardeningScreen(
    snapshot: HardeningSnapshot?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelectDnsPreset: (PrivateDnsPreset) -> Unit,
    onToggleTelemetry: (Boolean) -> Unit,
    onToggleAdTracking: (Boolean) -> Unit,
    onToggleBackgroundScan: (Boolean) -> Unit,
    onApplyFullHardening: () -> Unit,
    onRevertHardening: () -> Unit,
    onTogglePackageDisabled: (packageName: String, disable: Boolean) -> Unit,
    onDisableAllRecommendedPackages: () -> Unit,
    onRestoreAllPackages: () -> Unit,
    onCopyText: (label: String, text: String, feedback: String) -> Unit,
    onOpenAppSettings: (String) -> Unit,
    onOpenWirelessSettings: () -> Unit,
    onOpenPrivacySettings: () -> Unit
) {
    var activeSection by remember { mutableIntStateOf(0) } // 0 = Ajustes y DNS, 1 = Paquetes
    var packageFilter by remember { mutableStateOf(PackageFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showRevertDialog by remember { mutableStateOf(false) }

    if (showRevertDialog) {
        AlertDialog(
            onDismissRequest = { showRevertDialog = false },
            title = { Text("Revertir ajustes de blindaje") },
            text = {
                Text(
                    "Se restaurarán el DNS Privado (Automático), telemetría, publicidad y escaneo Wi-Fi/Bluetooth a sus valores estándar. Los paquetes ocultos por Centinela también se reactivarán."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevertDialog = false
                        onRevertHardening()
                    }
                ) {
                    Text("Revertir", color = AmberMic)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevertDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (snapshot == null || isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val settings = snapshot.systemSettings
    val canEditSettings = snapshot.hasWriteSecureSettings || snapshot.isDeviceOwner
    val recommendedCount = snapshot.recommendedPackages.size
    val disabledRecommended = snapshot.disabledRecommendedCount

    val filteredPackages = remember(snapshot.detectedPackages, packageFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        snapshot.detectedPackages.filter { item ->
            val filterOk = when (packageFilter) {
                PackageFilter.ALL -> true
                PackageFilter.RECOMMENDED -> item.entry.recommendation == RecommendationLevel.RECOMMENDED
                PackageFilter.ACTIVE -> !item.isDisabled
                PackageFilter.DISABLED -> item.isDisabled
            }
            val queryOk = q.isEmpty() ||
                item.entry.displayName.lowercase().contains(q) ||
                item.resolvedAppName.lowercase().contains(q) ||
                item.entry.packageName.lowercase().contains(q) ||
                item.entry.category.label.lowercase().contains(q)
            filterOk && queryOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Screen title + refresh
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Blindaje del sistema",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "DNS privado, telemetría y rastreadores (100 % reversible)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Actualizar estado",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sub-section selector chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = activeSection == 0,
                onClick = { activeSection = 0 },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Ajustes y DNS") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = activeSection == 1,
                onClick = { activeSection = 1 },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text("Paquetes ($disabledRecommended/$recommendedCount)")
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activeSection == 0) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Overall Hardening Status Card
                item {
                    val checksDone = listOf(
                        settings.isPrivateDnsHardened,
                        settings.isTelemetryBlocked,
                        settings.isAdTrackingBlocked,
                        settings.isBackgroundScanningBlocked
                    ).count { it }
                    val allSystemDone = checksDone == 4
                    val accent = if (allSystemDone) EmeraldSafe else AmberMic
                    val bg = if (allSystemDone) {
                        EmeraldContainer.copy(alpha = 0.35f)
                    } else {
                        AmberContainer.copy(alpha = 0.22f)
                    }

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = bg),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (allSystemDone) Icons.Rounded.GppGood else Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (allSystemDone) {
                                            "Blindaje de sistema activo ($checksDone/4)"
                                        } else {
                                            "Blindaje parcial ($checksDone/4 ajustes)"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$disabledRecommended de $recommendedCount paquetes de rastreo seguros desactivados",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (allSystemDone) EmeraldSafe else TextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { checksDone / 4f },
                                color = accent,
                                trackColor = SlateBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onApplyFullHardening,
                                    enabled = canEditSettings,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldSafe,
                                        contentColor = Color(0xFF022C22)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Blindar todo", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showRevertDialog = true },
                                    enabled = canEditSettings,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, SlateBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Revertir")
                                }
                            }
                        }
                    }
                }

                // Permission Card (WRITE_SECURE_SETTINGS)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(
                            1.dp,
                            if (canEditSettings) EmeraldSafe.copy(alpha = 0.5f) else CyanAccent.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (canEditSettings) Icons.Rounded.CheckCircle else Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = if (canEditSettings) EmeraldSafe else CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (canEditSettings) {
                                        "Control directo de ajustes activo"
                                    } else {
                                        "Activar control con 1 toque (sin root)"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (canEditSettings) {
                                Text(
                                    text = "Centinela tiene permiso WRITE_SECURE_SETTINGS. Todos los interruptores de DNS Privado, telemetría, anuncios y escaneo funcionan al instante desde esta pantalla.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldSafe
                                )
                            } else {
                                Text(
                                    text = "Ejecuta este comando una sola vez por ADB (no requiere root ni quitar cuentas de Google) para activar o revertir los ajustes directamente desde Centinela:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = SlateBg,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SlateBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = SystemHardeningController.ADB_GRANT_WRITE_SECURE_SETTINGS_COMMAND,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = CyanAccent,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        onCopyText(
                                            "Permiso WRITE_SECURE_SETTINGS",
                                            SystemHardeningController.ADB_GRANT_WRITE_SECURE_SETTINGS_COMMAND,
                                            "Comando ADB copiado al portapapeles."
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanAccent,
                                        contentColor = Color(0xFF082F49)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copiar comando ADB", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Private DNS Card
                item {
                    val activePreset = settings.activePrivateDnsPreset
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = if (settings.isPrivateDnsHardened) EmeraldSafe else AmberMic,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "DNS Privado cifrado (DoT)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (settings.isPrivateDnsHardened) {
                                            "Activo: ${settings.privateDnsSpecifier}"
                                        } else {
                                            "Modo actual: ${settings.privateDnsMode ?: "automático (sin bloqueo)"}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (settings.isPrivateDnsHardened) EmeraldSafe else TextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            PrivateDnsPreset.entries.forEach { preset ->
                                val selected = activePreset == preset
                                Surface(
                                    color = if (selected) EmeraldSafe.copy(alpha = 0.14f) else SlateBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) EmeraldSafe else SlateBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable(enabled = canEditSettings) {
                                            onSelectDnsPreset(preset)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (selected) {
                                                Icons.Rounded.RadioButtonChecked
                                            } else {
                                                Icons.Rounded.RadioButtonUnchecked
                                            },
                                            contentDescription = null,
                                            tint = if (selected) EmeraldSafe else TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = preset.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (selected) Color.White else Color.White.copy(alpha = 0.9f)
                                            )
                                            if (preset.hostname != null) {
                                                Text(
                                                    text = preset.hostname,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = CyanAccent
                                                )
                                            }
                                            Text(
                                                text = preset.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Telemetry Toggle Card
                item {
                    HardeningSwitchCard(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "Bloquear telemetría y reportes",
                        subtitle = "Desactiva experiencia de usuario, permisos HiView, subida automática de APKs e informes de error (${settings.telemetryDisabledCount}/${settings.telemetryTotalCount} activos).",
                        checked = settings.isTelemetryBlocked,
                        enabled = canEditSettings,
                        onCheckedChange = onToggleTelemetry
                    )
                }

                // Advertising & OAID Toggle Card
                item {
                    HardeningSwitchCard(
                        icon = Icons.Rounded.AdsClick,
                        title = "Limitar anuncios y OAID",
                        subtitle = "Limita el identificador publicitario OAID, anuncios personalizados y ubicación para anuncios (${settings.adsDisabledCount}/${settings.adsTotalCount} activos).",
                        checked = settings.isAdTrackingBlocked,
                        enabled = canEditSettings,
                        onCheckedChange = onToggleAdTracking
                    )
                }

                // Background Wi-Fi / BLE Scan Toggle Card
                item {
                    HardeningSwitchCard(
                        icon = Icons.Rounded.WifiOff,
                        title = "Detener escaneo Wi-Fi / Bluetooth",
                        subtitle = "Impide que Android escanee redes Wi-Fi y balizas Bluetooth en segundo plano incluso con el Wi-Fi apagado.",
                        checked = settings.isBackgroundScanningBlocked,
                        enabled = canEditSettings,
                        onCheckedChange = onToggleBackgroundScan
                    )
                }

                // Quick shortcuts card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Accesos rápidos de privacidad",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Recomendado: borra también el ID de publicidad en Ajustes > Google > Anuncios.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = onOpenWirelessSettings,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Red / DNS", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onOpenPrivacySettings,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Privacidad", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Section 1: Tracker & Bloatware Packages (100 % reversible)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar rastreador o paquete...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                PackageFilter.entries.forEach { f ->
                    FilterChip(
                        selected = packageFilter == f,
                        onClick = { packageFilter = f },
                        label = { Text(f.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Batch Actions Banner
                item {
                    val activeRecommended = snapshot.recommendedPackages.filter { !it.isDisabled }
                    val disabledAny = snapshot.detectedPackages.filter { it.isDisabled }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${snapshot.disabledTotalCount} de ${snapshot.detectedPackages.size} paquetes detectados desactivados",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (snapshot.isDeviceOwner) {
                                    "Modo Propietario activo: puedes desactivar o reactivar cualquier paquete con 1 toque (100 % reversible)."
                                } else {
                                    "En modo estándar puedes auditar en vivo el estado de cada rastreador, abrir sus ajustes o copiar los comandos ADB reversibles (disable / enable)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            if (snapshot.isDeviceOwner) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = onDisableAllRecommendedPackages,
                                        enabled = activeRecommended.isNotEmpty(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EmeraldSafe,
                                            contentColor = Color(0xFF022C22)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Desactivar seguros", fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = onRestoreAllPackages,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Reactivar", maxLines = 1)
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = SlateBorder,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val script = BloatwareCatalog.buildBatchDisableScript(activeRecommended)
                                        if (script.isBlank()) {
                                            onCopyText(
                                                "ADB",
                                                "",
                                                "Todos los paquetes seguros ya están desactivados."
                                            )
                                        } else {
                                            onCopyText(
                                                "Desactivar rastreadores ADB",
                                                script,
                                                "Script ADB copiado (${activeRecommended.size} paquetes seguros)."
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "ADB Desactivar",
                                        color = CyanAccent,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val script = BloatwareCatalog.buildBatchEnableScript(disabledAny)
                                        if (script.isBlank()) {
                                            onCopyText(
                                                "ADB",
                                                "",
                                                "No hay paquetes desactivados para reactivar."
                                            )
                                        } else {
                                            onCopyText(
                                                "Reactivar paquetes ADB",
                                                script,
                                                "Script ADB de restauración copiado (${disabledAny.size} paquetes)."
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, EmeraldSafe.copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = null,
                                        tint = EmeraldSafe,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "ADB Reactivar",
                                        color = EmeraldSafe,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredPackages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ningún paquete de rastreo coincide con el filtro actual.",
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    items(filteredPackages, key = { it.entry.packageName }) { item ->
                        TrackerPackageCard(
                            item = item,
                            isDeviceOwner = snapshot.isDeviceOwner,
                            onToggleDisable = { disable ->
                                onTogglePackageDisabled(item.entry.packageName, disable)
                            },
                            onOpenAppSettings = { onOpenAppSettings(item.entry.packageName) },
                            onCopySingleAdb = {
                                val cmd = if (item.isDisabled) {
                                    if (item.state == PackageActiveState.UNINSTALLED_FOR_USER) {
                                        BloatwareCatalog.buildRestoreUninstalledCommand(item.entry.packageName)
                                    } else {
                                        BloatwareCatalog.buildEnableCommand(item.entry.packageName)
                                    }
                                } else {
                                    BloatwareCatalog.buildDisableCommand(item.entry.packageName)
                                }
                                val msg = if (item.isDisabled) {
                                    "Comando para reactivar ${item.entry.displayName} copiado."
                                } else {
                                    "Comando para desactivar ${item.entry.displayName} copiado."
                                }
                                onCopyText("Comando ADB ${item.entry.packageName}", cmd, msg)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardeningSwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val accent = if (checked) EmeraldSafe else AmberMic
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) EmeraldContainer.copy(alpha = 0.22f) else SlateCard
        ),
        border = BorderStroke(
            1.dp,
            if (checked) EmeraldSafe.copy(alpha = 0.65f) else SlateBorder
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
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
                    color = TextSecondary
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

@Composable
private fun TrackerPackageCard(
    item: BloatwarePackageStatus,
    isDeviceOwner: Boolean,
    onToggleDisable: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    onCopySingleAdb: () -> Unit
) {
    val isDisabled = item.isDisabled
    val statusColor = if (isDisabled) EmeraldSafe else CrimsonAlert
    val statusBg = if (isDisabled) EmeraldSafe.copy(alpha = 0.18f) else CrimsonContainer.copy(alpha = 0.65f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(
            1.dp,
            if (isDisabled) EmeraldSafe.copy(alpha = 0.4f) else SlateBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.entry.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.entry.packageName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = statusBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = item.state.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDisabled) EmeraldSafe else Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = SlateBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = item.entry.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = if (item.entry.recommendation == RecommendationLevel.RECOMMENDED) {
                        EmeraldContainer.copy(alpha = 0.55f)
                    } else {
                        AmberContainer.copy(alpha = 0.55f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.entry.recommendation.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.entry.recommendation == RecommendationLevel.RECOMMENDED) {
                            EmeraldSafe
                        } else {
                            AmberMic
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDeviceOwner) {
                    OutlinedButton(
                        onClick = { onToggleDisable(!isDisabled) },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, statusColor),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isDisabled) Icons.Rounded.Restore else Icons.Rounded.Block,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDisabled) "Reactivar" else "Desactivar",
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCopySingleAdb,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDisabled) "ADB Reactivar" else "ADB Desactivar",
                        color = CyanAccent,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }

                IconButton(onClick = onOpenAppSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Abrir ajustes de la app",
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}
