package io.compartirarchivos.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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

/**
 * Raiz de la UI. Adapta tamano de iconos y layout segun TV vs movil.
 */
@Composable
fun AppRoot(
    state: AndroidAppState,
    onPickRoot: () -> Unit,
    onPickDownloadFolder: () -> Unit,
) {
    DisposableEffect(Unit) {
        state.start()
        onDispose { /* el stop lo hace onDestroy */ }
    }

    val self by state.self.collectAsState()
    val isTv = self.type == DeviceType.TV
    val iconSize = self.type.iconSizeDp.dp

    var tab by remember { mutableStateOf(0) }
    // Callback para que la pestana Dispositivos pida navegar a Enviar.
    var pendingNavigateToSend by remember { mutableStateOf(false) }

    LaunchedEffect(pendingNavigateToSend) {
        if (pendingNavigateToSend) {
            tab = 1
            pendingNavigateToSend = false
        }
    }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("CompartirArchivos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Yo: ${self.name} · ${self.host}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> DevicesTab(state, iconSize, onPickDownloadFolder) { device ->
                        state.setSendTarget(device)
                        pendingNavigateToSend = true
                    }
                    1 -> SendTab(state, iconSize, isTv)
                    2 -> ExplorerTab(state, iconSize, isTv, onPickRoot)
                }
            }
            val status by state.status.collectAsState()
            HorizontalDivider()
            Text("Estado: $status", Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
            NavigationBar {
                listOf("Dispositivos" to Icons.Filled.Devices, "Enviar" to Icons.Filled.Send, "Explorador" to Icons.Filled.Folder)
                    .forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
            }
        }
    }
}

@Composable
private fun DevicesTab(
    state: AndroidAppState,
    iconSize: androidx.compose.ui.unit.Dp,
    onPickDownloadFolder: () -> Unit,
    onSelect: (DeviceProfile) -> Unit,
) {
    val devices by state.devices.collectAsState()
    val pin by state.pin.collectAsState()
    val target by state.sendTarget.collectAsState()
    val downloadFolder by state.downloadFolder.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        // --- Carpeta de descarga ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Carpeta de descarga", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                val label = downloadFolder?.let {
                    // Mostrar el nombre legible de la carpeta SAF.
                    runCatching { android.net.Uri.parse(it).lastPathSegment ?: it }.getOrDefault(it)
                } ?: "Por defecto (archivos de la app)"
                Text(label, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPickDownloadFolder, modifier = Modifier.weight(1f)) {
                        Text(if (downloadFolder == null) "Elegir carpeta" else "Cambiar carpeta")
                    }
                    if (downloadFolder != null) {
                        OutlinedButton(onClick = { state.clearDownloadFolder() }, modifier = Modifier.weight(1f)) {
                            Text("Por defecto")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // --- Card del PIN ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tu PIN de emparejamiento", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                // Tipografia del tema: escala con el dispositivo, no desborda.
                Text(
                    pin ?: "------",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text("Válido 60 s. Compártelo con quien quieras recibir archivos.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { state.refreshPin() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(iconSize))
                    Spacer(Modifier.width(6.dp))
                    Text("Generar nuevo PIN")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Dispositivos en la red", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            Text("Buscando dispositivos en la red WiFi...", style = MaterialTheme.typography.bodyMedium)
        } else {
            devices.forEach { d ->
                DeviceItem(
                    d = d,
                    iconSize = iconSize,
                    isSelected = target?.id == d.id,
                    onClick = { onSelect(d) },
                )
            }
        }
    }
}

@Composable
private fun DeviceItem(
    d: DeviceProfile,
    iconSize: androidx.compose.ui.unit.Dp,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when (d.type) {
                DeviceType.TV -> Icons.Filled.Tv
                DeviceType.MOBILE -> Icons.Filled.PhoneAndroid
                DeviceType.DESKTOP -> Icons.Filled.Computer
            }
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.Medium)
                Text("${d.host} · ${d.type}", style = MaterialTheme.typography.bodySmall)
            }
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SendTab(state: AndroidAppState, iconSize: androidx.compose.ui.unit.Dp, isTv: Boolean) {
    val devices by state.devices.collectAsState()
    val selected by state.selected.collectAsState()
    val target by state.sendTarget.collectAsState()
    var pin by remember { mutableStateOf(state.sendPin.value) }
    LaunchedEffect(pin) { state.setSendPin(pin) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Enviar archivos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Destino:", style = MaterialTheme.typography.bodyMedium)
        if (devices.isEmpty()) {
            Text("Sin dispositivos. Ve a la pestaña Dispositivos.", style = MaterialTheme.typography.bodySmall)
        }
        devices.forEach { d ->
            val isTarget = target?.id == d.id
            Card(
                modifier = Modifier.fillMaxWidth().clickable { state.setSendTarget(d) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isTarget, onClick = { state.setSendTarget(d) })
                    Text("${d.name} (${d.type})")
                }
            }
        }
        HorizontalDivider()
        Text("Archivos seleccionados: ${selected.size}")
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN del destino") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { state.send() },
            enabled = target != null && pin.length == 6 && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(6.dp))
            Text("Enviar ${selected.size} archivo(s)")
        }
    }
}

@Composable
private fun ExplorerTab(
    state: AndroidAppState,
    iconSize: androidx.compose.ui.unit.Dp,
    isTv: Boolean,
    onPickRoot: () -> Unit,
) {
    val tree by state.treeRoot.collectAsState()
    val entries by state.explorerEntries.collectAsState()
    val selected by state.selected.collectAsState()
    val selPaths = selected.map { it.path }.toSet()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Explorador", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (tree == null) {
            Text("Concede acceso a una carpeta para explorar.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onPickRoot, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar carpeta (SAF)") }
        } else {
            Text("Raíz concedida. Toca un archivo para marcarlo.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            entries.forEach { e -> FileItemRow(e, e.path in selPaths, iconSize, onClick = {
                if (e.isDirectory) state.openExplorer(tree!!) else state.toggleSelect(e)
            }, onToggle = { state.toggleSelect(e) }) }
        }
        Text("${selected.size} marcados para enviar", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FileItemRow(
    e: FileEntry,
    isSelected: Boolean,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (e.isDirectory) Icons.Filled.Folder else Icons.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(8.dp))
            Text(e.name, Modifier.weight(1f))
            if (!e.isDirectory) Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        }
    }
}
