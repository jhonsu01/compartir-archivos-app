# CompartirArchivos

App multiplataforma para compartir archivos por red local (LAN/WiFi) entre **Android** (móvil + Android TV) y **Windows**, con descubrimiento automático (mDNS), emparejamiento por PIN y explorador de archivos integrado.

> Proyecto en desarrollo activo. Versión actual: **0.1.0**.

## Características

- 🔍 **Descubrimiento automático** de dispositivos en la red local (mDNS / NSD).
- 🔐 **Emparejamiento seguro** con PIN temporal de 6 dígitos (válido 60 s).
- 📤 **Transferencia de archivos** vía HTTP (Ktor) con servidor embebido.
- 📁 **Explorador de archivos** (Storage Access Framework en Android, `java.nio.file` en Desktop).
- 🌙 **Modo oscuro** obligatorio, Material Design 3.
- 📺 **UI adaptativa** móvil / TV (iconos compactos vs. grandes y espaciados).

## Arquitectura

```
shared/        ← Kotlin Multiplatform: protocolo, modelos, networking, discovery (expect/actual)
androidApp/    ← App Android (Compose), móvil + TV
desktopApp/    ← App Windows/Desktop (Compose Desktop → MSI)
```

**Stack:** Kotlin Multiplatform · Compose Multiplatform · Ktor (cliente + servidor Netty) · JmDNS / NsdManager · kotlinx.serialization.

## Descargas

Las versiones compiladas (APK y MSI) se publican automáticamente en [Releases](../../releases) mediante GitHub Actions cada vez que se etiqueta una versión (`vX.Y.Z`).

## Compilación

Requisitos: JDK 17, Android SDK (compileSdk 36, minSdk 26).

```bash
# APK Android (debug signing para pruebas)
./gradlew :androidApp:assembleRelease
# → androidApp/build/outputs/apk/release/androidApp-release.apk

# App Windows desktop (imagen autocontenida + instalador MSI, requiere WiX 3.x para MSI)
./gradlew :desktopApp:packageReleaseMsi
# → desktopApp/build/compose/binaries/main-release/msi/CompartirArchivos-0.1.0.msi
```

## Seguridad

- PIN de emparejamiento obligatorio, de un solo uso y con expiración.
- Conexiones no autenticadas bloqueadas (token de sesión tras emparejamiento).
- Permisos mínimos en Android.

## Solución de problemas

**El escritorio no detecta a los móviles (o viceversa):**
1. Verifica que **todos** los dispositivos están en la **misma red WiFi** (no red de invitados, que suele aislar clientes).
2. **Firewall de Windows**: permite que la app `CompartirArchivos.exe` (o `java.exe`) reciba conexiones en redes privadas. Al primer arranque Windows pregunta; responde "Permitir". Si la bloqueaste, ve a *Panel de control → Firewall de Windows → Permitir una aplicación*.
3. Algunos routers activan **AP Isolation / Client Isolation**, que impide que los dispositivos se vean entre sí aunque estén en la misma WiFi. Desactívalo en la configuración del router, o prueba creando un hotspot desde el móvil.
4. En Android, concede el acceso a archivos al abrir el explorador (SAF).

**Error "Fail to prepare request body" (v0.2.0):** corregido en v0.3.0 (faltaba `Content-Type: application/json` en el cliente Ktor 3.x).

## Roadmap

Ver [`guia_app_transferencia_archivos.md`](guia_app_transferencia_archivos.md) para el plan completo (Fases 1–5).

## Licencia

MIT
