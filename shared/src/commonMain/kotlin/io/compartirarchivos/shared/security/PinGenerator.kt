package io.compartirarchivos.shared.security

import io.compartirarchivos.shared.net.Protocol
import kotlin.random.Random

/**
 * Generacion y validacion del PIN de emparejamiento (seccion 4.2).
 * Determinista en cuanto a formato: 6 digitos, sin ambiguedades.
 */
object PinGenerator {

    /** Genera un PIN de [Protocol.PIN_LENGTH] digitos (string). */
    fun generate(): String {
        // Evitar digitos ambiguos? No: el PIN se escribe, no se dicta.
        val digits = CharArray(Protocol.PIN_LENGTH)
        repeat(Protocol.PIN_LENGTH) { i ->
            digits[i] = '0' + Random.nextInt(0, 10)
        }
        return digits.concatToString()
    }

    /** Valida formato del PIN (solo digitos, longitud correcta). */
    fun isValidFormat(pin: String): Boolean =
        pin.length == Protocol.PIN_LENGTH && pin.all { it.isDigit() }
}
