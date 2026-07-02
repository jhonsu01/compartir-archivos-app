package io.compartirarchivos.shared.model

import kotlinx.serialization.Serializable

/* ====================================================================
 * PROTOCOLO DE COMUNICACION (seccion 9 de la guia)
 * Mensajes JSON intercambiados entre dispositivos via HTTP/Ktor.
 * ==================================================================== */

/** Tipo de mensaje del protocolo. */
enum class MessageType {
    PAIR_REQUEST,
    PAIR_RESPONSE,
    TRANSFER_OFFER,
    TRANSFER_ACCEPT,
    TRANSFER_REJECT,
    INFO,
    PING
}

/** Cabecera comun de todo mensaje. */
@Serializable
data class ProtocolEnvelope(
    val type: MessageType,
    val payload: String,   // JSON serializado del mensaje concreto
)

/* --- Emparejamiento (seccion 4.2) --- */

@Serializable
data class PairRequest(
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val pin: String,       // PIN de 6 digitos mostrado por el receptor
)

@Serializable
data class PairResponse(
    val accepted: Boolean,
    val receiverId: String,
    val receiverName: String,
    val token: String = "",   // token de sesion si accepted
    val reason: String = "",  // motivo si rejected
)

/* --- Oferta de transferencia (antes de enviar bytes) --- */

@Serializable
data class FileMeta(
    val name: String,
    val size: Long,
    val mimeType: String = "application/octet-stream",
)

@Serializable
data class TransferOffer(
    val token: String,        // token obtenido del emparejamiento
    val files: List<FileMeta>,
)

@Serializable
data class TransferAccept(
    val accepted: Boolean,
    val reason: String = "",
)

/* --- Info del dispositivo receptor (GET /info) --- */

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val type: DeviceType,
    val requiresPairing: Boolean,
    val protocolVersion: Int = 1,
)
