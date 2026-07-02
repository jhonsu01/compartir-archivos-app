package io.compartirarchivos.shared.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.compartirarchivos.shared.identity.appContext
import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType
import io.compartirarchivos.shared.net.Protocol
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.net.InetAddress

/**
 * Descubrimiento mDNS en Android usando NsdManager.
 *
 * Anuncia este dispositivo y escucha servicios del mismo tipo.
 */
class NsdDiscoveryService : DiscoveryService {

    @Volatile private var nsdManager: NsdManager? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null
    @Volatile private var selfId: String? = null
    private val channel = Channel<DiscoveryEvent>(Channel.BUFFERED)

    private val ctx: Context
        get() = appContext ?: error("SharedInitializer.init(context) requerido")

    override val events: Flow<DiscoveryEvent> = channel.receiveAsFlow()

    override fun start(self: DeviceProfile, onStarted: () -> Unit) {
        if (nsdManager != null) return
        selfId = self.id
        val manager = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = manager

        // 1) Registrar este dispositivo.
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = self.name + "-" + self.id.take(6)
            serviceType = Protocol.MDNS_SERVICE_TYPE
            port = self.port
            setAttribute(Protocol.TXT_KEY_VERSION, Protocol.PROTOCOL_VERSION.toString())
            setAttribute(Protocol.TXT_KEY_DEVICE_ID, self.id)
            setAttribute(Protocol.TXT_KEY_DEVICE_NAME, self.name)
            setAttribute(Protocol.TXT_KEY_DEVICE_TYPE, self.type.name)
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                channel.trySend(DiscoveryEvent.Error("Registro NSD fallido: $errorCode"))
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)

        // 2) Descubrir otros dispositivos.
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                channel.trySend(DiscoveryEvent.Error("Discovery start fallido: $errorCode"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val deviceId = info.attributes[Protocol.TXT_KEY_DEVICE_ID]
                            ?.decodeToString() ?: return
                        if (deviceId == selfId) return
                        val name = info.attributes[Protocol.TXT_KEY_DEVICE_NAME]
                            ?.decodeToString() ?: info.serviceName
                        val type = DeviceType.fromName(
                            info.attributes[Protocol.TXT_KEY_DEVICE_TYPE]?.decodeToString()
                        )
                        val host = info.host?.hostAddress ?: return
                        channel.trySend(
                            DiscoveryEvent.Found(
                                DeviceProfile(deviceId, name, type, host, info.port)
                            )
                        )
                    }
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val id = serviceInfo.serviceName
                channel.trySend(DiscoveryEvent.Lost(id))
            }
        }
        manager.discoverServices(Protocol.MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        onStarted()
    }

    override fun stop() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Throwable) {}
        nsdManager = null
        channel.close()
    }
}

actual fun createDiscoveryService(): DiscoveryService = NsdDiscoveryService()
