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

    // Cola de resoluciones: NsdManager permite solo 1 resolve concurrente.
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    @Volatile private var resolving = false
    private val resolveLock = Any()

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
                // NsdManager solo permite UNA resolucion concurrente. Encolamos
                // y resolvemos de uno en uno para no perder servicios.
                enqueueResolve(serviceInfo)
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

    /** Encola un servicio para resolverlo cuando el resolve anterior termine. */
    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(resolveLock) {
            pendingResolves.addLast(info)
            if (resolving) return
            resolving = true
        }
        drainResolveQueue()
    }

    /** Resuelve servicios de la cola de uno en uno. */
    private fun drainResolveQueue() {
        val manager = nsdManager ?: return
        val info = synchronized(resolveLock) { pendingResolves.removeFirstOrNull() }
        if (info == null) {
            synchronized(resolveLock) { resolving = false }
            return
        }
        try {
            manager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    try {
                        val deviceId = resolved.attributes[Protocol.TXT_KEY_DEVICE_ID]
                            ?.decodeToString()
                        if (deviceId != null && deviceId != selfId) {
                            val name = resolved.attributes[Protocol.TXT_KEY_DEVICE_NAME]
                                ?.decodeToString() ?: resolved.serviceName
                            val type = DeviceType.fromName(
                                resolved.attributes[Protocol.TXT_KEY_DEVICE_TYPE]?.decodeToString()
                            )
                            val host = resolved.host?.hostAddress
                            if (host != null) {
                                channel.trySend(
                                    DiscoveryEvent.Found(
                                        DeviceProfile(deviceId, name, type, host, resolved.port)
                                    )
                                )
                            }
                        }
                    } finally {
                        drainResolveQueue() // siguiente
                    }
                }
                override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                    // No tragamos el error silenciosamente: lo reportamos y
                    // continuamos con el siguiente servicio de la cola.
                    channel.trySend(DiscoveryEvent.Error("Resolve fallido (code $errorCode): ${failed.serviceName}"))
                    drainResolveQueue()
                }
            })
        } catch (t: Throwable) {
            // resolveService puede lanzar si ya hay uno en curso; reintentamos.
            channel.trySend(DiscoveryEvent.Error("resolveService exception: ${t.message}"))
            synchronized(resolveLock) {
                resolving = false
            }
        }
    }
}
