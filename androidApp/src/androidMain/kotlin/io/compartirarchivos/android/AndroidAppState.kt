package io.compartirarchivos.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.compartirarchivos.shared.client.OutboundFile
import io.compartirarchivos.shared.client.TransferClient
import io.compartirarchivos.shared.client.TransferResult
import io.compartirarchivos.shared.discovery.DiscoveryEvent
import io.compartirarchivos.shared.discovery.createDiscoveryService
import io.compartirarchivos.shared.fs.FileEntry
import io.compartirarchivos.shared.fs.createFileSource
import io.compartirarchivos.shared.identity.loadOrCreateIdentity
import io.compartirarchivos.shared.model.DeviceInfo
import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType
import io.compartirarchivos.shared.net.FileSink
import io.compartirarchivos.shared.net.Protocol
import io.compartirarchivos.shared.net.ReceiveServer
import io.compartirarchivos.shared.net.ReceiverConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado central de la app Android. Arranca receptor + discovery.
 */
class AndroidAppState(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val type = DeviceDetector.detectType(context)
    private val identity = loadOrCreateIdentity(DeviceDetector.deviceName(), type)
    private val discovery = createDiscoveryService()
    private val fileSource = createFileSource()

    // Carpeta de descargas dentro de archivos externos de la app
    private val downloadsDir = java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "Recibidos").apply { mkdirs() }

    private val server = ReceiveServer(
        ReceiverConfig(
            selfInfo = DeviceInfo(identity.id, identity.name, type, requiresPairing = true),
            port = Protocol.DEFAULT_PORT,
        ),
        FileSink { name, bytes ->
            try {
                val safe = name.replace("..", "").replace("/", "_").replace("\\", "_").trim().ifEmpty { "archivo" }
                val target = java.io.File(downloadsDir, safe)
                target.writeBytes(bytes)
                target.absolutePath
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }
    )

    private val _self = MutableStateFlow(DeviceProfile(identity.id, identity.name, type, "0.0.0.0", Protocol.DEFAULT_PORT))
    val self: StateFlow<DeviceProfile> = _self.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val devices: StateFlow<List<DeviceProfile>> = _devices.asStateFlow()

    private val _pin = MutableStateFlow<String?>(null)
    val pin: StateFlow<String?> = _pin.asStateFlow()

    private val _status = MutableStateFlow("Listo")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _selected = MutableStateFlow<List<FileEntry>>(emptyList())
    val selected: StateFlow<List<FileEntry>> = _selected.asStateFlow()

    private val _explorerEntries = MutableStateFlow<List<FileEntry>>(emptyList())
    val explorerEntries: StateFlow<List<FileEntry>> = _explorerEntries.asStateFlow()

    /** Raiz tree del explorador (URI concedida con ACTION_OPEN_DOCUMENT_TREE). */
    private val _treeRoot = MutableStateFlow<String?>(null)
    val treeRoot: StateFlow<String?> = _treeRoot.asStateFlow()

    fun start() {
        server.start()
        val ip = DeviceDetector.localWifiIp(context)
        val selfProf = DeviceProfile(identity.id, identity.name, type, ip, Protocol.DEFAULT_PORT)
        _self.value = selfProf
        discovery.start(selfProf) {}

        scope.launch {
            discovery.events.collect { ev ->
                when (ev) {
                    is DiscoveryEvent.Found -> _devices.value =
                        (_devices.value.filter { it.id != ev.device.id } + ev.device).sortedBy { it.name }
                    is DiscoveryEvent.Lost -> _devices.value = _devices.value.filter { it.id != ev.deviceId }
                    is DiscoveryEvent.Error -> _status.value = "Error: ${ev.message}"
                }
            }
        }
        refreshPin()
    }

    fun refreshPin() { _pin.value = server.issuePin() }

    fun setTreeRoot(uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Throwable) {}
        _treeRoot.value = uri.toString()
        openExplorer(uri.toString())
    }

    fun openExplorer(treeUri: String) {
        scope.launch {
            _explorerEntries.value = fileSource.list(treeUri)
        }
    }

    fun toggleSelect(entry: FileEntry) {
        _selected.value = if (_selected.value.any { it.path == entry.path }) {
            _selected.value.filterNot { it.path == entry.path }
        } else {
            _selected.value + entry
        }
    }

    fun sendTo(target: DeviceProfile, pin: String) {
        val files = _selected.value
        if (files.isEmpty()) { _status.value = "Selecciona archivos"; return }
        _status.value = "Enviando a ${target.name}..."
        scope.launch {
            val outbound = files.map { OutboundFile(it.name, fileSource.read(it.path)) }
            val client = TransferClient(target.httpUrl)
            val res = client.sendAll(identity.id, identity.name, type, pin, outbound)
            client.close()
            _status.value = when (res) {
                is TransferResult.Success -> "Enviado: ${res.accepted.reason}"
                is TransferResult.PairingFailed -> "Emparejamiento fallido: ${res.reason}"
                is TransferResult.Error -> "Error: ${res.cause.message}"
            }
        }
    }

    fun stop() {
        discovery.stop()
        server.stop()
    }
}
