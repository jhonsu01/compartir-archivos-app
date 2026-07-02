package io.compartirarchivos.desktop

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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Estado central de la app de escritorio.
 * Arranca el servidor receptor + discovery, y expone la UI.
 */
class AppState {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val identity = loadOrCreateIdentity(defaultName = hostName(), type = DeviceType.DESKTOP)
    private val discovery = createDiscoveryService()
    private val fileSource = createFileSource()

    private val sinkDir: Path = Paths.get(System.getProperty("user.home"), "Downloads", "CompartirArchivos").also { Files.createDirectories(it) }

    private val server = ReceiveServer(
        ReceiverConfig(
            selfInfo = DeviceInfo(identity.id, identity.name, DeviceType.DESKTOP, requiresPairing = true),
            port = Protocol.DEFAULT_PORT,
        ),
        FileSink { name, bytes ->
            try {
                val target = sinkDir.resolve(sanitize(name))
                Files.write(target, bytes)
                target.toString()
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }
    )

    // --- Estado expuesto a la UI ---

    private val _selfProfile = MutableStateFlow(
        DeviceProfile(identity.id, identity.name, DeviceType.DESKTOP, "127.0.0.1", Protocol.DEFAULT_PORT)
    )
    val selfProfile: StateFlow<DeviceProfile> = _selfProfile.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val devices: StateFlow<List<DeviceProfile>> = _devices.asStateFlow()

    private val _currentPin = MutableStateFlow<String?>(null)
    val currentPin: StateFlow<String?> = _currentPin.asStateFlow()

    private val _filesToSend = MutableStateFlow<List<FileEntry>>(emptyList())
    val filesToSend: StateFlow<List<FileEntry>> = _filesToSend.asStateFlow()

    private val _status = MutableStateFlow("Listo")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _explorerPath = MutableStateFlow<String?>(null)
    val explorerPath: StateFlow<String?> = _explorerPath.asStateFlow()

    private val _explorerEntries = MutableStateFlow<List<FileEntry>>(emptyList())
    val explorerEntries: StateFlow<List<FileEntry>> = _explorerEntries.asStateFlow()

    /** Inicia servidor + discovery. */
    fun start() {
        server.start()
        discovery.start(_selfProfile.value) {}

        // Refrescar mi IP real para anunciarla bien
        scope.launch {
            val ip = bestLocalIp()
            _selfProfile.value = _selfProfile.value.copy(host = ip)
            // Reiniciar anuncio con la IP correcta seria ideal; en v0.1 el
            // discovery ya se resuelve por IP en el receptor.
        }

        scope.launch {
            discovery.events.collect { ev ->
                when (ev) {
                    is DiscoveryEvent.Found -> {
                        _devices.value = (_devices.value.filter { it.id != ev.device.id } + ev.device)
                            .sortedBy { it.name }
                    }
                    is DiscoveryEvent.Lost -> {
                        _devices.value = _devices.value.filter { it.id != ev.deviceId }
                    }
                    is DiscoveryEvent.Error -> _status.value = "Error discovery: ${ev.message}"
                }
            }
        }

        // PIN inicial
        refreshPin()
    }

    /** Genera y muestra un nuevo PIN de emparejamiento. */
    fun refreshPin() {
        _currentPin.value = server.issuePin()
    }

    /** Marca archivos del explorador para enviar. */
    fun toggleSelectForSend(entry: FileEntry) {
        val current = _filesToSend.value
        _filesToSend.value = if (current.any { it.path == entry.path }) {
            current.filter { it.path != entry.path }
        } else {
            current + entry
        }
    }

    /** Envia los archivos seleccionados a [target], pidiendo antes el PIN al usuario. */
    fun sendTo(target: DeviceProfile, pin: String) {
        val files = _filesToSend.value
        if (files.isEmpty()) {
            _status.value = "No hay archivos seleccionados"
            return
        }
        _status.value = "Enviando a ${target.name}..."
        scope.launch {
            val outbound = files.map { fe ->
                OutboundFile(fe.name, fileSource.read(fe.path))
            }
            val client = TransferClient(target.httpUrl)
            val result = client.sendAll(
                selfDeviceId = identity.id,
                selfDeviceName = identity.name,
                selfDeviceType = DeviceType.DESKTOP,
                pin = pin,
                files = outbound,
            )
            client.close()
            _status.value = when (result) {
                is TransferResult.Success -> "Enviado: ${result.accepted.reason}"
                is TransferResult.PairingFailed -> "Emparejamiento fallido: ${result.reason}"
                is TransferResult.Error -> "Error: ${result.cause.message}"
            }
        }
    }

    // --- Explorador ---

    fun openExplorer(path: String?) {
        _explorerPath.value = path ?: fileSource.defaultRootLabel()
        scope.launch {
            _explorerEntries.value = fileSource.list(_explorerPath.value)
        }
    }

    fun browseTo(entry: FileEntry) {
        if (entry.isDirectory) openExplorer(entry.path)
    }

    fun stop() {
        discovery.stop()
        server.stop()
    }

    // --- helpers ---

    private fun sanitize(name: String): String =
        name.replace("..", "").replace("/", "_").replace("\\", "_").trim().ifEmpty { "archivo" }
}

/* helpers de top-level */

internal fun hostName(): String =
    try { java.net.InetAddress.getLocalHost().hostName } catch (_: Throwable) { "Windows-PC" }

internal fun bestLocalIp(): String =
    try {
        java.net.NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }
            .firstOrNull()?.hostAddress ?: "127.0.0.1"
    } catch (_: Throwable) { "127.0.0.1" }
