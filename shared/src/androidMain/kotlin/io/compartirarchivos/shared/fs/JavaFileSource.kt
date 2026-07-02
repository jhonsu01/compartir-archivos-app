package io.compartirarchivos.shared.fs

import android.content.Context
import android.os.Environment
import io.compartirarchivos.shared.identity.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Explorador interno basado en java.io.File sobre el almacenamiento publico
 * (Downloads, DCIM, Pictures, Documents, Music). NO depende de ninguna app
 * externa: funciona en dispositivos sin gestor de archivos.
 *
 * Requiere permisos READ_EXTERNAL_STORAGE (API <= 32) o READ_MEDIA_* (API 33+).
 * La UI debe pedirlos en runtime antes de listar.
 */
class JavaFileSource : FileSource {

    private val ctx: Context
        get() = appContext ?: error("SharedInitializer.init(context) requerido")

    /** Raiz del almacenamiento publico compartido. */
    private val storageRoot: File
        get() = Environment.getExternalStorageDirectory()

    /** Puntos de entrada legibles para mostrar al usuario. */
    fun quickRoots(): List<FileEntry> {
        val root = storageRoot
        val names = listOf("Download", "Downloads", "DCIM", "Pictures", "Music", "Documents", "Movies")
        return names.mapNotNull { n ->
            val f = File(root, n)
            if (f.exists() && f.isDirectory && f.canRead()) {
                FileEntry(name = n, path = f.absolutePath, isDirectory = true, size = 0L)
            } else null
        }.ifEmpty {
            // Si ninguno existe, devolver al menos la raíz.
            listOf(FileEntry(root.name ?: "Almacenamiento", root.absolutePath, true, 0L))
        }
    }

    override suspend fun list(path: String?): List<FileEntry> = withContext(Dispatchers.IO) {
        val base = path?.let { File(it) } ?: storageRoot
        if (!base.exists() || !base.isDirectory || !base.canRead()) return@withContext emptyList()
        base.listFiles()
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { f ->
                FileEntry(
                    name = f.name,
                    path = f.absolutePath,
                    isDirectory = f.isDirectory,
                    size = if (f.isDirectory) 0L else f.length(),
                )
            } ?: emptyList()
    }

    override suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        File(path).readBytes()
    }

    override fun defaultRootLabel(): String = storageRoot.absolutePath
}
