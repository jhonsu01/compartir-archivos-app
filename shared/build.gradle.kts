import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
}

version = "0.1.0"
group = "io.compartirarchivos"

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // El hierarchy template por defecto crea commonMain -> {androidMain, desktopMain}.

    sourceSets {
        commonMain.dependencies {
            @Suppress("DEPRECATION")
            implementation(compose.runtime)
            @Suppress("DEPRECATION")
            implementation(compose.foundation)
            @Suppress("DEPRECATION")
            implementation(compose.material3)
            @Suppress("DEPRECATION")
            implementation(compose.components.resources)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.napier)

            // Cliente HTTP + serialización: en commonMain porque el escáner de
            // subred (SubnetScanner) y el TransferClient viven aquí.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.jmdns)
                // Servidor HTTP embebido (Netty): solo JVM.
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.status.pages)
                implementation(libs.ktor.server.call.logging)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.documentfile)
                // Servidor HTTP embebido (Netty): solo JVM.
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.status.pages)
                implementation(libs.ktor.server.call.logging)
            }
        }
    }
}

android {
    namespace = "io.compartirarchivos.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
