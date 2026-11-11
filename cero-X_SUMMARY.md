# cero-X App — 1.0.1-beta-arranque-honesto (versionCode 6)

## Resumen Ejecutivo

cero-X es una aplicación Android todo-en-uno que fusiona **17+ ecosistemas** de código abierto de lectura, IA, audio, video, entornos de desarrollo, extensiones, catálogo de apps, booru, y asistentes de IA en una sola plataforma modular. **Houbi (PineappleTwilight/houri) es la base principal del lector**.

**Versión**: 1.0.1-beta-arranque-honesto (versionCode 6)  
**Licencia**: GPL-3.0 desde el 2026-11-11. El linaje (Mihon, Komikku, Houri) es Apache-2.0 y conserva sus avisos en `NOTICE`. La «excepción GPL-3.0 para módulos de Namida» que se leía aquí no existía: Namida es una EULA propietaria y no se usa su código.  
**Compatibilidad**: Android 8.0 (API 26) → Android 16 (API 36), Android TV, Huawei (HMS), Windows, Linux  
**Arquitectura**: Núcleo modular inspirado en el kernel de Linux  
**UI**: Jetpack Compose + Material3. Las cinco paletas de `theme-engine` imitan el aire de Kiroku, Kototoro y AniZen; son colores propios, sin código de esos proyectos ni de Namida (EULA propietaria), y el desenfoque «Liquid Glass» no está implementado  
**Target SDK**: 36 (Android 16, requerido por Google Play)  
**APK publicado**: arm64-v8a sin modelos, 31.577.211 bytes (30,1 MiB), firmado con v2. El universal de cuatro ABI sigue siendo posible con el build sin `-PsoloAbi`.  
**Tamaño**: los 475.523.392 bytes de la build borrada eran 378 MiB de modelos ONNX más tres ABI de sobra. Hoy los modelos no viajan en el APK; el flujo de CI lo comprueba contando los `.onnx` del ZIP

**Repositorio:** https://github.com/Alex17273747576777/cero-X-app

---

## Módulos Kisara (7 módulos - fork estabilizado de Komikku)

| Módulo | Versión | Dependencias | Función |
|--------|---------|--------------|---------|
| kisara-reader | 2.3.9-kisara-stable | - | Lector manga base (fork de Komikku/TachiyomiSY/Mihon) |
| kisara-reader-engine | 2.3.9-kisara-stable | kisara-reader | Motor de lectura (visores, webtoon, OCR, colorización) |
| kisara-library | 2.3.9-kisara-stable | kisara-reader | Biblioteca (categorías, bulk-favorite, merge, búsqueda) |
| kisara-sync | 2.3.9-kisara-stable | kisara-library | Sync 2-way trackers (MAL, AniList, Kitsu, etc.) |
| kisara-theme | 2.3.9-kisara-stable | - | Sistema de temas (auto-color, 7+ paletas, AMOLED, panorama) |
| kisara-migration | 2.3.9-kisara-stable | kisara-library, kisara-sync | Migración de sources, rango, NSFW, smart-update |
| kisara-ocr-translator | 1.0.0-kisara | kisara-reader | OCR ONNX PP-OCRv6_manga + traductor IA |
| kisara-neural-colorizer | 1.0.0-kisara | kisara-reader | Colorización neural ONNX (NNAPI/CPU, 91.5% similitud PC) |

---

## 30 Módulos Funcionales

