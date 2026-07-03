package io.compartirarchivos.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
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
        title = "CompartirArchivos — v0.7.0",
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        window.minimumSize = Dimension(900, 600)
        AppTheme { AppScaffold(state) }
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
}

@Composable
private fun AppScaffold(state: AppState) {
    var tab by remember { mutableStateOf(Tab.DEVICES) }
    val self by state.selfProfile.collectAsState()
    val status by state.status.collectAsState()

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.DEVICES -> DevicesScreen(state) { device ->
                    state.setSendTarget(device)
                    tab = Tab.SEND
                }
                Tab.SEND -> SendScreen(state)
            }
            HorizontalDivider()
            Text("Estado: $status", Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }

    // Diálogo de confirmación de envío.
    val result by state.sendResult.collectAsState()
    result?.let { outcome ->
        AlertDialog(
            onDismissRequest = { state.clearSendResult() },
            confirmButton = { Button(onClick = { state.clearSendResult() }) { Text("Aceptar") } },
            title = {
                Text(
                    when (outcome) {
                        is SendOutcome.Success -> "Envío completado"
                        else -> "No se pudo enviar"
                    }
                )
            },
            text = { Text(outcome.message()) },
        )
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
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tu PIN", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(pin ?: "------", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Válido 60 s", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { state.refreshPin() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Nuevo PIN")
                    }
                }
            }
            Card(Modifier.weight(1.4f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Dispositivos en la red", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (devices.isEmpty()) {
                        Text("Buscando dispositivos en la LAN...", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            devices.forEach { d -> DeviceRow(d, isSelected = target?.id == d.id) { onSelect(d) } }
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Carpeta de descarga", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(downloadFolder, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { pickDirectoryDialog()?.let { state.setDownloadFolder(java.nio.file.Paths.get(it)) } }) { Text("Elegir carpeta") }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: DeviceProfile, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (d.type) {
            DeviceType.TV -> Icons.Filled.Tv
            DeviceType.MOBILE -> Icons.Filled.PhoneAndroid
            DeviceType.DESKTOP -> Icons.Filled.Computer
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, fontWeight = FontWeight.Medium)
            Text("${d.host} · ${d.type}", style = MaterialTheme.typography.bodySmall)
        }
        if (isSelected) Icon(Icons.Filled.CheckCircle, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary)
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

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Destino
        Text("Destino", style = MaterialTheme.typography.titleMedium)
        if (devices.isEmpty()) Text("Buscando dispositivos en la LAN...", style = MaterialTheme.typography.bodySmall)
        devices.forEach { d ->
            val isTarget = target?.id == d.id
            Row(
                Modifier.fillMaxWidth()
                    .background(if (isTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .clickable { state.setSendTarget(d) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isTarget, onClick = { state.setSendTarget(d) })
                Text("${d.name} (${d.type})")
            }
        }

        HorizontalDivider()

        // Archivos (botón Examinar... -> diálogo nativo de Windows)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Archivos (${selected.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = {
                val files = pickFilesDialog()
                if (files.isNotEmpty()) state.addFilesFromPaths(files)
            }) {
                Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Examinar…")
            }
        }
        selected.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name)
                    Text("${f.size} bytes", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { state.removeSelected(f) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Quitar")
                }
            }
        }
        if (selected.isNotEmpty()) {
            TextButton(onClick = { state.clearSelected() }) { Text("Quitar todos") }
        }

        HorizontalDivider()

        // PIN + Enviar
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
            Icon(Icons.Filled.Send, contentDescription = null); Spacer(Modifier.width(6.dp))
            Text("Enviar ${selected.size} archivo(s)")
        }
    }
}

/* ---------------- Diálogos nativos de Windows ---------------- */

private fun pickDirectoryDialog(): String? {
    val chooser = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
}

/** Diálogo nativo multi-selección de archivos. */
private fun pickFilesDialog(): List<String> {
    val chooser = javax.swing.JFileChooser().apply {
        isMultiSelectionEnabled = true
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.map { it.absolutePath }
    } else emptyList()
}
