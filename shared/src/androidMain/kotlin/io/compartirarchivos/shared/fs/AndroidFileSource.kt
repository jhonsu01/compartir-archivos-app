package io.compartirarchivos.shared.fs

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.compartirarchivos.shared.identity.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Explorador de archivos Android basado en Storage Access Framework.
 *
 * Trabaja con URIs tree (content://...). La UI debe obtener el URI raiz
 * via ACTION_OPEN_DOCUMENT_TREE y pasarlo como [path].
 */
class AndroidFileSource : FileSource {

    private val ctx: Context
        get() = appContext ?: error("SharedInitializer.init(context) requerido")

    override suspend fun list(path: String?): List<FileEntry> = withContext(Dispatchers.IO) {
        if (path == null) return@withContext emptyList()
        val root = DocumentFile.fromTreeUri(ctx, Uri.parse(path)) ?: return@withContext emptyList()
        root.listFiles().map { doc ->
            FileEntry(
                name = doc.name ?: "(sin nombre)",
                path = doc.uri.toString(),
                isDirectory = doc.isDirectory,
                size = if (doc.isDirectory) 0L else doc.length(),
            )
        }.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        val uri = Uri.parse(path)
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo abrir $path")
    }

    override fun defaultRootLabel(): String = "storage://"
}

actual fun createFileSource(): FileSource = AndroidFileSource()
