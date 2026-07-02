package io.compartirarchivos.android

import android.app.Application
import io.compartirarchivos.shared.SharedInitializer

/**
 * Application: inicializa el modulo shared (Context global) y Napier.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SharedInitializer.init(this)
    }
}
