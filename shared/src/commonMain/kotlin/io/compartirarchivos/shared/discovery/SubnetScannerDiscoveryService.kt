package io.compartirarchivos.shared.discovery

import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.net.SubnetScanner
import io.compartirarchivos.shared.net.localSubnets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Implementación de [DiscoveryService] basada en escaneo de subred.
 *
 * No usa mDNS/multicast (que el firewall de Windows bloquea inbound). En su
 * lugar, recorre todas las IPs de cada subred local (WiFi + Ethernet) y hace
 * GET /info a cada una; las que responden con un DeviceInfo válido se publican
 * como [DiscoveryEvent.Found]. Re-escanea cada [rescanIntervalMs] para mantener
 * la lista viva.
 *
 * Cubre TODA la LAN automáticamente, sin que el usuario introduzca ninguna IP.
 */
class SubnetScannerDiscoveryService(
    private val rescanIntervalMs: Long = 10_000,
) : DiscoveryService {

    private val channel = Channel<DiscoveryEvent>(Channel.BUFFERED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    @Volatile private var self: DeviceProfile? = null
    @Volatile private var seenIds: MutableSet<String> = mutableSetOf()

    override val events: Flow<DiscoveryEvent> = channel.receiveAsFlow()

    override fun start(self: DeviceProfile, onStarted: () -> Unit) {
        if (loopJob != null) return
        this.self = self
        loopJob = scope.launch {
            // Escaneo inicial inmediato.
            scanOnce()
            onStarted()
            // Re-escaneos periódicos.
            while (true) {
                delay(rescanIntervalMs)
                scanOnce()
            }
        }
    }

    private suspend fun scanOnce() {
        val me = self ?: return
        val subnets = localSubnets()
        val scanner = SubnetScanner(selfId = me.id)
        val result = scanner.scan(subnets)
        val currentIds = result.devices.map { it.id }.toMutableSet()

        // Emitir los nuevos.
        for (dev in result.devices) {
            if (dev.id !in seenIds) {
                channel.trySend(DiscoveryEvent.Found(dev))
            }
        }
        // Emitir "Lost" para los que ya no están.
        for (oldId in seenIds.toList()) {
            if (oldId !in currentIds && oldId != me.id) {
                channel.trySend(DiscoveryEvent.Lost(oldId))
            }
        }
        seenIds = currentIds
    }

    override fun stop() {
        loopJob?.cancel()
        loopJob = null
        channel.close()
    }
}
