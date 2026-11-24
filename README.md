# Centinela Privacidad

Aplicación Android en **Kotlin + Jetpack Compose (Material 3)** para monitorear en tiempo real el acceso a cámara y micrófono, auditar permisos, bloquear sensores y endurecer la privacidad del sistema **sin necesidad de root**.

> **Privacidad verificable:** Centinela Privacidad **no solicita** permisos de cámara (`CAMERA`) ni de micrófono (`RECORD_AUDIO`).

---

## Características principales

### 1. Monitor en tiempo real (`Monitor`)
- Servicio en primer plano que detecta al instante cuándo se activa la **cámara** (frontal, trasera o externa) o el **micrófono**.
- Atribución de la app responsable combinando **Servicio de Accesibilidad** (app en pantalla) y **Acceso de uso** (actividad reciente y servicios en segundo plano).
- Alertas emergentes configurables e inicio automático tras reiniciar el dispositivo.
- Acceso directo al **Panel de Privacidad de Android (Android 12+)**.

### 2. Bloqueo de sensores (`Bloqueo`)
- **Bloqueo de Micrófono:** silenciado en modo estándar (`AudioManager` con re-bloqueo automático al detectar grabación) o bloqueo inalterable a nivel de sistema (`DISALLOW_UNMUTE_MICROPHONE`) en **Modo Propietario (Device Owner)**.
- **Bloqueo de Cámara:** bloqueo a nivel de hardware/sistema para todas las apps (incluidas apps de sistema) en **Modo Propietario**.
- **Mosaico de Ajustes Rápidos (`Quick Settings Tile`):** *Bloquear Cám/Mic* desde la barra superior de Android.
- Accesos directos a los interruptores nativos de privacidad de Android 12+.

### 3. Historial de accesos (`Historial`)
- Registro local en SQLite con filtros por sensor (**Todos / Cámara / Micrófono**), búsqueda por app o paquete, duración de cada sesión y acceso directo a los ajustes de cada app.

### 4. Auditoría de permisos (`Auditoría`)
- Lista todas las aplicaciones (de usuario y del sistema) que tienen concedido permiso de cámara o micrófono.
- Permite abrir directamente sus ajustes o bloquear/restaurar permisos por política en **Modo Propietario**.

### 5. Blindaje del sistema y limpieza de rastreadores (`Blindaje`)
- **Ajustes y DNS Privado (100 % reversible con 1 toque vía `WRITE_SECURE_SETTINGS`):**
  - **DNS Privado cifrado (DoT):** presets listos para **Mullvad Adblock** (`adblock.dns.mullvad.net`), **Quad9** (`dns.quad9.net`), **AdGuard DNS** (`dns.adguard-dns.com`) o restaurar a **Automático**.
  - **Telemetría y reportes:** desactiva/revierte experiencia de usuario, permisos HiView, subida automática de APKs e informes de fallos.
  - **Publicidad y OAID:** limita el identificador publicitario OAID, anuncios personalizados y ubicación para anuncios.
  - **Escaneo en segundo plano:** desactiva escaneo permanente de Wi-Fi y Bluetooth.
  - Botones maestros **Blindar todo** y **Revertir**.
- **Paquetes de rastreo y bloatware (100 % reversible):**
  - Catálogo multi-fabricante (**Meta oculto**, **Honor / Huawei**, **Google Telemetría / SafetyCore**, **Xiaomi**, **Samsung** y otros) con verificación de estado en vivo (`Activo`, `Desactivado (ADB/Sistema)`, `Desactivado (Centinela)`).
  - Desactivación/reactivación en 1 toque en **Modo Propietario** (`setApplicationHidden`).
  - Generador automático de comandos **ADB reversibles** (`pm disable-user --user 0` / `pm enable`) individuales y en lote para **Modo Estándar**.

---

## Requisitos técnicos

- **Android:** 8.0 Oreo (`minSdk 26`) o superior (`compileSdk 35`, `targetSdk 35`)
- **JDK:** 17
- **Stack:** Kotlin 2.1, Jetpack Compose BOM 2025.02, Material 3, SQLite nativo

---

## Compilación local

1. Clona el repositorio:
   ```bash
   git clone https://github.com/carlosmundaray/centinela.git
   cd centinela
   ```

2. Ejecuta pruebas unitarias y compila la versión debug:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```

3. *(Opcional)* Para compilar **Release firmado**, crea un archivo `keystore.properties` en la raíz (ignorado por Git):
   ```properties
   storeFile=keystore/centinela-release.jks
   storePassword=TU_PASSWORD
   keyAlias=TU_ALIAS
   keyPassword=TU_KEY_PASSWORD
   ```
   Y ejecuta:
   ```bash
   ./gradlew assembleRelease
   ```

---

## Permisos avanzados opcionales por ADB (sin root)

### 1. Control directo de Blindaje (DNS Privado, telemetría, anuncios y escaneo)
No requiere quitar cuentas de Google ni restablecer el teléfono:
```bash
adb shell pm grant com.centinela.privacidad android.permission.WRITE_SECURE_SETTINGS
```

### 2. Modo Propietario / Device Owner (bloqueo total de cámara/micrófono y paquetes en 1 toque)
Requiere quitar temporalmente cuentas del dispositivo antes de ejecutarlo (luego puedes volver a iniciar sesión):
```bash
adb shell dpm set-device-owner com.centinela.privacidad/.blocker.CentinelaDeviceAdminReceiver
```
*(El Modo Propietario se puede desactivar en cualquier momento desde la pestaña **Bloqueo**, restaurando todos los bloqueos y paquetes ocultos por la app).*

---

## Estructura del proyecto

```text
app/src/main/java/com/centinela/privacidad/
├── MainActivity.kt                  # Navegación principal (5 pestañas)
├── CentinelaApp.kt                  # Inicialización de repositorio
├── blocker/                         # Bloqueo de cámara/micrófono, Device Admin y Quick Tile
├── data/                            # Modelos, SQLite helper y repositorio
├── hardening/                       # Catálogo de rastreadores, DNS Privado y controlador de blindaje
├── monitor/                         # SensorMonitorService, Accesibilidad y auditoría de permisos
└── ui/                              # Pantallas Jetpack Compose y tema Material 3
```
