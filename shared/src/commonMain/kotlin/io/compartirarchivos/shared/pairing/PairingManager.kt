package io.compartirarchivos.shared.pairing

import io.compartirarchivos.shared.model.DeviceProfile
import io.compartirarchivos.shared.net.Protocol
import io.compartirarchivos.shared.security.PinGenerator
import io.compartirarchivos.shared.security.SessionToken
import io.compartirarchivos.shared.security.TokenManager
import io.compartirarchivos.shared.util.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado de un desafio PIN activo en el receptor.
 */
data class ActivePin(
    val pin: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
) {
    fun isValid(nowMs: Long = nowMillis()): Boolean =
        nowMs < expiresAtMs
}

/**
 * Gestiona el ciclo de vida del PIN y los tokens de sesion del lado RECEPTOR.
 * El receptor muestra un PIN; el remitente lo introduce para obtener un token.
 *
 * (seccion 4.2 + 6)
 */
class PairingManager {

    private val _activePin = MutableStateFlow<ActivePin?>(null)
    val activePin: StateFlow<ActivePin?> = _activePin

    private val _sessions = MutableStateFlow<Map<String, SessionToken>>(emptyMap())
    val sessions: StateFlow<Map<String, SessionToken>> = _sessions

    /** Emite un PIN nuevo (caduca en [Protocol.PIN_TTL_MS]). */
    fun issuePin(nowMs: Long = nowMillis()): String {
        val pin = PinGenerator.generate()
        _activePin.value = ActivePin(pin, nowMs, nowMs + Protocol.PIN_TTL_MS)
        return pin
    }

    /** Valida un PIN contra el activo. Si ok, emite token de sesion. */
    fun verify(pin: String, nowMs: Long = nowMillis()): SessionToken? {
        val active = _activePin.value ?: return null
        if (!active.isValid(nowMs)) {
            _activePin.value = null
            return null
        }
        if (!pin.equals(active.pin, ignoreCase = false)) return null

        // PIN consumido: invalidarlo (un PIN = un emparejamiento) y emitir token.
        _activePin.value = null
        val token = TokenManager.issue(nowMs)
        _sessions.value = _sessions.value + (token.value to token)
        return token
    }

    /** Comprueba validez de un token para autorizar una transferencia. */
    fun isAuthorized(token: String, nowMs: Long = nowMillis()): Boolean {
        val s = _sessions.value[token] ?: return false
        if (!s.isValid(nowMs)) {
            revoke(token)
            return false
        }
        return true
    }

    fun revoke(token: String) {
        _sessions.value = _sessions.value - token
    }

    fun clearAll() {
        _activePin.value = null
        _sessions.value = emptyMap()
    }
}
