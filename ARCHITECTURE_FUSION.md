# Arquitectura de Fusión — cero-X Super-Fork v0.2.0

Este documento detalla cómo se fusionan los 8 ecosistemas en una sola aplicación Android con Houbi como lector base principal y soporte para Android 16 (API 36).

## Estrategia de Fusión

cero-X no es un simple clon. Es una **fusión arquitectónica** donde cada app aporta su módulo estrella, y el núcleo de cero-X los orquesta. Houbi (PineappleTwilight/houri) es la base principal del lector.

### Capa 1: Núcleo (Core)
- Event Bus unificado
- Module Registry con dependencias
- ConfigManager jerárquico
- Dependency Injection (Hilt, patrón Metro+Injekt de Houbi)
- Soporte Android 16 (API 36)

### Capa 2: Módulos de Fusión (11 módulos)

#### Houbi Reader Module (PineappleTwilight/houri) — BASE PRINCIPAL
```
com.ceroX.app.modules.houri
├── WebGPUViewer.kt          # Renderizado acelerado WebGPU (Houbi)
├── DarkModeFilter.kt        # Filtro oscuro con AMOLED subtoggle (Houbi)
├── Upscaler.kt              # Escalado de imágenes con cache PNG (Houbi)
├── UniversalTracker.kt      # Tracker universal MangaBaka/MU/Hikka/Kitsu (Houbi)
├── AchievementsFramework.kt # Framework de logros (Houbi)
├── ViewerRegistry.kt        # Registro de viewers polimórfico (Houbi)
├── PageLoaderFactory.kt     # Factory de carga de páginas (Houbi)
├── AppDispatchers.kt        # Foundation de concurrencia (Houbi)
├── SingleSQLiteConnection.kt # Conexión única SQLite vía Metro (Houbi)
├── WebtoonSupport.kt        # Webtoon: zoom lock, two-finger zoom, grayscale (Houbi)
└── HoubiModule.kt           # Motor principal (Mihon base)
```

#### Unified Reader Module (Kototoro + Reikai + Mihon)
```
com.ceroX.app.modules.reader
├── UnifiedLibraryItem.kt          # Single-list + category hopper (Reikai)
├── DynamicGrouping.kt             # Agrupación por tag/autor/source/status (Reikai)
├── MultiSourceMerge.kt            # Una serie, N fuentes fusionadas (Reikai)
├── CoverColorTheme.kt             # Tinte desde portada (Reikai ← Komikku)
├── NovelSupport.kt                # Light novels junto a manga (Kototoro)
├── ThumbnailCache.kt              # Caché 16MB-1GB configurable (Kototoro)
└── UnifiedReaderModule.kt         # Motor de lectura base (Mihon)
```

#### RepoForge Sources Module (Foxlations)
```
com.ceroX.app.modules.repoforge
├── RepoForgeGenerator.kt      # Generador de sources desde URL (Foxlations)
├── CloudflareBypass.kt        # Lectura detrás de Cloudflare (Foxlations)
├── ExtensionStore.kt          # Tienda Keiyoushi-compatible
└── SourceManager.kt           # Gestor de extensiones activas
```

#### Sync Module (Foxlations + Houbi)
```
com.ceroX.app.modules.sync
├── AniListSync.kt             # Sincronización AniList (Foxlations)
├── MALSync.kt                 # Sincronización MyAnimeList (Foxlations)
├── KitsuSync.kt               # Sincronización Kitsu (Foxlations)
├── UniversalTracker.kt        # Tracker universal Houbi (MangaBaka, MU, Hikka, Kitsu)
└── ProgressTracker.kt         # Sincronización de progreso de lectura
```

#### Backup Module (Foxlations)
```
com.ceroX.app.modules.backup
├── LocalBackup.kt             # Backup ZIP de biblioteca
├── RestoreManager.kt          # Restauración de biblioteca
└── ExportManager.kt           # Exportación JSON/CSV
```

