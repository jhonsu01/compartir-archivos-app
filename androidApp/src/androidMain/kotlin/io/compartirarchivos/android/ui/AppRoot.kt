package io.compartirarchivos.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.compartirarchivos.android.AndroidAppState
import io.compartirarchivos.shared.fs.FileEntry
import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType

/**
 * Raiz de la UI. Adapta tamano de iconos y layout segun TV vs movil.
 */
@Composable
fun AppRoot(state: AndroidAppState, onPickRoot: () -> Unit) {
    // Arranca servicio al entrar
    DisposableEffect(Unit) {
        state.start()
        onDispose { /* el stop lo hace onDestroy */ }
    }

    val self by state.self.collectAsState()
    val isTv = self.type == DeviceType.TV
    val iconSize = self.type.iconSizeDp.dp   // iconos compactos / grandes (seccion 5)

    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("CompartirArchivos", fontWeight = FontWeight.Bold, fontSize = if (isTv) 24.sp else 18.sp)
                    Text("Yo: ${self.name} · ${self.host}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> DevicesTab(state, iconSize, isTv)
                1 -> SendTab(state, iconSize, isTv)
                2 -> ExplorerTab(state, iconSize, isTv, onPickRoot)
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
private fun DevicesTab(state: AndroidAppState, iconSize: androidx.compose.ui.unit.Dp, isTv: Boolean) {
    val devices by state.devices.collectAsState()
    val pin by state.pin.collectAsState()
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.weight(1f)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tu PIN", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    pin ?: "------",
                    fontSize = if (isTv) 64.sp else 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text("Válido 60 s", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { state.refreshPin() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(iconSize))
                    Spacer(Modifier.width(6.dp))
                    Text("Nuevo PIN")
                }
            }
        }
        Card(Modifier.weight(1.3f)) {
            Column(Modifier.padding(16.dp)) {
                Text("Dispositivos en la red", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (devices.isEmpty()) {
                    Text("Buscando...", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(devices, key = { it.id }) { DeviceItem(it, iconSize) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(d: DeviceProfile, iconSize: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = when (d.type) {
            DeviceType.TV -> Icons.Filled.Tv
            DeviceType.MOBILE -> Icons.Filled.PhoneAndroid
            DeviceType.DESKTOP -> Icons.Filled.Computer
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, fontWeight = FontWeight.Medium)
            Text("${d.host}:${d.port}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SendTab(state: AndroidAppState, iconSize: androidx.compose.ui.unit.Dp, isTv: Boolean) {
    val devices by state.devices.collectAsState()
    val selected by state.selected.collectAsState()
    var target by remember { mutableStateOf<DeviceProfile?>(null) }
    var pin by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Enviar archivos", fontWeight = FontWeight.SemiBold)
        Text("Destino:")
        devices.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = target?.id == d.id, onClick = { target = d })
                Text("${d.name} (${d.type})")
            }
        }
        if (devices.isEmpty()) Text("Sin dispositivos detectados.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Archivos: ${selected.size}")
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN del destino") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(
            onClick = { target?.let { state.sendTo(it, pin) } },
            enabled = target != null && pin.length == 6 && selected.isNotEmpty(),
        ) {
            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(6.dp)); Text("Enviar")
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

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Explorador", fontWeight = FontWeight.SemiBold)
        if (tree == null) {
            Text("Concede acceso a una carpeta para explorar.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onPickRoot) { Text("Seleccionar carpeta (SAF)") }
        } else {
            Text("Raiz concedida", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.path }) { e ->
                    FileItemRow(e, e.path in selPaths, iconSize,
                        onClick = {
                            if (e.isDirectory) state.openExplorer(tree!!) else state.toggleSelect(e)
                        },
                        onToggle = { state.toggleSelect(e) }
                    )
                }
            }
            Text("${selected.size} marcados para enviar", style = MaterialTheme.typography.bodySmall)
        }
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
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (e.isDirectory) Icons.Filled.Folder else Icons.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(8.dp))
        Text(e.name, Modifier.weight(1f))
        if (!e.isDirectory) Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        TextButton(onClick = onClick) { Text(if (e.isDirectory) "Abrir" else "Marcar") }
    }
}
