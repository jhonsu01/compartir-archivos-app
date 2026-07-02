import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

version = "0.1.0"
group = "io.compartirarchivos.desktop"

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                @Suppress("DEPRECATION")
                implementation(compose.material3)
                @Suppress("DEPRECATION")
                implementation(compose.materialIconsExtended)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.compartirarchivos.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)

            packageName = "CompartirArchivos"
            packageVersion = "0.4.0"
            description = "Comparte archivos por red local entre Android y Windows"
            vendor = "CompartirArchivos"
            copyright = "© 2026 CompartirArchivos"

            // Iconos (opcional si no existen; jpackage usa default)
            // iconFile.set(project.file("icons/app.ico"))

            windows {
                menuGroup = "CompartirArchivos"
                upgradeUuid = "b3f2a1c4-5d6e-4f7a-8b9c-0d1e2f3a4b5c"
                dirChooser = true
                perUserInstall = true
                // shortcut = true
            }

            // JVM args para el runtime empaquetado
            jvmArgs += listOf("-Xmx512m")
        }
    }
}

// v0.x: desactivar ProGuard en release (el task corre en modo passthrough,
// copiando el jar sin ofuscar, para no romper la cadena del distribuible).
compose.desktop.application.buildTypes.release.proguard {
    isEnabled.set(false)
}
