# CompartirArchivos

App multiplataforma para compartir archivos en red local (LAN/WiFi) entre Android (movil + TV) y Windows, con descubrimiento automatico (mDNS), emparejamiento por PIN y explorador de archivos.

## Stack
- **Kotlin Multiplatform** + **Compose Multiplatform**
- Modulos: `shared/` (protocolo, modelos, crypto PIN), `androidApp/`, `desktopApp/`
- Descubrimiento: mDNS (NSD en Android, JmDNS en JVM)
- Transferencia: Ktor (servidor HTTP embebido + cliente)
- Builds: Gradle (Kotlin DSL) + wrappers; Android APK y Desktop MSI via jpackage

## Estado
- **Version actual:** 0.1.0 (en desarrollo)
- **Fase guia:** Fases 1-5 (intentar todo lo factible)

## Hechos clave
- 2026-07-02: Inicio del proyecto. Decision de stack KMP+Compose. Sin firma en v0.x.

## Items atomicos
- Ver `items.json` para detalle granular.
