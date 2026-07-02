package io.compartirarchivos.shared.client

import io.compartirarchivos.shared.model.DeviceInfo
import io.compartirarchivos.shared.model.DeviceType
import io.compartirarchivos.shared.model.PairRequest
import io.compartirarchivos.shared.model.PairResponse
import io.compartirarchivos.shared.model.TransferAccept
import io.compartirarchivos.shared.net.Protocol
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Un archivo a enviar, ya materializado en memoria.
 * (Para archivos grandes >2GB se recomendaria streaming, fuera del scope v0.1)
 */
data class OutboundFile(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String = "application/octet-stream",
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is OutboundFile && name == other.name)
    override fun hashCode(): Int = name.hashCode()
}

/**
 * Resultado de un envio completo (emparejamiento + transferencia).
 */
sealed interface TransferResult {
    data class Success(val accepted: TransferAccept) : TransferResult
    data class PairingFailed(val reason: String) : TransferResult
    data class Error(val cause: Throwable) : TransferResult
}

/**
 * Cliente HTTP del EMISOR. Secuencia:
 *   1) GET  /info   (comprueba que el receptor esta vivo)
 *   2) POST /pair   (envia PIN -> recibe token)
 *   3) POST /transfer?token=... (sube multipart)
 *
 * (secciones 4.2, 4.3)
 */
class TransferClient(
    private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    /** GET /info -> informacion del receptor. */
    suspend fun fetchInfo(): DeviceInfo =
        client.get("$baseUrl${Protocol.Endpoint.INFO}").body()

    /** POST /pair con el PIN introducido por el usuario. */
    suspend fun pair(
        selfDeviceId: String,
        selfDeviceName: String,
        selfDeviceType: DeviceType,
        pin: String,
    ): PairResponse {
        val req = PairRequest(selfDeviceId, selfDeviceName, selfDeviceType, pin)
        return client.post("$baseUrl${Protocol.Endpoint.PAIR}") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    /** POST /transfer con los archivos (requiere token valido). */
    suspend fun transfer(token: String, files: List<OutboundFile>): TransferAccept {
        return client.post("$baseUrl${Protocol.Endpoint.TRANSFER}") {
            parameter("token", token)
            setBody(MultiPartFormDataContent(
                formData {
                    files.forEach { f ->
                        append("files", f.bytes, Headers.build {
                            append(HttpHeaders.ContentType, f.mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"${f.name}\"")
                        })
                    }
                }
            ))
        }.body()
    }

    /** Orquestacion completa: info -> pair -> transfer. */
    suspend fun sendAll(
        selfDeviceId: String,
        selfDeviceName: String,
        selfDeviceType: DeviceType,
        pin: String,
        files: List<OutboundFile>,
    ): TransferResult {
        return try {
            // 1) info (sanity, opcional)
            fetchInfo()
            // 2) pair
            val pair = pair(selfDeviceId, selfDeviceName, selfDeviceType, pin)
            if (!pair.accepted) return TransferResult.PairingFailed(pair.reason.ifEmpty { "PIN rechazado" })
            // 3) transfer
            val accept = transfer(pair.token, files)
            TransferResult.Success(accept)
        } catch (t: Throwable) {
            TransferResult.Error(t)
        }
    }

    fun close() = client.close()
}
