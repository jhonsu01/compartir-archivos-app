package io.compartirarchivos.shared.net

import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.model.DeviceType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.compartirarchivos.shared.model.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.math.max

/**
 * Resultado de un escaneo de subred: los dispositivos CompartirArchivos
 * que respondieron a GET /info con protocolVersion compatible.
 */
data class ScanResult(val devices: List<DeviceProfile>)

/**
 * Escanea una lista de subredes IPv4 locales buscando otros dispositivos
 * CompartirArchivos. Para cada IP candidata hace un GET /info con timeout
 * corto y concurrencia limitada; las que responden con un DeviceInfo válido
 * se devuelven como DeviceProfile.
 *
 * No usa mDNS/multicast: es TCP unicast saliente, así que no le afecta el
 * firewall de Windows inbound. Cubre toda la LAN (WiFi + Ethernet).
 */
class SubnetScanner(
    private val port: Int = Protocol.DEFAULT_PORT,
    private val selfId: String,
    private val connectTimeoutMs: Long = 400,
    private val requestTimeoutMs: Long = 700,
    private val maxConcurrency: Int = 50,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout)
        }
    }

    /** Escanea todas las [subnets] y devuelve los dispositivos hallados. */
    suspend fun scan(subnets: List<Subnet>): ScanResult = withContext(Dispatchers.IO) {
        val candidates = subnets.flatMap { hostsInSubnet(it) }.distinct()
        val sem = Semaphore(maxConcurrency)
        val found = coroutineScope {
            candidates.map { ip ->
                async {
                    sem.withPermit { probe(ip) }
                }
            }.awaitAll().filterNotNull()
        }
        ScanResult(found)
    }

    /** Hace GET /info a una IP; devuelve el DeviceProfile o null si no responde. */
    private suspend fun probe(ip: String): DeviceProfile? {
        return try {
            val info: DeviceInfo = client.get("http://$ip:$port${Protocol.Endpoint.INFO}") {
                contentType(ContentType.Application.Json)
                timeout { requestTimeoutMillis = requestTimeoutMs; connectTimeoutMillis = connectTimeoutMs }
            }.body()
            if (info.protocolVersion != Protocol.PROTOCOL_VERSION) return null
            if (info.id == selfId) return null // ignorarse a sí mismo
            DeviceProfile(
                id = info.id,
                name = info.name,
                type = info.type,
                host = ip,
                port = port,
            )
        } catch (_: ClientRequestException) {
            null
        } catch (_: Throwable) {
            null // timeout, conexión rechazada, parse error: no es un CompartirArchivos
        }
    }

    /** Genera todas las IPs host de una subred (excluye network y broadcast). */
    internal fun hostsInSubnet(subnet: Subnet): List<String> {
        val ipBytes = subnet.hostAddress.split('.').mapNotNull { it.toIntOrNull() }
        if (ipBytes.size != 4) return emptyList()
        val prefix = subnet.prefixLength.toInt().coerceIn(0, 32)
        if (prefix >= 31) return listOf(subnet.hostAddress) // punto a punto
        val ipInt = (ipBytes[0] shl 24) or (ipBytes[1] shl 16) or (ipBytes[2] shl 8) or ipBytes[3]
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val network = ipInt and mask
        val broadcast = network or (mask.inv())
        val total = max(0, broadcast - network - 1)
        if (total > 4096) return emptyList() // salvaguarda: no escanear redes enormes
        return (1..total).map { offset ->
            val addr = network + offset
            "${(addr shr 24) and 0xff}.${(addr shr 16) and 0xff}.${(addr shr 8) and 0xff}.${addr and 0xff}"
        }
    }
}
