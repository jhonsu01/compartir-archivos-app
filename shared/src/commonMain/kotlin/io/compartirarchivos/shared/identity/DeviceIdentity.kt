package io.compartirarchivos.shared.identity

import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType

/**
 * Identidad propia del dispositivo. Se persiste por plataforma:
 *  - Android: en SharedPreferences
 *  - Desktop: archivo en user home
 *
 * El [id] debe ser estable entre ejecuciones para que el mDNS no
 * genere duplicados.
 */
data class DeviceIdentity(
    val id: String,
    val name: String,
    val type: DeviceType,
)

/**
 * Devuelve la identidad propia, generandola y persistiendola si no existe.
 * Implementacion expect/actual.
 */
expect fun loadOrCreateIdentity(defaultName: String, type: DeviceType): DeviceIdentity

/** Construye el DeviceProfile a anunciar por mDNS. */
fun DeviceIdentity.toProfile(port: Int, host: String = "0.0.0.0"): DeviceProfile =
    DeviceProfile(id = id, name = name, type = type, host = host, port = port)
