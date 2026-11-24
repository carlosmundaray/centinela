package com.centinela.privacidad.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.centinela.privacidad.data.SensorEventRecord
import com.centinela.privacidad.ui.theme.CrimsonAlert
import com.centinela.privacidad.ui.theme.TextSecondary

private enum class HistoryFilter(val label: String) {
    ALL("Todos"),
    CAMERA("Cámara"),
    MIC("Micrófono")
}

@Composable
fun HistoryScreen(
    events: List<SensorEventRecord>,
    onOpenAppSettings: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredEvents = remember(events, selectedFilter, searchQuery) {
        events.filter { event ->
            val matchesSensor = when (selectedFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.CAMERA -> event.sensorType.isCamera
                HistoryFilter.MIC -> !event.sensorType.isCamera
            }
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                event.appName.lowercase().contains(q) ||
                (event.packageName?.lowercase()?.contains(q) == true) ||
                event.sensorDetail.lowercase().contains(q)
            matchesSensor && matchesQuery
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Borrar historial") },
            text = { Text("¿Deseas eliminar todos los eventos registrados de cámara y micrófono?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Borrar", color = CrimsonAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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
            Text(
                text = "Historial de accesos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (events.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Limpiar historial",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar app o paquete...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (events.isEmpty()) {
                        "No hay eventos registrados todavía."
                    } else {
                        "No se encontraron coincidencias para tu filtro."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredEvents, key = { it.id }) { event ->
                    SensorEventCard(
                        event = event,
                        onOpenAppSettings = onOpenAppSettings
                    )
                }
            }
        }
    }
}