```
com.ceroX.app.modules
├── houbi-reader          # LECTOR BASE PRINCIPAL (Houbi/PineappleTwilight/houri)
├── kisara-reader         # Manga Reader (fork de Komikku - ifritraen/kisara)
├── kisara-reader-engine  # Motor de lectura Kisara
├── kisara-library        # Biblioteca Kisara
├── kisara-sync           # Sync 2-way trackers
├── kisara-theme          # Temas Kisara
├── kisara-migration      # Migración de sources
├── kisara-ocr-translator # OCR ONNX PP-OCRv6_manga + traductor IA
├── kisara-neural-colorizer # Colorización neural ONNX (NNAPI/CPU)
├── extension-sandbox     # SanTBox de extensiones
├── extension-factory     # Fábrica de extensiones propias
├── app-catalog           # Catálogo de apps
├── novel-reader          # Lector de novelas
├── comic-reader          # Lector de cómics
├── media-server          # Servidores multimedia
├── download-tools        # Herramientas de descarga
├── booru-viewer          # Booru viewer (weekanya/Booru)
├── prisma-client         # Prisma client (RarDog/Prisma)
├── ai-hub                # AI Hub (SilentCoderHere/aihub)
├── kai-assistant         # Kai assistant (SimonSchubert/Kai)
├── theme-engine          # Motor de temas (Kiroku, Kototoro, AniZen, namida)
├── mode-manager          # Gestor de modos
├── cross-platform        # Multiplataforma
├── unified-reader        # Lector unificado
├── repoforge-sources     # Generador de sources
├── sync-trackers         # Sync trackers
├── backup-restore        # Backup ZIP local
├── neural-ai             # IA Neural (ONNX)
├── media-player          # Multimedia
├── updates-channel       # Actualizaciones
├── deep-reverse-engineering # Ingeniería inversa
├── downloads             # Descargas SHA-256
├── environments          # Entornos Debian
├── ai-training           # Entrenamiento IA
├── video                 # Video
└── audio                 # Audio
```

---

## 🛡️ Sistema de Estabilización

| Componente | Función |
|------------|---------|
| ModuleLifecycleManager | Topological sort, retry con backoff, aislamiento de errores, callbacks de ciclo de vida |
| ModuleDependenciesValidator | Detección de dependencias faltantes, circulares, huérfanas y en ERROR |
| CrashHandler | Manejo global de excepciones no capturadas con reporte JSON |
| MemoryMonitor | Monitoreo de memoria en segundo plano con umbrales CRITICAL/WARNING |
| NetworkSecurityConfig | HTTPS obligatorio, cleartext solo localhost debug |
| DataExtractionRules / BackupRules | Android 12+ compatibles, excluye credenciales de backup |
| ProGuard/R8 | Optimización agresiva: ONNX, OkHttp, Hilt, Room, Compose, Gson, Kotlin |
| ConnectivityReceiver | Notificación de cambios de red |

---

## 📱 Cómo Compilar (requiere Android SDK API 36)

```bash
cd /home/user/cero-X-app/android

# Configurar Android SDK
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

# Instalar platform-tools y build-tools si no están
sdkmanager "platform-tools" "build-tools;36.0.0" "platforms;android-36"

# Dar permisos al wrapper
chmod +x gradlew

# Compilar APK universal (bash necesario debido al shebang del script)
bash ./gradlew assembleDebug --no-daemon

# Instalar en dispositivo
bash ./gradlew installDebug
```

**APK:** `app/build/outputs/apk/debug/app-debug.apk` (universal, 200-750 MB)

> **Importante:** El script `gradlew` usa `#!/bin/sh` pero contiene sintaxis de bash. Ejecutar con `bash ./gradlew` no con `./gradlew`.

> **Nota sobre el SDK:** El Android SDK se descarga de los servidores de Google (developer.android.com), no de GitHub. El token de GitHub es solo para autenticación del repositorio.

---

## 🚀 Próximos Pasos

1. Instalar Android SDK con API 36
2. Ejecutar `bash ./gradlew assembleDebug` para compilar
3. Ejecutar `scripts/run_stress_tests.sh` para pruebas de estrés
4. Probar en dispositivos Android 8-16, Android TV, Huawei

---

*cero-X — El núcleo de Linux para el ecosistema de manga, IA, audio y video de código abierto. Con Houbi como lector base principal, Kisara (fork de Komikku) como lector de manga con ONNX OCR + Neural Colorizer, SanTBox de extensiones, Catálogo de Apps, Booru, Prisma, AI Hub, Kai, Tema super personalizable, Modos dedicados y soporte multiplataforma.*