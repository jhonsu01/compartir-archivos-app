package io.compartirarchivos.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.compartirarchivos.android.AndroidAppState
import io.compartirarchivos.shared.fs.FileEntry
import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType

@Composable
fun AppRoot(
    state: AndroidAppState,
    onPickRoot: () -> Unit,
    onPickDownloadFolder: () -> Unit,
    onOpenExternalFiles: () -> Unit,
    onRequestStoragePermission: () -> Unit,
) {
    DisposableEffect(Unit) {
        state.start()
        onDispose {}
    }

    val self by state.self.collectAsState()
    val status by state.status.collectAsState()
    var tab by remember { mutableStateOf(0) } // 0 = Recibir, 1 = Enviar
    var showDownloadDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CompartirArchivos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("IP: ${self.host}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { showDownloadDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Carpeta de descarga")
                    }
                }
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Recibir") }, icon = { Icon(Icons.Filled.Download, contentDescription = null) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Enviar") }, icon = { Icon(Icons.Filled.Send, contentDescription = null) })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Column(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ReceiveTab(state)
                    1 -> SendTab(
                        state = state,
                        onPickRoot = onPickRoot,
                        onOpenExternalFiles = onOpenExternalFiles,
                        onRequestStoragePermission = onRequestStoragePermission,
                    )
                }
            }
            HorizontalDivider()
            Text("Estado: $status", Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showDownloadDialog) {
        DownloadFolderDialog(state, onPick = { onPickDownloadFolder(); showDownloadDialog = false }, onDismiss = { showDownloadDialog = false })
    }
}

/* ---------------- RECIBIR ---------------- */

@Composable
private fun ReceiveTab(state: AndroidAppState) {
    val pin by state.pin.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tu PIN de emparejamiento", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    pin ?: "------",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text("Válido 60 segundos. Compártelo con quien quiera enviarte archivos.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { state.refreshPin() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp)); Text("Generar nuevo PIN")
                }
            }
        }
    }
}

/* ---------------- ENVIAR (todo en una pantalla, scroll) ---------------- */

@Composable
private fun SendTab(
    state: AndroidAppState,
    onPickRoot: () -> Unit,
    onOpenExternalFiles: () -> Unit,
    onRequestStoragePermission: () -> Unit,
) {
    val devices by state.devices.collectAsState()
    val selected by state.selected.collectAsState()
    val target by state.sendTarget.collectAsState()
    var pin by remember { mutableStateOf(state.sendPin.value) }
    LaunchedEffect(pin) { state.setSendPin(pin) }

    // Explorador integrado
    val explorerMode by state.explorerMode.collectAsState()
    val entries by state.explorerEntries.collectAsState()
    val tree by state.treeRoot.collectAsState()
    val selPaths = selected.map { it.path }.toSet()

    // Pedir permiso al entrar si modo interno y no hay entradas
    LaunchedEffect(Unit) {
        if (entries.isEmpty()) onRequestStoragePermission()
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // --- 1. Dispositivos ---
        Text("1. Elige el destino", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (devices.isEmpty()) {
            Text("Buscando dispositivos en la LAN...", style = MaterialTheme.typography.bodySmall)
        }
        devices.forEach { d ->
            val isTarget = target?.id == d.id
            Card(
                modifier = Modifier.fillMaxWidth().clickable { state.setSendTarget(d) },
                colors = CardDefaults.cardColors(containerColor = if (isTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (d.type) {
                        DeviceType.TV -> Icons.Filled.Tv
                        DeviceType.MOBILE -> Icons.Filled.PhoneAndroid
                        DeviceType.DESKTOP -> Icons.Filled.Computer
                    }
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.Medium)
                        Text(d.host, style = MaterialTheme.typography.bodySmall)
                    }
                    RadioButton(selected = isTarget, onClick = { state.setSendTarget(d) })
                }
            }
        }

        HorizontalDivider()
        // --- 2. Archivos ---
        Text("2. Elige los archivos (${selected.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        // Chips de modo de exploración
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = explorerMode == io.compartirarchivos.android.ExplorerMode.INTERNAL,
                onClick = onRequestStoragePermission,
                label = { Text("Interno") },
                leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = explorerMode == io.compartirarchivos.android.ExplorerMode.SAF,
                onClick = {
                    state.useSafExplorer()
                    if (tree == null) onPickRoot()
                },
                label = { Text("Carpeta (SAF)") },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = false,
                onClick = onOpenExternalFiles,
                label = { Text("Otra app") },
                leadingIcon = { Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }

        when (explorerMode) {
            io.compartirarchivos.android.ExplorerMode.INTERNAL -> {
                if (entries.isEmpty()) Text("Concediendo permiso o cargando carpetas...", style = MaterialTheme.typography.bodySmall)
                entries.forEach { e ->
                    FileItemRow(
                        e, e.path in selPaths,
                        onClick = { if (e.isDirectory) state.openInternalFolder(e.path) else state.toggleSelect(e) },
                        onToggle = { state.toggleSelect(e) },
                    )
                }
            }
            io.compartirarchivos.android.ExplorerMode.SAF -> {
                if (tree == null) {
                    Text("Concede acceso a una carpeta para explorarla.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onPickRoot, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar carpeta (SAF)") }
                } else {
                    entries.forEach { e ->
                        FileItemRow(
                            e, e.path in selPaths,
                            onClick = { if (e.isDirectory) state.openExplorer(tree!!) else state.toggleSelect(e) },
                            onToggle = { state.toggleSelect(e) },
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        // --- 3. PIN y enviar ---
        Text("3. Introduce el PIN del destino", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN (6 dígitos)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { state.send() },
            enabled = target != null && pin.length == 6 && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Enviar ${selected.size} archivo(s)")
        }
    }
}

@Composable
private fun FileItemRow(e: FileEntry, isSelected: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (e.isDirectory) Icons.Filled.Folder else Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, fontWeight = FontWeight.Medium)
                if (!e.isDirectory) Text("${e.size} bytes", style = MaterialTheme.typography.bodySmall)
            }
            if (!e.isDirectory) Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        }
    }
}

/* ---------------- Diálogo carpeta de descarga ---------------- */

@Composable
private fun DownloadFolderDialog(state: AndroidAppState, onPick: () -> Unit, onDismiss: () -> Unit) {
    val folder by state.downloadFolder.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onPick) { Text("Elegir carpeta") } },
        dismissButton = {
            Row {
                if (folder != null) TextButton(onClick = { state.clearDownloadFolder() }) { Text("Por defecto") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        title = { Text("Carpeta de descarga") },
        text = {
            val label = folder?.let { runCatching { android.net.Uri.parse(it).lastPathSegment ?: it }.getOrDefault(it) }
                ?: "Por defecto (archivos de la app)"
            Text("Actual: $label\n\nLos archivos recibidos se guardan aquí.")
        },
    )
}
