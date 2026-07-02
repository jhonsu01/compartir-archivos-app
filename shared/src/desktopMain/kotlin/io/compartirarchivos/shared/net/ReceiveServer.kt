package io.compartirarchivos.shared.net

import io.compartirarchivos.shared.model.DeviceInfo
import io.compartirarchivos.shared.model.PairRequest
import io.compartirarchivos.shared.model.PairResponse
import io.compartirarchivos.shared.model.TransferAccept
import io.compartirarchivos.shared.pairing.PairingManager
import io.compartirarchivos.shared.security.PinGenerator
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

/**
 * Callback que el host (Android/Desktop) implementa para decidir donde
 * guardar los archivos recibidos. Devuelve la ruta guardada o null si falla.
 */
fun interface FileSink {
    suspend fun save(fileName: String, bytes: ByteArray): String?
}

/**
 * Estado configurable de un receptor al arrancar.
 */
data class ReceiverConfig(
    val selfInfo: DeviceInfo,
    val port: Int = Protocol.DEFAULT_PORT,
    val pairing: PairingManager = PairingManager(),
)

/**
 * Servidor HTTP embebido (Netty) que actua como RECEPTOR.
 * Endpoints:
 *  GET  /info        -> DeviceInfo
 *  POST /pair        -> PairRequest -> PairResponse (valida PIN)
 *  POST /transfer    -> multipart (token query + archivos)
 *
 * (secciones 4.2, 4.3, 6)
 */
class ReceiveServer(
    private val config: ReceiverConfig,
    private val sink: FileSink,
) {
    @Volatile
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun start() {
        if (server != null) return
        val engine = embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
            configureModules()
            routes()
        }
        server = engine
        engine.start(wait = false)
    }

    fun stop() {
        @Suppress("UNCHECKED_CAST")
        (server as? io.ktor.server.engine.ApplicationEngine)?.stop(500, 1_500)
        server = null
    }

    /** Genera y devuelve un PIN fresco para mostrarlo en la UI del receptor. */
    fun issuePin(): String = config.pairing.issuePin()

    /** Informacion del dispositivo receptor. */
    fun selfInfo(): DeviceInfo = config.selfInfo

    private fun Application.configureModules() {
        install(ContentNegotiation) { json(json) }
        install(CallLogging)
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "unknown")))
            }
        }
    }

    private fun Application.routes() {
        routing {
            get(Protocol.Endpoint.INFO) {
                call.respond(config.selfInfo)
            }

            // Emparejamiento: el remitente envia el PIN mostrado por el receptor.
            post(Protocol.Endpoint.PAIR) {
                val req = call.receive<PairRequest>()
                if (!PinGenerator.isValidFormat(req.pin)) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        PairResponse(false, config.selfInfo.id, config.selfInfo.name, reason = "PIN invalido")
                    )
                    return@post
                }
                val token = config.pairing.verify(req.pin)
                if (token == null) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        PairResponse(false, config.selfInfo.id, config.selfInfo.name, reason = "PIN incorrecto o expirado")
                    )
                } else {
                    call.respond(
                        PairResponse(true, config.selfInfo.id, config.selfInfo.name, token = token.value)
                    )
                }
            }

            // Transferencia: multipart con token (query) + uno o varios archivos.
            post(Protocol.Endpoint.TRANSFER) {
                val tokenQ = call.request.queryParameters["token"]
                if (tokenQ == null || !config.pairing.isAuthorized(tokenQ)) {
                    call.respond(HttpStatusCode.Forbidden, TransferAccept(false, "No autorizado (token invalido/expirado)"))
                    return@post
                }
                val multipart = call.receiveMultipart()
                var saved = 0
                var failed = 0
                while (true) {
                    val part = multipart.readPart() ?: break
                    try {
                        if (part is PartData.FileItem) {
                            val name = part.originalFileName ?: "archivo_${System.currentTimeMillis()}"
                            val bytes = part.provider().toByteArray()
                            if (sink.save(name, bytes) != null) saved++ else failed++
                        }
                    } finally {
                        part.dispose()
                    }
                }
                call.respond(TransferAccept(saved > 0, "saved=$saved failed=$failed"))
            }

            // Ping de mantenimiento.
            get("/ping") { call.respondText("pong") }
        }
    }
}

/** Lee todos los bytes de un ByteReadChannel. */
private suspend fun ByteReadChannel.toByteArray(): ByteArray {
    val out = ByteArrayOutputStream()
    this.copyTo(out)
    return out.toByteArray()
}
