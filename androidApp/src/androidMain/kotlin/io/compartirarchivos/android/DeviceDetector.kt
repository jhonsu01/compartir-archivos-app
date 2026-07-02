package io.compartirarchivos.android

import android.content.Context
import android.content.res.Configuration
import android.net.wifi.WifiManager
import io.compartirarchivos.shared.model.DeviceType

/**
 * Detecta el tipo de dispositivo para adaptar la UI (seccion 5).
 */
object DeviceDetector {

    fun detectType(context: Context): DeviceType {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) {
            DeviceType.TV
        } else {
            DeviceType.MOBILE
        }
    }

    /** Nombre legible del dispositivo. */
    fun deviceName(): String {
        val model = android.os.Build.MODEL
        val brand = android.os.Build.MANUFACTURER
        return "$brand $model".trim().ifBlank { "Android" }
    }

    /**
     * Obtiene la IP local WiFi. Requiere permiso ACCESS_WIFI_STATE.
     * (mDNS se encarga del descubrimiento real; esto es informativo.)
     */
    @Suppress("DEPRECATION")
    fun localWifiIp(context: Context): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wm?.connectionInfo?.ipAddress ?: 0
            if (ip == 0) "0.0.0.0" else
                "%d.%d.%d.%d".format(ip and 0xff, (ip shr 8) and 0xff, (ip shr 16) and 0xff, (ip shr 24) and 0xff)
        } catch (_: Throwable) { "0.0.0.0" }
    }
}
