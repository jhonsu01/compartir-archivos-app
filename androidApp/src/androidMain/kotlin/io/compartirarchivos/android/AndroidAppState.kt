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
    private val javaFileSource = io.compartirarchivos.shared.fs.JavaFileSource()

    // Modo del explorador: interno (java.io) o SAF.
    private val _explorerMode = MutableStateFlow(ExplorerMode.INTERNAL)
    val explorerMode: StateFlow<ExplorerMode> = _explorerMode.asStateFlow()

    // Raices rapidas (Download, DCIM, Pictures...) para el explorador interno.
    private val _quickRoots = MutableStateFlow<List<FileEntry>>(emptyList())
    val quickRoots: StateFlow<List<FileEntry>> = _quickRoots.asStateFlow()

    // Carpeta de descargas por defecto (archivos externos de la app).
    private val defaultDownloadsDir = java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "Recibidos").apply { mkdirs() }

    // Carpeta de descarga elegida por el usuario (URI tree SAF), persistida.
    private val prefs = context.getSharedPreferences("compartirarchivos_prefs", Context.MODE_PRIVATE)
    private val _downloadFolder = MutableStateFlow<String?>(prefs.getString(KEY_DOWNLOAD_FOLDER, null))
    val downloadFolder: StateFlow<String?> = _downloadFolder.asStateFlow()

    private val server = ReceiveServer(
        ReceiverConfig(
            selfInfo = DeviceInfo(identity.id, identity.name, type, requiresPairing = true),
            port = Protocol.DEFAULT_PORT,
        ),
        FileSink { name, bytes ->
            try {
                val safe = name.replace("..", "").replace("/", "_").replace("\\", "_").trim().ifEmpty { "archivo" }
                val treeUri = _downloadFolder.value
                if (treeUri != null) {
                    // Guardar via SAF (DocumentFile) en la carpeta elegida.
                    val tree = DocumentFile.fromTreeUri(context, android.net.Uri.parse(treeUri))
                        ?: return@FileSink null
                    val existing = tree.findFile(safe)
                    existing?.delete()
                    val doc = tree.createFile("application/octet-stream", safe) ?: return@FileSink null
                    context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) }
                    doc.uri.toString()
                } else {
                    // Fallback: carpeta por defecto de la app.
                    val target = java.io.File(defaultDownloadsDir, safe)
                    target.writeBytes(bytes)
                    target.absolutePath
                }
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

    // Destino y PIN del envio: viven en el StateFlow (no en remember de UI)
    // para sobrevivir a cambios de pestana (seleccionar destino -> explorador -> volver).
    private val _sendTarget = MutableStateFlow<DeviceProfile?>(null)
    val sendTarget: StateFlow<DeviceProfile?> = _sendTarget.asStateFlow()

    private val _sendPin = MutableStateFlow("")
    val sendPin: StateFlow<String> = _sendPin.asStateFlow()

    fun setSendTarget(device: DeviceProfile?) { _sendTarget.value = device }
    fun setSendPin(pin: String) { _sendPin.value = pin }

    private val _explorerEntries = MutableStateFlow<List<FileEntry>>(emptyList())
    val explorerEntries: StateFlow<List<FileEntry>> = _explorerEntries.asStateFlow()

    /** Raiz tree del explorador (URI concedida con ACTION_OPEN_DOCUMENT_TREE). */
    private val _treeRoot = MutableStateFlow<String?>(null)
    val treeRoot: StateFlow<String?> = _treeRoot.asStateFlow()

    /** Archivos recibidos (lista de la carpeta de descarga actual). */
    private val _receivedFiles = MutableStateFlow<List<FileEntry>>(emptyList())
    val receivedFiles: StateFlow<List<FileEntry>> = _receivedFiles.asStateFlow()

    /** Establece la carpeta de descarga (URI tree SAF), persistiéndola. */
    fun setDownloadFolder(uri: android.net.Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { context.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Throwable) {}
        _downloadFolder.value = uri.toString()
        prefs.edit().putString(KEY_DOWNLOAD_FOLDER, uri.toString()).apply()
        _status.value = "Carpeta de descarga actualizada"
    }

    /** Limpia la carpeta personalizada y vuelve a la por defecto. */
    fun clearDownloadFolder() {
        _downloadFolder.value = null
        prefs.edit().remove(KEY_DOWNLOAD_FOLDER).apply()
        _status.value = "Usando carpeta por defecto: ${defaultDownloadsDir.absolutePath}"
    }

    /** Cambia al explorador interno (java.io) y carga las carpetas rápidas. */
    fun useInternalExplorer() {
        _explorerMode.value = ExplorerMode.INTERNAL
        _treeRoot.value = null
        scope.launch {
            _quickRoots.value = javaFileSource.quickRoots()
            _explorerEntries.value = _quickRoots.value
        }
    }

    /** Cambia al explorador SAF (requiere carpeta concedida vía onPickRoot). */
    fun useSafExplorer() {
        _explorerMode.value = ExplorerMode.SAF
        val root = _treeRoot.value
        if (root != null) openExplorer(root) else {
            _explorerEntries.value = emptyList()
            _status.value = "Selecciona una carpeta con SAF"
        }
    }

    /** Lista una carpeta del explorador interno por ruta absoluta. */
    fun openInternalFolder(path: String) {
        _treeRoot.value = null
        scope.launch {
            _explorerEntries.value = javaFileSource.list(path)
        }
    }

    /** Lee un archivo de cualquier origen (interno java.io o SAF content://). */
    suspend fun readFile(path: String): ByteArray =
        if (path.startsWith("content://")) fileSource.read(path) else javaFileSource.read(path)

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

    fun clearSelected() {
        _selected.value = emptyList()
    }

    /** Añade archivos elegidos vía "Abrir con otra app" (URIs content://). */
    fun addExternalUris(uris: List<android.net.Uri>) {
        val entries = uris.mapIndexed { i, u ->
            FileEntry(
                name = runCatching {
                    val cursor = context.contentResolver.query(u, null, null, null, null)
                    cursor?.use {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && it.moveToFirst()) it.getString(idx) else "archivo_$i"
                    }
                }.getOrNull() ?: "archivo_$i",
                path = u.toString(),
                isDirectory = false,
                size = runCatching {
                    val cursor = context.contentResolver.query(u, null, null, null, null)
                    cursor?.use {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (idx >= 0 && it.moveToFirst()) it.getLong(idx) else 0L
                    }
                }.getOrNull() ?: 0L,
            )
        }
        _selected.value = (_selected.value + entries).distinctBy { it.path }
        _status.value = "${entries.size} archivo(s) añadido(s)"
    }

    fun sendTo(target: DeviceProfile, pin: String) {
        val files = _selected.value
        if (files.isEmpty()) { _status.value = "Selecciona archivos"; return }
        _status.value = "Enviando a ${target.name}..."
        scope.launch {
            val outbound = files.map { OutboundFile(it.name, readFile(it.path)) }
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

    /** Envio usando el destino y PIN persistentes del StateFlow. */
    fun send() {
        val target = _sendTarget.value
        val pin = _sendPin.value
        if (target == null) { _status.value = "Selecciona un dispositivo destino"; return }
        if (pin.length != 6) { _status.value = "Introduce el PIN de 6 digitos"; return }
        sendTo(target, pin)
    }

    fun stop() {
        discovery.stop()
        server.stop()
    }

    // ---------------- Archivos recibidos ----------------

    /** Recarga la lista de archivos recibidos desde la carpeta de descarga actual. */
    fun refreshReceivedFiles() {
        scope.launch {
            val treeUri = _downloadFolder.value
            val entries = if (treeUri != null) {
                val tree = DocumentFile.fromTreeUri(context, android.net.Uri.parse(treeUri))
                tree?.listFiles()
                    ?.filter { it.isFile }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map {
                        FileEntry(it.name ?: "(sin nombre)", it.uri.toString(), false, it.length())
                    } ?: emptyList()
            } else {
                defaultDownloadsDir.listFiles()
                    ?.filter { it.isFile }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { FileEntry(it.name, it.absolutePath, false, it.length()) } ?: emptyList()
            }
            _receivedFiles.value = entries
        }
    }

    /**
    /** Deduce el MIME por extension (apk, imagen, audio, video, texto, pdf). */
     * extensión (más fiable que contentResolver.getType para archivos de la app)
     * y soporta .apk (gestor de paquetes), imágenes, audio, vídeo, texto, PDF.
     */
    fun openReceived(entry: FileEntry): Boolean {
        return try {
            val mime = mimeForName(entry.name)
            val uri = if (entry.path.startsWith("content://")) {
                android.net.Uri.parse(entry.path)
            } else {
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", java.io.File(entry.path)
                )
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _status.value = "Abriendo ${entry.name}"
            true
        } catch (t: Throwable) {
            _status.value = "No hay app para abrir ${entry.name}: ${t.message}"
            false
        }
    }

    /** Mueve un archivo recibido a otra carpeta (destino elegido por el usuario). */
    fun moveReceived(entry: FileEntry, destTreeUri: android.net.Uri): Boolean {
        return try {
            val destTree = DocumentFile.fromTreeUri(context, destTreeUri) ?: return false
            // Crear destino con el mismo nombre.
            val dest = destTree.createFile(mimeForName(entry.name), entry.name) ?: return false
            // Copiar bytes (funciona para ambos orígenes file:// y content://).
            val srcUri = if (entry.path.startsWith("content://")) {
                android.net.Uri.parse(entry.path)
            } else {
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", java.io.File(entry.path)
                )
            }
            var copied = false
            context.contentResolver.openInputStream(srcUri)?.use { input ->
                context.contentResolver.openOutputStream(dest.uri)?.use { output ->
                    input.copyTo(output)
                    copied = true
                }
            }
            if (!copied) return false
            // Borrar el original según su origen.
            if (entry.path.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, android.net.Uri.parse(entry.path))?.delete()
            } else {
                java.io.File(entry.path).delete()
            }
            _status.value = "Movido: ${entry.name}"
            refreshReceivedFiles()
            true
        } catch (t: Throwable) {
            _status.value = "Error al mover: ${t.message}"
            false
        }
    }

    /** Deduce el MIME por extension (apk, imagen, audio, video, texto, pdf). */
    private fun mimeForName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "image/*"
            "mp3", "wav", "ogg", "m4a", "flac", "aac" -> "audio/*"
            "mp4", "mkv", "avi", "mov", "webm", "3gp" -> "video/*"
            "txt", "log", "md", "csv" -> "text/plain"
            "pdf" -> "application/pdf"
            "htm", "html" -> "text/html"
            "zip", "rar", "7z" -> "application/zip"
            "json", "xml" -> "application/$ext"
            else -> {
                val fromResolver = runCatching { context.contentResolver.getType(android.net.Uri.parse(name)) }.getOrNull()
                fromResolver ?: "*/*"
            }
        }
    }

    /** Renombra un archivo recibido. */
    fun renameReceived(entry: FileEntry, newName: String): Boolean {
        if (newName.isBlank()) return false
        return try {
            if (entry.path.startsWith("content://")) {
                val doc = DocumentFile.fromSingleUri(context, android.net.Uri.parse(entry.path)) ?: return false
                doc.renameTo(newName)
            } else {
                java.io.File(entry.path).renameTo(java.io.File(entry.path).resolveSibling(newName))
            }
            _status.value = "Renombrado a $newName"
            refreshReceivedFiles()
            true
        } catch (t: Throwable) {
            _status.value = "Error al renombrar: ${t.message}"
            false
        }
    }

    /** Borra un archivo recibido. */
    fun deleteReceived(entry: FileEntry): Boolean {
        return try {
            if (entry.path.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, android.net.Uri.parse(entry.path))?.delete() ?: false
            } else {
                java.io.File(entry.path).delete()
            }
            _status.value = "Borrado: ${entry.name}"
            refreshReceivedFiles()
            true
        } catch (t: Throwable) {
            _status.value = "Error al borrar: ${t.message}"
            false
        }
    }

    companion object {
        private const val KEY_DOWNLOAD_FOLDER = "download_folder_uri"
    }
}

/** Modo del explorador de archivos en Android. */
enum class ExplorerMode { INTERNAL, SAF }
