# Investigación y Análisis Comparativo — 7 Apps Base de cero-X

## Apps Analizadas

| # | App | Repositorio | Licencia | Plataforma | Estado |
|---|-----|-------------|----------|------------|--------|
| 1 | **Anizen Preview** | (preview/nightly) | Desconocida | Android | Preview |
| 2 | **Foxlations** | github.com/Foxlations/Foxlations | Apache-2.0 | Android + iOS | Activo |
| 3 | **Kototoro** | (fork de Kotatsu/Tachiyomi) | Apache-2.0 | Android | Fork activo |
| 4 | **Kisara Preview** | github.com/ifritraen/kisara | Apache-2.0 | Android | Fork de Komikku |
| 5 | **Reikai Preview** | github.com/unseensnick/Reikai | Apache-2.0 | Android 8+ | Activo (Mihon base) |
| 6 | **Echo-Nightly** | github.com/itsmechinmoy/echo-nightly | Desconocida | Multiplatform (Compose) | Nightly |
| 7 | **Namida Beta** | github.com/namidaco/namida | GPL-3.0 | Android (Flutter) | Beta |

---

## Matriz de Características por App

### 1. Anizen Preview
- **Tipo**: Preview/nightly de lector de manga
- **Fortalezas conocidas**: Ciclo de releases rápido, características experimentales tempranas
- **Mejor función para cero-X**: Canal de actualización nightly/preview integrado

### 2. Foxlations
- **Tipo**: Lector manga/manhwa/novela/video multiplataforma (Android + iOS)
- **Licencia**: Apache-2.0
- **Fortalezas**:
  - **RepoForge**: Generador de extensiones/sources desde cualquier sitio web (beta)
  - Sincronización de progreso con AniList / MyAnimeList / Kitsu
  - Backup y restauración de biblioteca local
  - Lectura detrás de sitios protegidos por Cloudflare
  - Sin cuentas, sin analytics, todo local
  - Stats de lectura (tiempo, capítulos) calculados en dispositivo
- **Mejor función para cero-X**: RepoForge + sync multi-plataforma + stats locales

### 3. Kototoro
- **Tipo**: Fork de Kotatsu (linaje Tachiyomi)
- **Licencia**: Apache-2.0
- **Fortalezas**:
  - Lector unificador manga + novela + video
  - Caché de miniaturas configurable (16 MB - 1 GB)
  - Soporte de novelas ligeras
  - Build nightly frecuente
- **Mejor función para cero-X**: Lector unificado multi-tipo (manga/novela/video)

### 4. Kisara Preview
- **Tipo**: Fork de Komikku (linaje TachiyomiSY/Mihon)
- **Licencia**: Apache-2.0
- **Fortalezas**:
  - **ONNX Runtime Mobile** (FP16/FP32) para inferencia IA on-device
  - **NNAPI Hardware Acceleration** (NPU/GPU routing)
  - **KisaraColorizer**: Colorización neural de manga con modelos ONNX quantizados
  - Jetpack Compose + Material3 UI
  - Injekt DI + Kotlin Coroutines + StateFlow
  - Logs de engine con monitoreo de hardware (CPU %, GPU MHz, RAM)
- **Mejor función para cero-X**: IA on-device con ONNX + NNAPI + colorización neural

### 5. Reikai Preview
- **Tipo**: Lector manga + novelas ligeras, construido sobre Mihon
- **Licencia**: Apache-2.0
- **Requiere**: Android 8.0+
- **Fortalezas**:
  - **Single-list library + category hopper**: Biblioteca plana con salto rápido por categoría
  - **Dynamic grouping**: Reagrupamiento dinámico por tag, autor, source, status
  - **Cover-color theming**: Tinte de pantalla de detalles desde portada (portado de Komikku)
  - **Multi-source grouping and merge**: Una serie con múltiples fuentes fusionada
  - Light novels junto a manga en una misma biblioteca
- **Mejor función para cero-X**: Biblioteca unificada con merge multi-fuente + agrupación dinámica