#### AI Module (Kisara)
```
com.ceroX.app.modules.ai
├── OnnxRuntimeManager.kt      # ONNX Runtime Android (CPU + NNAPI)
├── NeuralColorizer.kt         # Colorización de manga on-device (KisaraColorizer)
├── ModelQuantizer.kt          # Quantización FP16/INT8
└── HardwareMonitor.kt         # Telemetría CPU/GPU/RAM (Kisara)
```

#### Media Module (Namida + Echo-Nightly)
```
com.ceroX.app.modules.media
├── AudioPlayer.kt             # Reproductor música (ExoPlayer, Namida)
├── VideoPlayer.kt             # Reproductor video (Namida)
├── YouTubeSupport.kt          # Soporte YouTube (Namida)
└── ComposeUI.kt               # UI compartida Jetpack Compose (Echo-Nightly)
```

#### Updates Module (Anizen + Echo-Nightly + Namida)
```
com.ceroX.app.modules.updates
├── NightlyChannel.kt          # Canal de actualizaciones preview
├── AutoUpdater.kt             # Auto-instalación de builds
└── VersionManager.kt          # Gestión de versiones
```

#### Reverse Engineering Module (cero-X)
```
com.ceroX.app.modules.reverse_engineering
├── AntigravityAnalyzer.kt     # Análisis de binarios (Antigravity)
├── DeepSeekHarness.kt         # Evaluación de modelos IA (DeepSeek Harness)
├── CodeComparator.kt          # Comparativa de código en paralelo
└── StressTester.kt            # Pruebas de estrés al extremo
```

#### Downloads Module
```
com.ceroX.app.modules.downloads
├── DownloadManager.kt         # Gestor de descargas Android
├── Sha256Verifier.kt          # Verificación de integridad SHA-256
└── ResumeSupport.kt           # Reanudación de descargas
```

#### Environments Module
```
com.ceroX.app.modules.environments
├── DebianManager.kt           # Gestor de entornos Debian
├── ContainerManager.kt        # Contenedores y VMs ligeras
└── IsoDownloader.kt           # Descarga de ISOs de Debian
```

### Capa 3: UI Unificada
- Jetpack Compose + Material3 (desde Houbi/Echo-Nightly)
- Temas dinámicos con cover-color (desde Reikai)
- Soporte Android 8.0 (API 26) a Android 16 (API 36)

---

## Mapa de Conflictos y Resoluciones

| Conflicto | Apps Involucradas | Resolución |
|-----------|-------------------|------------|
| Paquete `com.ceroX.app.reader` | Todas | Unificar bajo `com.ceroX.app.modules.reader` |
| Clase `Library` | Reikai, Kototoro, Mihon | Renombrar a `UnifiedLibrary` |
| Clase `Source` | Foxlations, Mihon | Renombrar a `ExtensionSource` |
| Lector Base | Komikku → Houbi | **Houbi es ahora la base principal** |
| Dependencia ONNX | Kisara | Incluir `onnxruntime-android` |
| Dependencia Flutter | Namida | **NO incluir** — solo adaptar lógica de reproductor a Android nativo |
| Licencia GPL-3.0 | Namida | Mantener archivos de Namida bajo GPL-3.0, resto bajo Apache-2.0 |
| DI Framework | Houbi (Metro+Injekt) | Usar Hilt para cero-X |

---

## Build System — Android 16

- Gradle 8.2+
- Android Gradle Plugin 8.2.0+
- Kotlin 1.9.22+
- **compileSdk 36, targetSdk 36, minSdk 26**
- Arquitecturas: armeabi-v7a, arm64-v8a, x86, x86_64
- Jetpack Compose 1.7.0, Material3 1.3.0
- Hilt 2.50 para DI
- Room 2.7.0 para base de datos

---

## Licencia Resultante

cero-X se distribuye bajo **Apache-2.0** con excepción de los módulos derivados de Namida (GPL-3.0) que mantienen su licencia original en los archivos correspondientes.