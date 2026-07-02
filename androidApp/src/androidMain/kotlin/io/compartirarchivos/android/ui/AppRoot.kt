package io.compartirarchivos.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.compartirarchivos.android.AndroidAppState
import io.compartirarchivos.shared.fs.FileEntry
import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType

private enum class Screen { HOME, RECEIVE, SEND, FILES, DOWNLOAD }

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
    var screen by remember { mutableStateOf(Screen.HOME) }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("CompartirArchivos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${self.name} · IP: ${self.host}", style = MaterialTheme.typography.bodySmall)
                }
                if (screen != Screen.HOME) {
                    TextButton(onClick = { screen = Screen.HOME }) { Text("Inicio") }
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider()
                Text("Estado: $status", Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                NavigationBar {
                    val items = listOf(
                        "Inicio" to Icons.Filled.Devices to Screen.HOME,
                        "Enviar" to Icons.Filled.Send to Screen.SEND,
                        "Archivos" to Icons.Filled.Folder to Screen.FILES,
                        "Recibir" to Icons.Filled.Download to Screen.RECEIVE,
                    )
                    items.forEach { (pair, s) ->
                        val (label, icon) = pair
                        NavigationBarItem(
                            selected = screen == s,
                            onClick = {
                                screen = s
                                if (s == Screen.FILES) {
                                    // Al entrar a Archivos, si no hay permiso, pedirlo.
                                    onRequestStoragePermission()
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            when (screen) {
                Screen.HOME -> HomeScreen { screen = it }
                Screen.RECEIVE -> ReceiveScreen(state)
                Screen.SEND -> SendWizard(
                    state = state,
                    onPickRoot = onPickRoot,
                    onOpenExternalFiles = onOpenExternalFiles,
                    onGoFiles = { screen = Screen.FILES },
                )
                Screen.FILES -> FilesScreen(
                    state = state,
                    onPickRoot = onPickRoot,
                    onOpenExternalFiles = onOpenExternalFiles,
                    onRequestStoragePermission = onRequestStoragePermission,
                )
                Screen.DOWNLOAD -> DownloadFolderScreen(state, onPickDownloadFolder)
            }
        }
    }
}

/* ---------------- HOME ---------------- */

@Composable
private fun HomeScreen(onNavigate: (Screen) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("¿Qué quieres hacer?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        HomeCard(
            icon = Icons.Filled.Download,
            title = "Recibir archivos",
            subtitle = "Muestra tu PIN para que otros te envíen archivos",
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = { onNavigate(Screen.RECEIVE) },
        )
        HomeCard(
            icon = Icons.Filled.Send,
            title = "Enviar archivos",
            subtitle = "Elige archivos y envíalos a otro dispositivo",
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = { onNavigate(Screen.SEND) },
        )
        HomeCard(
            icon = Icons.Filled.Folder,
            title = "Carpeta de descarga",
            subtitle = "Elige dónde se guardan los archivos que recibes",
            color = MaterialTheme.colorScheme.tertiaryContainer,
            onClick = { onNavigate(Screen.DOWNLOAD) },
        )
    }
}

@Composable
private fun HomeCard(icon: ImageVector, title: String, subtitle: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/* ---------------- RECEIVE ---------------- */

@Composable
private fun ReceiveScreen(state: AndroidAppState) {
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
                Text("Válido 60 segundos. Compártelo solo con quien quieras recibir archivos.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { state.refreshPin() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp)); Text("Generar nuevo PIN")
                }
            }
        }
    }
}

/* ---------------- SEND (asistente de pasos) ---------------- */

@Composable
private fun SendWizard(
    state: AndroidAppState,
    onPickRoot: () -> Unit,
    onOpenExternalFiles: () -> Unit,
    onGoFiles: () -> Unit,
) {
    val selected by state.selected.collectAsState()
    val devices by state.devices.collectAsState()
    val target by state.sendTarget.collectAsState()
    var pin by remember { mutableStateOf(state.sendPin.value) }
    LaunchedEffect(pin) { state.setSendPin(pin) }

    // Paso actual: 1=archivos, 2=destino, 3=pin/envio
    val step = when {
        selected.isEmpty() -> 1
        target == null -> 2
        else -> 3
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepIndicator(step)
        when (step) {
            1 -> Step1Files(selected, onGoFiles, onOpenExternalFiles, onClear = { state.clearSelected() })
            2 -> Step2Target(devices, target, onSelect = { state.setSendTarget(it) })
            3 -> Step3Pin(selected.size, target, pin, onPinChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, onSend = { state.send() })
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        val steps = listOf("1. Archivos", "2. Destino", "3. Enviar")
        steps.forEachIndexed { i, label ->
            val active = i + 1 == step
            val done = i + 1 < step
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(label, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Step1Files(selected: List<FileEntry>, onGoFiles: () -> Unit, onOpenExternalFiles: () -> Unit, onClear: () -> Unit) {
    Text("Paso 1: elige los archivos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onGoFiles, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Folder, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Explorar archivos")
        }
        OutlinedButton(onClick = onOpenExternalFiles, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Apps, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Abrir con otra app")
        }
    }
    if (selected.isNotEmpty()) {
        Text("${selected.size} archivo(s) seleccionado(s):", style = MaterialTheme.typography.bodyMedium)
        selected.take(8).forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall) }
        if (selected.size > 8) Text("… y ${selected.size - 8} más", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onClear) { Text("Quitar todos") }
    } else {
        Text("Aún no has elegido archivos.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Step2Target(devices: List<DeviceProfile>, target: DeviceProfile?, onSelect: (DeviceProfile) -> Unit) {
    Text("Paso 2: elige el destino", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (devices.isEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Buscando dispositivos en la red WiFi...", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text("Si no aparece el dispositivo destino, asegúrate de estar en la misma red WiFi y de que la app esté abierta en ambos. El destino debe pulsar \"Recibir\" para estar visible.", style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        devices.forEach { d ->
            val isTarget = target?.id == d.id
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(d) },
                colors = CardDefaults.cardColors(containerColor = if (isTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (d.type) {
                        DeviceType.TV -> Icons.Filled.Tv
                        DeviceType.MOBILE -> Icons.Filled.PhoneAndroid
                        DeviceType.DESKTOP -> Icons.Filled.Computer
                    }
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.Medium)
                        Text(d.host, style = MaterialTheme.typography.bodySmall)
                    }
                    RadioButton(selected = isTarget, onClick = { onSelect(d) })
                }
            }
        }
    }
}

@Composable
private fun Step3Pin(count: Int, target: DeviceProfile?, pin: String, onPinChange: (String) -> Unit, onSend: () -> Unit) {
    Text("Paso 3: introduce el PIN del destino", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    target?.let {
        Text("Destino: ${it.name} (${it.host})", style = MaterialTheme.typography.bodySmall)
    }
    Text("$count archivo(s) listo(s) para enviar.", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = pin,
        onValueChange = onPinChange,
        label = { Text("PIN (6 dígitos)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onSend,
        enabled = pin.length == 6 && target != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Send, contentDescription = null); Spacer(Modifier.width(8.dp))
        Text("Enviar $count archivo(s)")
    }
}

/* ---------------- FILES (explorador) ---------------- */

@Composable
private fun FilesScreen(
    state: AndroidAppState,
    onPickRoot: () -> Unit,
    onOpenExternalFiles: () -> Unit,
    onRequestStoragePermission: () -> Unit,
) {
    val mode by state.explorerMode.collectAsState()
    val entries by state.explorerEntries.collectAsState()
    val selected by state.selected.collectAsState()
    val selPaths = selected.map { it.path }.toSet()
    val tree by state.treeRoot.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Explorador de archivos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Selector de modo.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == io.compartirarchivos.android.ExplorerMode.INTERNAL, onClick = {
                onRequestStoragePermission()
            }, label = { Text("Interno") }, leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(16.dp)) })
            FilterChip(selected = mode == io.compartirarchivos.android.ExplorerMode.SAF, onClick = {
                state.useSafExplorer(); if (tree == null) onPickRoot()
            }, label = { Text("SAF (carpeta)") }, leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) })
            FilterChip(selected = false, onClick = onOpenExternalFiles, label = { Text("Otra app") }, leadingIcon = { Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(16.dp)) })
        }

        when (mode) {
            io.compartirarchivos.android.ExplorerMode.INTERNAL -> {
                if (entries.isEmpty()) {
                    Text("Concediendo permiso o cargando carpetas...", style = MaterialTheme.typography.bodySmall)
                }
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
                    Text("Concede acceso a una carpeta para explorarla con SAF.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onPickRoot, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar carpeta (SAF)") }
                } else {
                    Text("Carpeta SAF concedida. Toca un archivo para marcarlo.", style = MaterialTheme.typography.bodySmall)
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
        Text("${selected.size} marcados para enviar.", style = MaterialTheme.typography.bodySmall)
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

/* ---------------- DOWNLOAD folder ---------------- */

@Composable
private fun DownloadFolderScreen(state: AndroidAppState, onPick: () -> Unit) {
    val folder by state.downloadFolder.collectAsState()
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Carpeta de descarga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val label = folder?.let {
            runCatching { android.net.Uri.parse(it).lastPathSegment ?: it }.getOrDefault(it)
        } ?: "Por defecto (archivos de la app)"
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Actual:", style = MaterialTheme.typography.bodySmall)
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Folder, contentDescription = null); Spacer(Modifier.width(8.dp))
            Text(if (folder == null) "Elegir carpeta" else "Cambiar carpeta")
        }
        if (folder != null) {
            OutlinedButton(onClick = { state.clearDownloadFolder() }, modifier = Modifier.fillMaxWidth()) {
                Text("Usar carpeta por defecto")
            }
        }
        Text(
            "Los archivos que recibas se guardarán en esta carpeta.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
