package io.compartirarchivos.shared.net

/**
 * Constantes del protocolo y servicio mDNS (seccion 9 de la guia).
 */
object Protocol {
    /** Tipo de servicio registrado via mDNS / NSD. */
    const val MDNS_SERVICE_TYPE = "_compartirarchivos._tcp."

    /** Clave para el campo TXT del servicio mDNS con la version del protocolo. */
    const val TXT_KEY_VERSION = "v"

    /** Clave para el campo TXT con el identificador del dispositivo. */
    const val TXT_KEY_DEVICE_ID = "id"

    /** Clave para el campo TXT con el nombre legible del dispositivo. */
    const val TXT_KEY_DEVICE_NAME = "name"

    /** Clave para el campo TXT con el tipo de dispositivo (MOBILE/TV/DESKTOP). */
    const val TXT_KEY_DEVICE_TYPE = "type"

    /** Puerto base donde escucha el servidor HTTP embebido. */
    const val DEFAULT_PORT = 48721

    /** Version actual del protocolo de aplicacion. */
    const val PROTOCOL_VERSION = 1

    /** Longitud del PIN de emparejamiento (seccion 4.2). */
    const val PIN_LENGTH = 6

    /** Validez del PIN en milisegundos (30-60s segun guia). */
    const val PIN_TTL_MS = 60_000L

    /** Endpoint HTTP del servidor receptor. */
    object Endpoint {
        const val INFO = "/info"
        const val PAIR = "/pair"
        const val TRANSFER = "/transfer"
    }
}