### 6. Echo-Nightly
- **Tipo**: Reproductor de música (nightly builds)
- **Base**: Fork de Echo (brahmkshatriya/echo)
- **Fortalezas**:
  - Multiplatform (Compose Desktop + Android)
  - Canal nightly continuo
- **Mejor función para cero-X**: Arquitectura multiplatform con Jetpack Compose

### 7. Namida Beta
- **Tipo**: Reproductor música + video con soporte YouTube
- **Licencia**: GPL-3.0
- **Base**: Flutter
- **Fortalezas**:
  - Reproductor multimedia (música + video)
  - Soporte de YouTube
  - Builds beta vía GitHub + Telegram
  - Interfaz moderna y feature-rich
- **Mejor función para cero-X**: Reproductor multimedia integrado (audio + video + streaming)

---

## Funciones Seleccionadas para cero-X (Fusionadas)

| Categoría | Función | App Origen | Implementación |
|-----------|---------|------------|----------------|
| **Lectura** | Lector unificado manga/novela/video | Kototoro | Core reader multi-tipo |
| **Biblioteca** | Single-list + category hopper | Reikai | Biblioteca plana unificada |
| **Biblioteca** | Dynamic grouping (tag/autor/source/status) | Reikai | Agrupación en tiempo real |
| **Biblioteca** | Multi-source merge (una serie, N fuentes) | Reikai | Fusor de series |
| **UI** | Cover-color theming | Reikai (desde Komikku) | Tinte dinámico desde portada |
| **UI** | Jetpack Compose + Material3 | Kisara, Echo-Nightly | UI declarativa moderna |
| **Sources** | RepoForge (generador de extensiones desde URL) | Foxlations | Creador de sources visual |
| **Sync** | Sincronización AniList/MAL/Kitsu | Foxlations | Tracker integrado |
| **Backup** | Backup/restauración local de biblioteca | Foxlations | Export/import JSON/Zip |
| **Nube** | Sin cuentas, todo local, sin analytics | Foxlations | Privacy-first |
| **Stats** | Tiempo de lectura, capítulos, stats dispositivo | Foxlations | Métricas locales |
| **IA** | ONNX Runtime (FP16/FP32) CPU + NNAPI | Kisara | Inferencia IA on-device |
| **IA** | Colorización neural de manga (ONNX quantized) | Kisara | KisaraColorizer integrado |
| **Hardware** | Monitoreo CPU/GPU/RAM en logs | Kisara | Telemetría de dispositivo |
| **Multimedia** | Reproductor música + video + YouTube | Namida | MediaPlayer + ExoPlayer |
| **Multiplatform** | Compose para Android + Desktop | Echo-Nightly | UI compartida |
| **Updates** | Canal nightly/preview integrado | Anizen, Echo-Nightly, Namida | Auto-updater de canal preview |

---

## Arquitectura de Fusión — cero-X Super-Fork

```
cero-X/
├── core/                          # Núcleo unificado (event bus, DI, lifecycle)
├── modules/
│   ├── reader/                    # Fusión: Kototoro + Reikai + Mihon base
│   │   ├── unified_library/       # Single-list + dynamic grouping + merge
│   │   ├── cover_color_theme/     # Tinte desde portada
│   │   ├── multi_source_merge/    # Una serie, N fuentes
│   │   └── novel_support/         # Light novels junto a manga
│   ├── sources/                   # Fusión: Foxlations RepoForge
│   │   ├── repoforge_generator/   # Generador de sources desde URL
│   │   ├── cloudflare_bypass/     # Lectura detrás de Cloudflare
│   │   └── extension_store/       # Tienda de extensiones (Keiyoushi-compat)
│   ├── sync/                      # Fusión: Foxlations
│   │   ├── anilist_sync/          # Sincronización AniList
│   │   ├── mal_sync/              # Sincronización MyAnimeList
│   │   └── kitsu_sync/            # Sincronización Kitsu
│   ├── backup/                    # Fusión: Foxlations
│   │   ├── local_backup/          # Backup ZIP local
│   │   └── restore/               # Restauración de biblioteca
│   ├── ai/                        # Fusión: Kisara (KisaraColorizer)
│   │   ├── onnx_runtime/          # ONNX Runtime Android (CPU + NNAPI)
│   │   ├── neural_colorizer/      # Colorización de manga on-device
│   │   ├── model_quantizer/       # Quantización FP16/INT8
│   │   └── hardware_monitor/      # Telemetría CPU/GPU/RAM
│   ├── media/                     # Fusión: Namida + Echo-Nightly
│   │   ├── audio_player/          # Reproductor música (ExoPlayer)
│   │   ├── video_player/          # Reproductor video
│   │   ├── youtube_support/       # Soporte YouTube
│   │   └── compose_ui/            # UI compartida multiplatform
│   ├── updates/                   # Fusión: Anizen + Echo-Nightly + Namida
│   │   ├── nightly_channel/       # Canal de actualizaciones preview
│   │   └── auto_updater/          # Auto-instalación de builds
│   └── reverse_engineering/       # Herramientas de RE integradas
│       ├── antigravity/           # Análisis de binarios
│       ├── deepseek_harness/      # Evaluación de modelos IA
│       ├── code_comparator/       # Comparativa de código en paralelo
│       └── stress_tester/         # Pruebas de estrés al extremo
├── build/
└── docs/
```

