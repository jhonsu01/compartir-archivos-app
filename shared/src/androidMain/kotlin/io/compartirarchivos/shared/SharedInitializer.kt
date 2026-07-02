package io.compartirarchivos.shared

import android.content.Context
import io.compartirarchivos.shared.identity.appContext

/**
 * Inicializacion del modulo shared en Android.
 * Llamar desde android.app.Application.onCreate().
 */
object SharedInitializer {
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
    }
}
