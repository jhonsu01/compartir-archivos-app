package io.compartirarchivos.shared.identity

import io.compartirarchivos.shared.model.DeviceType
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Identidad persistida en un archivo del home del usuario (Desktop).
 */
actual fun loadOrCreateIdentity(defaultName: String, type: DeviceType): DeviceIdentity {
    val dir: Path = java.nio.file.Paths.get(System.getProperty("user.home"), ".compartirarchivos")
    Files.createDirectories(dir)
    val file = dir.resolve("identity.txt")
    return if (Files.exists(file)) {
        val lines = Files.readAllLines(file)
        val id = lines.getOrNull(0)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val name = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: defaultName
        DeviceIdentity(id, name, type)
    } else {
        val id = UUID.randomUUID().toString()
        Files.write(file, listOf(id, defaultName))
        DeviceIdentity(id, defaultName, type)
    }
}
