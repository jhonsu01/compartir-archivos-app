package io.compartirarchivos.shared.discovery

import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType
import io.compartirarchivos.shared.net.Protocol
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Descubrimiento mDNS en JVM/Desktop usando JmDNS.
 *
 * - Anuncia este dispositivo como servicio [Protocol.MDNS_SERVICE_TYPE].
 * - Escucha servicios nuevos y los publica como [DiscoveryEvent.Found].
 */
class JmdnsDiscoveryService : DiscoveryService {

    @Volatile private var jmdns: JmDNS? = null
    @Volatile private var selfInfo: ServiceInfo? = null
    private val channel = Channel<DiscoveryEvent>(Channel.BUFFERED)

    private val listener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            // Hay que pedir la resolucion para tener host+puerto
            event.dns?.requestServiceInfo(event.type, event.name, true)
        }
        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info ?: return
            val host = info.inetAddresses.firstOrNull()?.hostAddress ?: return
            val deviceId = info.getPropertyString(Protocol.TXT_KEY_DEVICE_ID) ?: return
            val name = info.getPropertyString(Protocol.TXT_KEY_DEVICE_NAME) ?: event.name
            val type = DeviceType.fromName(info.getPropertyString(Protocol.TXT_KEY_DEVICE_TYPE))
            val device = DeviceProfile(
                id = deviceId,
                name = name,
                type = type,
                host = host,
                port = info.port,
            )
            if (deviceId != selfInfo?.getPropertyString(Protocol.TXT_KEY_DEVICE_ID)) {
                channel.trySend(DiscoveryEvent.Found(device))
            }
        }
        override fun serviceRemoved(event: ServiceEvent) {
            val id = event.info?.getPropertyString(Protocol.TXT_KEY_DEVICE_ID)
            if (id != null) channel.trySend(DiscoveryEvent.Lost(id))
        }
    }

    override val events: Flow<DiscoveryEvent> = channel.receiveAsFlow()

    override fun start(self: DeviceProfile, onStarted: () -> Unit) {
        if (jmdns != null) return
        // Bind a la interfaz de red real (WiFi/LAN), no a loopback ni a
        // adaptadores virtuales (Docker/VMware/Hyper-V). Esto es critico en
        // Windows donde InetAddress.getLocalHost() suele resolver a loopback.
        val iface = bestLanInterface()
        val mdns = if (iface != null) JmDNS.create(iface) else JmDNS.create()
        jmdns = mdns

        // Anunciar este dispositivo.
        val info = ServiceInfo.create(
            Protocol.MDNS_SERVICE_TYPE,
            self.name + "-" + self.id.take(6),
            self.port,
            0,
            0,
            mapOf(
                Protocol.TXT_KEY_VERSION to Protocol.PROTOCOL_VERSION.toString(),
                Protocol.TXT_KEY_DEVICE_ID to self.id,
                Protocol.TXT_KEY_DEVICE_NAME to self.name,
                Protocol.TXT_KEY_DEVICE_TYPE to self.type.name,
            )
        )
        mdns.registerService(info)
        selfInfo = info

        // Escuchar otros.
        mdns.addServiceListener(Protocol.MDNS_SERVICE_TYPE, listener)
        onStarted()
    }

    override fun stop() {
        try {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        } catch (_: Throwable) {}
        jmdns = null
        selfInfo = null
        channel.close()
    }
}

/**
 * Elige la mejor interfaz IPv4 para mDNS: la primera que este UP, no sea
 * loopback ni virtual, y tenga una IPv4 valida. Devuelve null si no halla
 * ninguna (entonces el llamador usara JmDNS.create() sobre 0.0.0.0).
 */
private fun bestLanInterface(): InetAddress? {
    return try {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedByDescending { it.isWirelessInterface() }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && !it.isLoopbackAddress }
            .firstOrNull()
    } catch (_: Throwable) {
        null
    }
}

/** Heuristica simple: nombres de interfaz WiFi suelen llevar wlan/wi-fi/wireless. */
private fun NetworkInterface.isWirelessInterface(): Boolean {
    val n = name.lowercase()
    return n.contains("wlan") || n.contains("wi-fi") || n.contains("wireless") || n.contains("wifi")
}

actual fun createDiscoveryService(): DiscoveryService = JmdnsDiscoveryService()
