package io.compartirarchivos.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tv
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
import io.compartirarchivos.shared.fs.FileEntry
import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType
import java.awt.Dimension

fun main() = application {
    val state = remember { AppState() }

    DisposableEffect(Unit) {
        state.start()
        onDispose { state.stop() }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "CompartirArchivos — v0.5.0",
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
    val dark = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF38BDF8),
        background = androidx.compose.ui.graphics.Color(0xFF101418),
        surface = androidx.compose.ui.graphics.Color(0xFF161B22),
    )
    MaterialTheme(colorScheme = dark) {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
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
                Tab.DEVICES -> DevicesScreen(state) { device ->
                    state.setSendTarget(device)
                    tab = Tab.SEND
                }
                Tab.SEND -> SendScreen(state)
                Tab.EXPLORER -> ExplorerScreen(state)
            }
            HorizontalDivider()
            Text(
                "Estado: $status",
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/* ---------------- Dispositivos + PIN + carpeta descarga ---------------- */

@Composable
private fun DevicesScreen(state: AppState, onSelect: (DeviceProfile) -> Unit) {
    val devices by state.devices.collectAsState()
    val pin by state.currentPin.collectAsState()
    val target by state.sendTarget.collectAsState()
    val downloadFolder by state.downloadFolder.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Panel PIN
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tu PIN", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pin ?: "------",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Válido 60 s", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { state.refreshPin() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp)); Text("Nuevo PIN")
                    }
                }
            }

            // Dispositivos detectados automáticamente (escáner de subred)
            Card(modifier = Modifier.weight(1.4f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Dispositivos en la red", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (devices.isEmpty()) {
                        Text("Buscando dispositivos en la LAN...", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "El escaneo cubre toda tu red (WiFi y cable). Asegúrate de que la app esté abierta en los demás dispositivos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            devices.forEach { d ->
                                DeviceRow(d, isSelected = target?.id == d.id) { onSelect(d) }
                            }
                        }
                    }
                }
            }
        }

        // Carpeta de descarga
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Carpeta de descarga", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(downloadFolder, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = {
                    pickDirectoryDialog()?.let { state.setDownloadFolder(java.nio.file.Paths.get(it)) }
                }) { Text("Elegir carpeta") }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: DeviceProfile, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
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
            Text("${d.host} · ${d.type}", style = MaterialTheme.typography.bodySmall)
        }
        if (isSelected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/* ---------------- Enviar ---------------- */

@Composable
private fun SendScreen(state: AppState) {
    val devices by state.devices.collectAsState()
    val selected by state.filesToSend.collectAsState()
    val target by state.sendTarget.collectAsState()
    var pin by remember { mutableStateOf(state.sendPin.value) }
    LaunchedEffect(pin) { state.setSendPin(pin) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Enviar archivos", style = MaterialTheme.typography.titleMedium)
        Text("Destino:", style = MaterialTheme.typography.bodyMedium)
        if (devices.isEmpty()) Text("Buscando dispositivos en la LAN...", style = MaterialTheme.typography.bodySmall)
        devices.forEach { d ->
            val isTarget = target?.id == d.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { state.setSendTarget(d) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isTarget, onClick = { state.setSendTarget(d) })
                Text("${d.name} (${d.type})")
            }
        }

        HorizontalDivider()
        Text("Archivos seleccionados: ${selected.size}")
        selected.forEach { Text("• ${it.name} (${it.size} bytes)", style = MaterialTheme.typography.bodySmall) }

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("PIN del destino") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.4f),
        )
        Button(
            onClick = { state.send() },
            enabled = target != null && pin.length == 6 && selected.isNotEmpty(),
        ) {
            Icon(Icons.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Enviar ${selected.size} archivo(s)")
        }
    }
}

/* ---------------- Explorador ---------------- */

@Composable
private fun ExplorerScreen(state: AppState) {
    val path by state.explorerPath.collectAsState()
    val entries by state.explorerEntries.collectAsState()
    val selected by state.filesToSend.collectAsState()
    val selectedPaths = selected.map { it.path }.toSet()

    LaunchedEffect(Unit) { if (path == null) state.openExplorer(null) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Explorador de archivos", style = MaterialTheme.typography.titleMedium)
        Text("Ruta: ${path ?: "(home)"}", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        entries.forEach { entry ->
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
        Text("${selected.size} archivo(s) marcados para envío.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FileRow(entry: FileEntry, isSelected: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
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

/** Diálogo nativo de selección de directorio (Swing JFileChooser). */
private fun pickDirectoryDialog(): String? {
    val chooser = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else null
}
