# Investigación y Análisis Comparativo — 8 Apps Base de cero-X

## Apps Analizadas

| # | App | Repositorio | Licencia | Plataforma | Estado | Rol en cero-X |
|---|-----|-------------|----------|------------|--------|---------------|
| 1 | **Anizen Preview** | preview/nightly | Desconocida | Android | Preview | Canal nightly/preview |
| 2 | **Foxlations** | github.com/Foxlations/Foxlations | Apache-2.0 | Android + iOS | Activo | RepoForge, Cloudflare bypass, sync trackers, backup |
| 3 | **Kototoro** | Fork de Kotatsu/Tachiyomi | Apache-2.0 | Android | Fork activo | Lector unificado manga/novela/video, caché 16MB-1GB |
| 4 | **Houbi** | github.com/PineappleTwilight/houri | Apache-2.0 | Android | Activo (v1.22.17+) | **LECTOR BASE PRINCIPAL** - WebGPU, dark-mode, upscaler, universal tracker, achievements, webtoon |
| 5 | **Kisara Preview** | github.com/ifritraen/kisara | Apache-2.0 | Android | Fork de Komikku | ONNX Runtime CPU/NNAPI, colorización neural manga |
| 6 | **Reikai Preview** | github.com/unseensnick/Reikai | Apache-2.0 | Android 8+ | Activo (Mihon base) | Single-list library, dynamic grouping, multi-source merge, cover-color theming |
| 7 | **Echo-Nightly** | github.com/itsmechinmoy/echo-nightly | Desconocida | Multiplatform (Compose) | Nightly | Multiplatform Compose UI, canal nightly |
| 8 | **Namida Beta** | github.com/namidaco/namida | GPL-3.0 | Android (Flutter) | Beta | Reproductor música+video+YouTube |

---

## Houbi (PineappleTwilight/houri) — Lector Base Principal

Houbi reemplaza a Komikku como la base principal del lector en cero-X. Es un fork de Mihon con 271+ commits y releases activas (v1.22.17+).

### Características clave de Houbi integradas en cero-X:

| Característica | Descripción |
|---------------|-------------|
| **WebGPU Viewer** | Renderizado acelerado de páginas manga y webtoons con WebGPU |
| **Dark-mode Filter** | Filtro oscuro inteligente con subtoggle AMOLED |
| **Upscaler** | Escalado de imágenes con cache de PNG, modelo seleccionable |
| **Universal Tracker** | Sincronización con MangaBaka, MangaUpdates, Hikka, Kitsu, AniList, MyAnimeList |
| **Achievements Framework** | Logros: negatives, backlog, LTR, reading-time, rotating pool, ultimate tier |
| **ViewerRegistry** | Registro de viewers polimórfico para reader modular |
| **PageLoaderFactory** | Factory para carga de páginas polimórfica |
| **AppDispatchers** | Foundation de concurrencia (Metro + Injekt DI) |
| **Single SQLite Connection** | Conexión única de SQLite vía delegación Metro |
| **Webtoon Support** | Soporte webtoon con zoom lock, two-finger zoom, grayscale filter, continuous scroll |

---

## Matriz de Características por App

### 1. Anizen Preview
- **Tipo**: Preview/nightly de lector de manga
- **Mejor función**: Canal de actualización nightly/preview integrado

### 2. Foxlations
- **Tipo**: Lector manga/manhwa/novela/video multiplataforma
- **Licencia**: Apache-2.0
- **Mejor función**: RepoForge (generador de extensiones desde URL), sync AniList/MAL/Kitsu, backup local, stats en dispositivo

### 3. Kototoro
- **Tipo**: Fork de Kotatsu (linaje Tachiyomi)
- **Licencia**: Apache-2.0
- **Mejor función**: Lector unificado manga/novela/video, caché de miniaturas configurable 16MB-1GB

### 4. Houbi (PineappleTwilight/houri) ← BASE PRINCIPAL
- **Tipo**: Fork de Mihon con características avanzadas
- **Licencia**: Apache-2.0
- **Mejor función**: WebGPU viewer, universal tracker, achievements, upscaler, dark-mode filter, webtoon support

### 5. Kisara Preview
- **Tipo**: Fork de Komikku (linaje TachiyomiSY/Mihon)
- **Licencia**: Apache-2.0
- **Mejor función**: ONNX Runtime Mobile (FP16/FP32), NNAPI, colorización neural manga

### 6. Reikai Preview
- **Tipo**: Lector manga + novelas, construido sobre Mihon
- **Licencia**: Apache-2.0
- **Mejor función**: Single-list library + category hopper, dynamic grouping, multi-source merge, cover-color theming

### 7. Echo-Nightly
- **Tipo**: Reproductor de música (nightly builds)
- **Mejor función**: Arquitectura multiplatform con Jetpack Compose

### 8. Namida Beta
- **Tipo**: Reproductor música + video con YouTube
- **Licencia**: GPL-3.0
- **Mejor función**: Reproductor multimedia integrado (audio + video + streaming)

---

## Funciones Seleccionadas para cero-X (Fusionadas)

