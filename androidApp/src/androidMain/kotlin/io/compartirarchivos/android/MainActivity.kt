package io.compartirarchivos.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.compartirarchivos.android.ui.AppRoot
import io.compartirarchivos.shared.model.DeviceType

class MainActivity : ComponentActivity() {

    private lateinit var state: AndroidAppState

    private val openTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) state.setTreeRoot(uri)
    }

    private val downloadFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) state.setDownloadFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Multicast lock para que mDNS funcione con WiFi (CHANGE_WIFI_MULTICAST_STATE)
        acquireMulticastLock()
        // Conservar IP real: pedimos permisos de red en runtime solo si hace falta
        state = AndroidAppState(applicationContext)
        setContent {
            CompartirArchivosTheme {
                AppRoot(
                    state = state,
                    onPickRoot = { openTreeLauncher.launch(null) },
                    onPickDownloadFolder = { downloadFolderLauncher.launch(null) },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::state.isInitialized) state.stop()
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
            val lock = wifi.createMulticastLock("compartirarchivos_mdns").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Throwable) {}
    }
}

@Composable
private fun CompartirArchivosTheme(content: @Composable () -> Unit) {
    // Modo oscuro obligatorio (seccion 5)
    val scheme = darkColorScheme(
        background = Color(0xFF101418),
        surface = Color(0xFF161B22),
        primary = Color(0xFF38BDF8),
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }
}
