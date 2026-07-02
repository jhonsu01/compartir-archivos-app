package io.compartirarchivos.shared.identity

import android.content.Context
import android.provider.Settings
import io.compartirarchivos.shared.model.DeviceType
import java.util.UUID

/**
 * Identidad persistida en SharedPreferences (Android).
 *
 * Requiere el Context de la app. Se inyecta via [appContext] (variable
 * de modulo inicializada desde la Application).
 */
actual fun loadOrCreateIdentity(defaultName: String, type: DeviceType): DeviceIdentity {
    val ctx = appContext
        ?: error("Llama a SharedInitializer.init(context) desde tu clase Application")
    val prefs = ctx.getSharedPreferences("compartirarchivos_identity", Context.MODE_PRIVATE)
    val existingId = prefs.getString("id", null)
    val id = existingId ?: run {
        // Fallback: ANDROID_ID acortado + UUID si no disponible
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        val newId = (androidId?.take(12) ?: "") + UUID.randomUUID().toString().take(8)
        prefs.edit().putString("id", newId).putString("name", defaultName).apply()
        newId
    }
    val name = prefs.getString("name", defaultName) ?: defaultName
    return DeviceIdentity(id, name, type)
}

/** Contexto global de la app; debe inicializarse desde Application.onCreate(). */
internal var appContext: Context? = null