| Categoría | Función | App Origen | Implementación |
|-----------|---------|------------|----------------|
| **Lectura Base** | Lector principal con WebGPU viewer | Houbi | Módulo `houbi-reader` |
| **Lectura Unificada** | Manga/novela/video en un lector | Kototoro | Módulo `unified-reader` |
| **Biblioteca** | Single-list + category hopper | Reikai | Biblioteca plana unificada |
| **Biblioteca** | Dynamic grouping (tag/autor/source/status) | Reikai | Agrupación en tiempo real |
| **Biblioteca** | Multi-source merge (una serie, N fuentes) | Reikai | Fusor de series |
| **UI** | Cover-color theming | Reikai (desde Komikku) | Tinte dinámico desde portada |
| **UI** | Jetpack Compose + Material3 | Houbi, Echo-Nightly | UI declarativa moderna |
| **Sources** | RepoForge (generador de extensiones desde URL) | Foxlations | Creador de sources visual |
| **Sources** | Lectura detrás de Cloudflare | Foxlations | Bypass integrado |
| **Sync** | Sincronización AniList/MAL/Kitsu | Foxlations | Tracker integrado |
| **Sync** | Universal tracker (MangaBaka, MU, Hikka, Kitsu) | Houbi | Tracker unificado |
| **Backup** | Backup/restauración local de biblioteca | Foxlations | Export/import JSON/Zip |
| **Nube** | Sin cuentas, todo local, sin analytics | Foxlations | Privacy-first |
| **Stats** | Tiempo de lectura, capítulos, stats dispositivo | Foxlations | Métricas locales |
| **IA** | ONNX Runtime (FP16/FP32) CPU + NNAPI | Kisara | Inferencia IA on-device |
| **IA** | Colorización neural de manga (ONNX quantized) | Kisara | KisaraColorizer integrado |
| **Hardware** | Monitoreo CPU/GPU/RAM en logs | Kisara | Telemetría de dispositivo |
| **Multimedia** | Reproductor música + video + YouTube | Namida | MediaPlayer + ExoPlayer |
| **Multiplatform** | Compose para Android + Desktop | Echo-Nightly | UI compartida |
| **Updates** | Canal nightly/preview integrado | Anizen, Echo-Nightly, Namida | Auto-updater de canal preview |
| **Achievements** | Logros de lectura (backlog, LTR, reading-time) | Houbi | Framework de logros |
| **Upscaler** | Escalado de imágenes con cache PNG | Houbi | Upscaler integrado |
| **Webtoon** | Soporte webtoon con zoom lock, two-finger zoom | Houbi | Webtoon viewer |
| **Ingeniería Inversa** | Antigravity, DeepSeek Harness, CodeComparator, StressTester | cero-X | Módulo dedicado |

---

## Arquitectura de Fusión — cero-X Super-Fork (v0.2.0)

```
cero-X/
├── core/                          # Núcleo unificado (event bus, DI, lifecycle)
├── modules/
│   ├── houbi-reader/              # LECTOR BASE PRINCIPAL (Houbi/PineappleTwilight/houri)
│   │   ├── webgpu_viewer/         # WebGPU viewer para páginas y webtoons
│   │   ├── dark_mode_filter/      # Filtro oscuro con AMOLED subtoggle
│   │   ├── upscaler/              # Upscaler con cache PNG
│   │   ├── universal_tracker/     # Tracker universal (MangaBaka, MU, Hikka, Kitsu)
│   │   ├── achievements/          # Framework de logros
│   │   ├── viewer_registry/       # Registro de viewers polimórfico
│   │   ├── page_loader_factory/   # Factory de carga de páginas
│   │   ├── app_dispatchers/       # Foundation de concurrencia
│   │   └── single_sqlite/         # Conexión única SQLite (Metro delegation)
│   ├── unified-reader/            # Lector unificado (Kototoro + Reikai + Mihon)
│   ├── repoforge-sources/         # Generador de sources (Foxlations)
│   ├── sync-trackers/             # Sync AniList/MAL/Kitsu (Foxlations)
│   ├── backup-restore/            # Backup ZIP local (Foxlations)
│   ├── neural-ai/                 # ONNX + colorización neural (Kisara)
│   ├── media-player/              # Audio+video+YouTube (Namida + Echo)
│   ├── updates-channel/           # Canal nightly/preview (Anizen + Echo + Namida)
│   ├── deep-reverse-engineering/  # Antigravity + DeepSeek Harness + estrés
│   ├── downloads/                 # Gestor de descargas SHA-256
│   └── environments/              # Debian, contenedores, ISOs
├── build/
└── docs/
```

---

## Licencias y Consideraciones Legales

| App | Licencia | Impacto en cero-X |
|-----|----------|-------------------|
| Houbi | Apache-2.0 | Compatible, permisiva |
| Foxlations | Apache-2.0 | Compatible, permisiva |
| Kototoro | Apache-2.0 | Compatible, permisiva |
| Kisara | Apache-2.0 | Compatible, permisiva |
| Reikai | Apache-2.0 | Compatible, permisiva |
| Echo-Nightly | Desconocida | Requiere verificación |
| Namida | GPL-3.0 | **Copia izquierda** — archivos específicos mantienen GPL-3.0 |

**Decisión**: cero-X bajo Apache-2.0 para módulos de Houbi/Foxlations/Kototoro/Kisara/Reikai. Archivos derivados de Namida mantienen GPL-3.0.

---

## Android 16 (API Level 36) — Configuración

| Propiedad | Valor |
|-----------|-------|
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 26 (Android 8.0) |
| Versiones soportadas | Android 8.0 → Android 16 |

Google Play requiere targetSdk 36 para nuevas apps y actualizaciones desde agosto 2026.

---

*Informe generado para cero-X App — Fork Maestro de 8 Ecosistemas de Código Abierto con Houbi como lector base principal*