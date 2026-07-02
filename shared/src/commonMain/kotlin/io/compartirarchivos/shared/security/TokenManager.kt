package io.compartirarchivos.shared.security

import io.compartirarchivos.shared.util.nowMillis
import kotlin.random.Random

/**
 * Token de sesion emitido tras un emparejamiento exitoso.
 * Caduca para forzar re-emparejamiento periódico (seccion 6).
 */
data class SessionToken(
    val value: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
) {
    fun isValid(nowMs: Long = nowMillis()): Boolean =
        nowMs in issuedAtMs..expiresAtMs
}

/**
 * Generador de tokens de sesion aleatorios (hex).
 * No pretende ser criptográficamente fuerte; la seguridad de transporte
 * la aporta TLS y la de acceso el PIN temporal.
 */
object TokenManager {
    private const val TOKEN_BYTES = 24
    private const val TTL_MS = 30L * 60 * 1000 // 30 minutos

    fun issue(nowMs: Long = nowMillis()): SessionToken {
        val bytes = ByteArray(TOKEN_BYTES)
        Random.Default.nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return SessionToken(hex, nowMs, nowMs + TTL_MS)
    }
}
