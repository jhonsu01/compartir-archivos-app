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

/**
 * Factory común: devuelve el descubrimiento por escaneo de subred, que cubre
 * toda la LAN (WiFi + Ethernet) automáticamente y no depende de mDNS/multicast
 * (que el firewall de Windows bloquea inbound).
 */
fun createDiscoveryService(): DiscoveryService = SubnetScannerDiscoveryService()
