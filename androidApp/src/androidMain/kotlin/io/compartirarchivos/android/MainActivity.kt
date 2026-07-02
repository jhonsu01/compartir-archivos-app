package io.compartirarchivos.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import io.compartirarchivos.android.ui.AppRoot
import io.compartirarchivos.shared.fs.FileEntry

class MainActivity : ComponentActivity() {

    private lateinit var state: AndroidAppState

    // Selector SAF de carpeta raíz del explorador.
    private val openTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) state.setTreeRoot(uri) }

    // Selector SAF de carpeta de descarga.
    private val downloadFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) state.setDownloadFolder(uri) }

    // "Abrir con otra app": selector de uno o varios archivos (ACTION_OPEN_DOCUMENT).
    // Los archivos seleccionados se añaden a la lista de envío.
    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) state.addExternalUris(uris)
    }

    // Permiso de almacenamiento en runtime (para el explorador interno).
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val anyGranted = granted.values.any { it }
        if (anyGranted) {
            state.useInternalExplorer()
        } else {
            // Sin permiso, no se puede usar el explorador interno: ofrecer SAF.
            state.useSafExplorer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acquireMulticastLock()
        state = AndroidAppState(applicationContext)
        setContent {
            CompartirArchivosTheme {
                AppRoot(
                    state = state,
                    onPickRoot = { openTreeLauncher.launch(null) },
                    onPickDownloadFolder = { downloadFolderLauncher.launch(null) },
                    onOpenExternalFiles = { openFileLauncher.launch(arrayOf("*/*")) },
                    onRequestStoragePermission = { requestStoragePermission() },
                )
            }
        }
    }

    /** Solicita el permiso de lectura de almacenamiento según la versión de Android. */
    private fun requestStoragePermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            state.useInternalExplorer()
        } else {
            storagePermissionLauncher.launch(perms)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::state.isInitialized) state.stop()
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
            wifi.createMulticastLock("compartirarchivos_mdns").apply {
                setReferenceCounted(true); acquire()
            }
        } catch (_: Throwable) {}
    }
}

@Composable
private fun CompartirArchivosTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        background = Color(0xFF101418),
        surface = Color(0xFF161B22),
        primary = Color(0xFF38BDF8),
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }
}