---

## Plan de Ingeniería Inversa Profunda

### Fase 1: Extracción de Código
1. Clonar los 7 repositorios fuente
2. Extraer módulos individuales por funcionalidad
3. Identificar dependencias y conflictos de licencia (Apache-2.0 vs GPL-3.0)

### Fase 2: Análisis Comparativo de Código
- Comparar implementaciones de lector (Kototoro vs Reikai vs Mihon base)
- Comparar sistemas de extensiones (Foxlations RepoForge vs Mihon extensions)
- Comparar UI (Kisara Compose vs Reikai Views vs Namida Flutter)
- Documentar patrones de diseño en cada app

### Fase 3: Fusión de Código
- Resolver conflictos de paquetes y nombres de clases
- Unificar bajo paquete `com.ceroX.app`
- Mantener licencias originales en headers de archivo

### Fase 4: Pruebas de Estrés al Extremo
- Carga de 10,000+ series en biblioteca
- 100+ extensiones activas simultáneas
- Descargas concurrentes masivas
- Inferencia IA continua con modelos grandes
- Pruebas en Android 8 (API 26) a Android 14 (API 34)

---

## Licencias y Consideraciones Legales

| App | Licencia | Impacto en cero-X |
|-----|----------|-------------------|
| Foxlations | Apache-2.0 | Compatible, permisiva |
| Kototoro | Apache-2.0 | Compatible, permisiva |
| Kisara | Apache-2.0 | Compatible, permisiva |
| Reikai | Apache-2.0 | Compatible, permisiva |
| Echo-Nightly | Desconocida | Requiere verificación |
| Namida | GPL-3.0 | **Copia izquierda** — cero-X debe ser GPL-3.0 si incluye código de Namida |
| Anizen | Desconocida | Requiere verificación |

**Decisión recomendada**: cero-X bajo Apache-2.0 para módulos de Foxlations/Kototoro/Kisara/Reikai. Si se incluye código de Namida (GPL-3.0), esos archivos específicos deben mantener GPL-3.0 y la app completa se convierte en GPL-3.0.

---

## Roadmap de Integración

- **Semana 1-2**: Clonar 7 repos, extraer módulos, crear estructura de fusión
- **Semana 3-4**: Resolver conflictos, unificar paquete, build inicial
- **Semana 5-6**: Integrar RepoForge, sync trackers, backup/restore
- **Semana 7-8**: Integrar ONNX + colorización neural, hardware monitor
- **Semana 9-10**: Integrar reproductor multimedia (Namida) + Compose UI
- **Semana 11-12**: Canal nightly, auto-updater, reverse engineering tools
- **Semana 13-14**: Pruebas de estrés al extremo, optimización
- **Semana 15-16**: Beta pública Android 8-14

---

*Informe generado para cero-X App — Fork Maestro de 7 Ecosistemas de Código Abierto*