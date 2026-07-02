package io.compartirarchivos.shared.fs

/**
 * Una entrada del explorador de archivos (seccion 4.4).
 */
data class FileEntry(
    val name: String,
    val path: String,        // uri o ruta absoluta
    val isDirectory: Boolean,
    val size: Long,
    val mimeType: String = if (isDirectory) "inode/directory" else "application/octet-stream",
)

/**
 * Fuente de archivos multiplataforma (expect/actual):
 *  - Android: Storage Access Framework (SAF)
 *  - Desktop: java.nio.file.FileSystem
 *
 * Expone listar y leer, desacoplando la UI del sistema de archivos.
 */
interface FileSource {
    /** Lista el contenido de una carpeta (path). Raiz si [path] es null. */
    suspend fun list(path: String?): List<FileEntry>

    /** Lee los bytes de un archivo (para enviarlo). */
    suspend fun read(path: String): ByteArray

    /** Nombre legible de la raiz por defecto. */
    fun defaultRootLabel(): String
}

expect fun createFileSource(): FileSource
