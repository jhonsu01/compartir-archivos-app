package io.compartirarchivos.shared.model

import kotlinx.serialization.Serializable

/**
 * Perfil de un dispositivo detectado en la red local.
 * Cada instancia expone un servidor HTTP embebido en [host]:[port].
 */
@Serializable
data class DeviceProfile(
    val id: String,
    val name: String,
    val type: DeviceType,
    val host: String,
    val port: Int,
) {
    val httpUrl: String get() = "http://$host:$port"
}
