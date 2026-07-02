package io.compartirarchivos.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType
import io.compartirarchivos.shared.fs.FileEntry
import java.awt.Dimension

fun main() = application {
    val state = remember { AppState() }

    DisposableEffect(Unit) {
        state.start()
        onDispose { state.stop() }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "CompartirArchivos — v0.1.0",
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        window.minimumSize = Dimension(900, 600)
        AppTheme {
            AppScaffold(state)
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    // Modo oscuro obligatorio (seccion 5)
    val dark = darkColorScheme(
        primary = MaterialTheme.colorScheme.primary,
        background = androidx.compose.ui.graphics.Color(0xFF101418),
        surface = androidx.compose.ui.graphics.Color(0xFF161B22),
    )
    MaterialTheme(colorScheme = dark) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    DEVICES("Dispositivos", Icons.Filled.Computer),
    SEND("Enviar", Icons.Filled.Send),
    EXPLORER("Explorador", Icons.Filled.Folder),
}

@Composable
private fun AppScaffold(state: AppState) {
    var tab by remember { mutableStateOf(Tab.DEVICES) }
    val self by state.selfProfile.collectAsState()
    val status by state.status.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("CompartirArchivos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Yo: ${self.name} (${self.host}:${self.port})", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.DEVICES -> DevicesScreen(state)
                Tab.SEND -> SendScreen(state)
                Tab.EXPLORER -> ExplorerScreen(state)
            }
            // Barra de estado
            Divider()
            Text(
                "Estado: $status",
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/* ---------------- Pestaña: Dispositivos + PIN ---------------- */

@Composable
private fun DevicesScreen(state: AppState) {
    val devices by state.devices.collectAsState()
    val pin by state.currentPin.collectAsState()
    val self by state.selfProfile.collectAsState()

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Panel PIN
        Card(modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tu PIN de emparejamiento", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    pin ?: "------",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text("Válido 60 s. Compártelo solo con quien quieras recibir archivos.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { state.refreshPin() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Generar nuevo PIN")
                }
            }
        }

        // Lista de dispositivos encontrados
        Card(modifier = Modifier.weight(1.4f)) {
            Column(Modifier.padding(16.dp)) {
                Text("Dispositivos en la red", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (devices.isEmpty()) {
                    Text("Buscando dispositivos...", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(devices, key = { it.id }) { d -> DeviceRow(d) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: DeviceProfile) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (d.type) {
            DeviceType.TV -> Icons.Filled.Tv
            DeviceType.MOBILE -> Icons.Filled.PhoneAndroid
            DeviceType.DESKTOP -> Icons.Filled.Computer
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(d.name, fontWeight = FontWeight.Medium)
            Text("${d.host}:${d.port} · ${d.type}", style = MaterialTheme.typography.bodySmall)
        }
        AssistChip(onClick = {}, label = { Text("Detectado") })
    }
}

/* ---------------- Pestaña: Enviar ---------------- */

@Composable
private fun SendScreen(state: AppState) {
    val devices by state.devices.collectAsState()
    val selected by state.filesToSend.collectAsState()
    var selectedDevice by remember { mutableStateOf<DeviceProfile?>(null) }
    var pin by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enviar archivos", style = MaterialTheme.typography.titleMedium)

        Text("Destino:", style = MaterialTheme.typography.bodyMedium)
        devices.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedDevice?.id == d.id, onClick = { selectedDevice = d })
                Text("${d.name} (${d.type})")
            }
        }
        if (devices.isEmpty()) Text("No hay dispositivos. Ve a la pestaña Dispositivos.", style = MaterialTheme.typography.bodySmall)

        Divider()
        Text("Archivos seleccionados: ${selected.size}")
        selected.forEach { Text("• ${it.name} (${it.size} bytes)", style = MaterialTheme.typography.bodySmall) }

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("PIN del destino") },
            modifier = Modifier.fillMaxWidth(0.4f),
        )

        Button(
            onClick = { selectedDevice?.let { state.sendTo(it, pin) } },
            enabled = selectedDevice != null && pin.length == 6 && selected.isNotEmpty(),
        ) {
            Icon(Icons.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Enviar ${selected.size} archivo(s)")
        }
    }
}

/* ---------------- Pestaña: Explorador ---------------- */

@Composable
private fun ExplorerScreen(state: AppState) {
    val path by state.explorerPath.collectAsState()
    val entries by state.explorerEntries.collectAsState()
    val selected by state.filesToSend.collectAsState()
    val selectedPaths = selected.map { it.path }.toSet()

    LaunchedEffect(Unit) { if (path == null) state.openExplorer(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Explorador de archivos", style = MaterialTheme.typography.titleMedium)
        Text("Ruta: ${path ?: "(home)"}", style = MaterialTheme.typography.bodySmall)
        Divider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(entries, key = { it.path }) { entry ->
                FileRow(
                    entry = entry,
                    isSelected = entry.path in selectedPaths,
                    onClick = {
                        if (entry.isDirectory) state.browseTo(entry)
                        else state.toggleSelectForSend(entry)
                    },
                    onToggle = { state.toggleSelectForSend(entry) },
                )
            }
        }
        Text("${selected.size} archivo(s) marcados para envío.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FileRow(entry: FileEntry, isSelected: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Send, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) { Text(entry.name) }
        if (!entry.isDirectory) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        }
        TextButton(onClick = onClick) { Text(if (entry.isDirectory) "Abrir" else "Marcar") }
    }
}
