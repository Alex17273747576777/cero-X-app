# cero-X App — Módulo Android (Beta v0.1.0)

## Requisitos del Sistema

- **Android 8.0 (API 26)** o superior
- **Android 14 (API 34)** como SDK de compilación objetivo
- Java 11+ JDK
- Android Studio Giraffe o posterior
- Gradle 8.2+

## Arquitectura

El módulo Android sigue la misma arquitectura modular del núcleo cero-X:

```
app/
├── src/
│   └── main/
│       ├── java/com/ceroX/app/
│       │   ├── core/           # Núcleo (CoreEngine, EventBus, ModuleRegistry, ConfigManager)
│       │   ├── modules/        # 7 módulos funcionales
│       │   │   ├── ai_training/      # ONNX Runtime, fine-tuning, CPU/NNAPI
│       │   │   ├── downloads/        # DownloadManager, SHA-256 verificación
│       │   │   ├── reader/           # Lector manga/novelas, extensiones
│       │   │   ├── audio/            # MediaPlayer, índice Wotaku
│       │   │   ├── video/            # Reproductor video, biblioteca
│       │   │   ├── reverse_engineering/ # Antigravity, DeepSeek Harness
│       │   │   └── environments/     # Debian, contenedores, ISOs
│       │   └── ui/             # Interfaz de usuario
│       │       ├── main/MainActivity.kt
│       │       └── dashboard/ModuleDashboardFragment.kt
│       └── res/
│           ├── layout/         # Layouts XML
│           ├── values/         # Strings, colores, temas
│           ├── drawable/       # Iconos vectoriales
│           └── mipmap/         # Iconos de launcher
├── build.gradle                # minSdk 26, targetSdk 34
├── proguard-rules.pro
└── AndroidManifest.xml         # Permisos, servicios, proveedor de archivos
```

## Módulos Implementados

| Módulo | Función | Tecnología |
|--------|---------|------------|
| **ai-training** | Entrenamiento/fine-tuning IA | ONNX Runtime, CPU, NNAPI |
| **downloads** | Gestor de descargas | DownloadManager, SHA-256 |
| **reader** | Lector manga/novelas | Extensiones, biblioteca local |
| **audio** | Reproductor audio | MediaPlayer, Wotaku index |
| **video** | Reproductor video | Biblioteca local, codecs |
| **reverse-engineering** | Ingeniería inversa | Antigravity, DeepSeek Harness |
| **environments** | Gestor de entornos | Debian, contenedores, ISOs |

## Compilación

```bash
cd android

# Hacer ejecutable el wrapper
chmod +x gradlew

# Compilar en modo debug
./gradlew assembleDebug

# Instalar en dispositivo conectado
./gradlew installDebug

# Compilar release
./gradlew assembleRelease
```

## Compatibilidad

- **Android 8.0 (API 26)** — Mínimo soportado
- **Android 9-13** — Compatible
- **Android 14 (API 34)** — Target SDK, totalmente compatible

> Nota: Android 17 aún no existe como versión oficial. El rango soportado es Android 8 → Android 14 (API 26-34).

## Ejecución

1. Conectar un dispositivo Android o iniciar un emulador
2. Ejecutar: `./gradlew installDebug`
3. La app se iniciará mostrando el panel de módulos
4. Cada módulo muestra su estado (Activo/Detenido)

## Próximos Pasos para la Beta 1.0

- [ ] Conectar adaptadores reales con Mihon, Komikku, Wotaku, Kisara
- [ ] Implementar UI completa para cada módulo
- [ ] Agregar base de datos Room para biblioteca local
- [ ] Implementar descargas reales con OkHttp
- [ ] Agregar inferencia ONNX real con modelos locales
- [ ] Implementar clonación de ecosistemas con git
- [ ] Pruebas en dispositivos reales Android 8-14