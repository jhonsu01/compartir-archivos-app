package io.compartirarchivos.shared.model

/**
 * Tipo de dispositivo, para adaptar la UI (iconos compactos en movil,
 * grandes y espaciados en TV) segun seccion 5 de la guia.
 */
enum class DeviceType {
    MOBILE,
    TV,
    DESKTOP;

    val iconSizeDp: Int
        get() = when (this) {
            TV -> 56      // iconos grandes + espaciados
            DESKTOP -> 40
            MOBILE -> 28  // compactos
        }

    companion object {
        fun fromName(name: String?): DeviceType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MOBILE
    }
}
