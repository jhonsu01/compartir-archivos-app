package io.compartirarchivos.shared.fs

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Explorador de archivos Desktop basado en java.nio.file.
 * Raiz por defecto: el directorio home del usuario.
 */
class DesktopFileSource : FileSource {
    private val home: Path = Paths.get(System.getProperty("user.home"))

    override suspend fun list(path: String?): List<FileEntry> {
        val base = path?.let { Paths.get(it) } ?: home
        if (!Files.exists(base) || !Files.isDirectory(base)) return emptyList()
        return Files.list(base).use { stream ->
            stream
                .map { p ->
                    FileEntry(
                        name = p.fileName?.toString() ?: p.toString(),
                        path = p.toString(),
                        isDirectory = Files.isDirectory(p),
                        size = if (Files.isDirectory(p)) 0L else Files.size(p),
                    )
                }
                .toList()
                .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    override suspend fun read(path: String): ByteArray = Files.readAllBytes(Paths.get(path))

    override fun defaultRootLabel(): String = home.toString()
}

actual fun createFileSource(): FileSource = DesktopFileSource()
