# CompartirArchivos

App multiplataforma para compartir archivos en red local (LAN/WiFi) entre Android (movil + TV) y Windows, con descubrimiento automatico (mDNS), emparejamiento por PIN y explorador de archivos.

## Stack
- **Kotlin Multiplatform** + **Compose Multiplatform**
- Modulos: `shared/` (protocolo, modelos, crypto PIN), `androidApp/`, `desktopApp/`
- Descubrimiento: mDNS (NSD en Android, JmDNS en JVM)
- Transferencia: Ktor (servidor HTTP embebido + cliente)
- Builds: Gradle (Kotlin DSL) + wrappers; Android APK y Desktop MSI via jpackage

## Estado
- **Version actual:** 0.1.0 (publicada en GitHub Releases)
- **Repo:** https://github.com/jhonsu01/compartir-archivos-app (publico)
- **Fase guia:** Fases 1-4 implementadas (descubrimiento, PIN, transferencia, explorador, UI adaptativa). Fase 5 (optimizacion/publicacion tiendas) pendiente.

## Hechos clave
- 2026-07-02: Inicio del proyecto. Decision de stack KMP+Compose. Sin firma en v0.x.
- 2026-07-02: v0.1.0 publicada. APK 15MB + MSI 103MB en release automatica via Actions. CI verde.
- **Versiones fijadas:** Kotlin 2.2.20, Compose MP 1.10.0, AGP 8.13.0, Gradle 8.14.3, Ktor 3.5.0, minSdk 26.

## Items atomicos
- Ver `items.json` para detalle granular.
