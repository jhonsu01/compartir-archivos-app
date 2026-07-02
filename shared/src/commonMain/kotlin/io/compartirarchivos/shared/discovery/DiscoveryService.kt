package io.compartirarchivos.shared.discovery

import io.compartirarchivos.shared.model.DeviceProfile
import kotlinx.coroutines.flow.Flow

/**
 * Eventos del descubrimiento mDNS en red local (seccion 4.1).
 */
sealed interface DiscoveryEvent {
    data class Found(val device: DeviceProfile) : DiscoveryEvent
    data class Lost(val deviceId: String) : DiscoveryEvent
    data class Error(val message: String) : DiscoveryEvent
}

/**
 * Servicio de descubrimiento de dispositivos en la LAN.
 * Implementacion expect/actual:
 *  - Android: NsdManager
 *  - Desktop (JVM): JmDNS
 */
interface DiscoveryService {
    /** Flujo de eventos (Found/Lost) para que la UI reaccione. */
    val events: Flow<DiscoveryEvent>

    /** Empieza a anunciar este dispositivo y a buscar otros. */
    fun start(self: DeviceProfile, onStarted: () -> Unit = {})

    /** Detiene anuncio y discovery. */
    fun stop()
}

/** Factory expect/actual: cada plataforma construye su implementacion. */
expect fun createDiscoveryService(): DiscoveryService
