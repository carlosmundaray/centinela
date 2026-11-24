package com.centinela.privacidad.ui

import android.Manifest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.centinela.privacidad.data.AuditedAppInfo
import com.centinela.privacidad.ui.theme.AmberContainer
import com.centinela.privacidad.ui.theme.AmberMic
import com.centinela.privacidad.ui.theme.CrimsonAlert
import com.centinela.privacidad.ui.theme.CrimsonContainer
import com.centinela.privacidad.ui.theme.EmeraldSafe
import com.centinela.privacidad.ui.theme.SlateBorder
import com.centinela.privacidad.ui.theme.SlateCard
import com.centinela.privacidad.ui.theme.TextSecondary

private enum class AuditFilter(val label: String) {
    ALL("Todas"),
    CAMERA("Cámara"),
    MIC("Micrófono")
}

@Composable
fun AuditScreen(
    auditedApps: List<AuditedAppInfo>,
    isLoading: Boolean,
    isDeviceOwner: Boolean,
    onRefresh: () -> Unit,
    onOpenAppSettings: (String) -> Unit,
    onToggleAppPermission: (packageName: String, permission: String, block: Boolean) -> Unit
) {
    var filter by remember { mutableStateOf(AuditFilter.ALL) }
    var includeSystemApps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val userAppsCount = remember(auditedApps) { auditedApps.count { !it.isSystemApp } }
    val cameraCount = remember(auditedApps, includeSystemApps) {
        auditedApps.count {
            (includeSystemApps || !it.isSystemApp) &&
                (it.hasCameraPermission || it.cameraBlockedByPolicy)
        }
    }
    val micCount = remember(auditedApps, includeSystemApps) {
        auditedApps.count {
            (includeSystemApps || !it.isSystemApp) &&
                (it.hasMicPermission || it.micBlockedByPolicy)
        }
    }

    val filteredApps = remember(auditedApps, filter, includeSystemApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        auditedApps.filter { app ->
            val systemOk = includeSystemApps || !app.isSystemApp
            val filterOk = when (filter) {
                AuditFilter.ALL -> true
                AuditFilter.CAMERA -> app.hasCameraPermission || app.cameraBlockedByPolicy
                AuditFilter.MIC -> app.hasMicPermission || app.micBlockedByPolicy
            }
            val queryOk = q.isEmpty() ||
                app.appName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            systemOk && filterOk && queryOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Auditoría de permisos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$cameraCount cámara • $micCount micrófono ($userAppsCount apps de usuario)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Actualizar auditoría",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filtrar apps (incluye sistema)...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AuditFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(item.label) }
                )
            }
            FilterChip(
                selected = includeSystemApps,
                onClick = { includeSystemApps = !includeSystemApps },
                label = { Text("Sistema") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            filteredApps.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ninguna app coincide con el filtro actual.",
                        color = TextSecondary
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AuditedAppCard(
                            app = app,
                            isDeviceOwner = isDeviceOwner,
                            onOpenAppSettings = { onOpenAppSettings(app.packageName) },
                            onToggleCamera = { block ->
                                onToggleAppPermission(
                                    app.packageName,
                                    Manifest.permission.CAMERA,
                                    block
                                )
                            },
                            onToggleMic = { block ->
                                onToggleAppPermission(
                                    app.packageName,
                                    Manifest.permission.RECORD_AUDIO,
                                    block
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditedAppCard(
    app: AuditedAppInfo,
    isDeviceOwner: Boolean,
    onOpenAppSettings: () -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onToggleMic: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val icon = rememberAppIcon(context, app.packageName)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateBorder.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = app.appName,
                            modifier = Modifier.size(34.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (app.hasCameraPermission || app.cameraBlockedByPolicy) {
                            PermissionBadge(
                                label = if (app.cameraBlockedByPolicy) "Cámara bloqueada" else "Cámara",
                                icon = Icons.Rounded.CameraAlt,
                                tint = if (app.cameraBlockedByPolicy) EmeraldSafe else CrimsonAlert,
                                bg = if (app.cameraBlockedByPolicy) {
                                    EmeraldSafe.copy(alpha = 0.18f)
                                } else {
                                    CrimsonContainer.copy(alpha = 0.65f)
                                }
                            )
                        }
                        if (app.hasMicPermission || app.micBlockedByPolicy) {
                            PermissionBadge(
                                label = if (app.micBlockedByPolicy) "Micro bloqueado" else "Micrófono",
                                icon = Icons.Rounded.Mic,
                                tint = if (app.micBlockedByPolicy) EmeraldSafe else AmberMic,
                                bg = if (app.micBlockedByPolicy) {
                                    EmeraldSafe.copy(alpha = 0.18f)
                                } else {
                                    AmberContainer.copy(alpha = 0.65f)
                                }
                            )
                        }
                        if (app.isSystemApp) {
                            Surface(
                                color = SlateBorder,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Sistema",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onOpenAppSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Revocar o configurar permisos",
                        tint = TextSecondary
                    )
                }
            }

            if (isDeviceOwner &&
                (app.hasCameraPermission || app.cameraBlockedByPolicy || app.hasMicPermission || app.micBlockedByPolicy)
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (app.hasCameraPermission || app.cameraBlockedByPolicy) {
                        val isBlocked = app.cameraBlockedByPolicy
                        OutlinedButton(
                            onClick = { onToggleCamera(!isBlocked) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isBlocked) EmeraldSafe else CrimsonAlert
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isBlocked) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Block,
                                contentDescription = null,
                                tint = if (isBlocked) EmeraldSafe else CrimsonAlert,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBlocked) "Permitir Cám" else "Bloquear Cám",
                                color = if (isBlocked) EmeraldSafe else CrimsonAlert,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    if (app.hasMicPermission || app.micBlockedByPolicy) {
                        val isBlocked = app.micBlockedByPolicy
                        OutlinedButton(
                            onClick = { onToggleMic(!isBlocked) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isBlocked) EmeraldSafe else AmberMic
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isBlocked) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Block,
                                contentDescription = null,
                                tint = if (isBlocked) EmeraldSafe else AmberMic,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBlocked) "Permitir Mic" else "Bloquear Mic",
                                color = if (isBlocked) EmeraldSafe else AmberMic,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBadge(
    label: String,
    icon: ImageVector,
    tint: Color,
    bg: Color
) {
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}
