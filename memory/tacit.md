# Conocimiento Tacito del Usuario

## Preferencias de stack
- **Multiplataforma:** prefiere Kotlin Multiplatform + Compose (decision 2026-07-02) sobre .NET/Electron para apps Android+Windows.
- **Firma de binarios:** en fases tempranas (v0.x) se omite firma; APK usa debug keystore, MSI sin firma.

## GitHub / Publicacion
- Publica repos como **publicos**.
- Usa **GitHub Desktop** para push/pull cotidiano; el agente debe dejar el repo local listo para que el usuario haga push desde Desktop.
- Quiere **GitHub Actions con releases automaticas** visibles conforme avanza el proyecto (tag-driven).

## Comunicacion
- Lengua: espanol. Comentarios/commit messages en espanol o ingles segun convenga al repo; README y mensajes al usuario en espanol.
- Verbosidad: prefiere reportes concisos con declaraciones obligatorias del sistema (DET/STO, Slurp/CodeGraph) pero sin paja.

## Entorno
- Windows (Git Bash). SDK Android en `%LOCALAPPDATA%\Android\Sdk`. JDK 17 en `C:\java\java-17-openjdk`.
- `gh` no estaba instalado (instalado por el agente el 2026-07-02). Login web con OTC; scopes necesarios: repo, read:org, workflow.
- Java por defecto en PATH: JDK 25 (puede dar problemas con AGP; usar JDK 17 explicito para builds Android).
- **WiX portable** para MSI sin admin: `.tools/wix/` con `wix314-binaries.zip`; añadir al PATH antes de `./gradlew packageReleaseMsi`.
- `gradlew` debe llevar bit +x: `git update-index --chmod=+x gradlew` tras añadirlo.

## Versiones probadas (no cambiar sin testear)
- Kotlin 2.2.20 + Compose MP 1.10.0 + AGP 8.13.0 + Gradle 8.14.3 (funcionan juntas, julio 2026).
- AGP 9.x + Kotlin 2.4.x dan problemas con KMP (new DSL, metadata mismatch). Evitar por ahora.
- minSdk 26 por Netty 4.2. Si se baja a 24, cambiar Android a engine CIO (sin Netty).

## Presupuestos Slurp (v7)
- (pendiente de inicializar Slurp cuando haya codigo)
