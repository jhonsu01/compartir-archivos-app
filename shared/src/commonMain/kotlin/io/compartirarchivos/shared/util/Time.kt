package io.compartirarchivos.shared.util

/**
 * Hora epoch en milisegundos (expect/actual).
 * Evita depender de kotlinx-datetime en commonMain para casos simples.
 */
expect fun nowMillis(): Long
