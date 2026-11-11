# Limpieza: lo que la app decía y no era

Commit de limpieza previo al trabajo de entretenimiento (IPTV, EQ, audio). El
criterio no fue «código feo» sino **afirmaciones falsas**: un método que devuelve
datos inventados, una bandera que no toca nada, una cabecera que se atribuye código
ajeno, un healthCheck que informa de una función que no existe.

## Borrado

| Qué | Dónde | Por qué |
|---|---|---|
| `searchWotaku()` | `modules/audio/AudioModule.kt` | Devolvía una pista fabricada (`id = "wotaku-" + query.hashCode()`, título «Resultado Wotaku: …», fuente «wotaku.wiki») sin hacer una petición de red. Wotaku es un índice de la comunidad, no una API de audio, y no hay cliente suyo en el repo |
| `hwAccel` / `hw_accel` | `modules/video/VideoModule.kt` + `core/ConfigManager.kt` | Se leía del config y se informaba en el healthCheck. No había ningún sitio donde tocara la reproducción, y no podía haberlo: el vídeo lo abre otra app mediante un Intent |
| `searchYouTube()` | `modules/media/MediaModule.kt` | Devolvía una pista con `url = "https://youtube.com/watch?v=placeholder"`, un enlace que no lleva a ningún vídeo |
| `compose_ui`, `youtube_support` | `modules/media/MediaModule.kt` | Banderas informadas como funciones; el módulo no pinta nada ni toca YouTube |
| `YouTubeSupport.kt` | `modules/youtube_support/` | Fichero entero: 965 bytes, cero referencias en el proyecto, «Basado en Namida» en la cabecera y un `getVideoInfo()` que devolvía «Título del video» y «Canal» para cualquier id |
| `"backend" = "wotaku"`, `"backend" = "ffmpeg"` | `core/ConfigManager.kt` | Defaults que nombran tecnología que no está en el proyecto |
| Tema `namida_music_video` | `modules/theme/ThemeEngineModule.kt` | `designSource = "namidaco/namida-snapshots"`: ese repo son 4 KB con builds beta y Namida es una EULA propietaria. Sustituido por `media_card_grid`, paleta propia |
| `auto_update_enabled = true` | `modules/updates/UpdatesModule.kt` | No hay auto-update: ni comprobación periódica ni descarga |
| Versiones `"0.1.0-beta"` y `"0.2.0-beta-android16"` | `UpdatesModule.kt`, `NightlyUpdater.kt` | Escritas a mano. La app es 1.0.1 y lo de «android16» ya se había quitado de la interfaz por falso. Ahora se leen del `PackageManager` |

## Arreglado de paso, porque al limpiar apareció

- **`MediaPlayer.stop()` antes de `release()`** en `AudioModule` y `MediaModule`:
  `stop()` lanza `IllegalStateException` desde el estado Idle, que es donde está un
  reproductor que se creó y nunca llegó a sonar. Cerrar el módulo de audio reventaba.
- **`play()` devolvía `true` sin reproducir nada.** Con `mediaPlayer == null` las
  llamadas seguras (`?.`) no hacían nada y el método acababa en `return true`. Ahora
  devuelve `false`, y `currentTrack` sólo se asigna si sonó.
- **Sólo se capturaba `IOException`.** `setDataSource` con una URL vacía lanza
  `IllegalArgumentException`, que salía hacia arriba.
- **El contexto no llegaba a los módulos.** `ConfigManager.getModuleConfig()` devuelve
  preferencias guardadas, así que `config["context"]` era siempre null y
  `VideoModule.play()` y `UpdatesModule.installUpdate()` devolvían `false` en el 100 %
  de los casos por más correcto que estuviera su código. `ModuleLifecycleManager`
  ahora inyecta `context.applicationContext` en el mapa que pasa a `initialize()`. Se
  hace ahí y no en `ConfigManager` porque ese mapa se serializa con gson al guardarse
  y un `Context` no se serializa.
- **`getCurrentTheme()` terminaba en `!!`.** Si el id activo no estaba en la lista —y
  acaba de dejar de estarlo, porque una paleta cambió de nombre— la pantalla de Diseño
  se cerraba con una excepción. Ahora cae en la primera paleta.

## Lo que sigue sin estar, dicho en la propia app

- ~~La pantalla de Diseño guarda la paleta pero no la aplica~~ **hecho en el commit
  siguiente**: `CeroXTheme` acepta un `ColorScheme` y `MainActivity` convierte la
  paleta elegida con `UIThemeManager`, que ya sabía hacerlo y no lo llamaba nadie.
  Límite: cambia los colores de Compose, no los de las pantallas en XML.
- El interruptor «Liquid Glass» y la barra de desenfoque guardan dos valores que
  ningún renderer usa. No hay desenfoque en la app. El healthCheck informa
  `desenfoque_aplicado = false`.
- No hay ecualizador, ni reproductor propio, ni HLS. El EQ viene con
  `android.media.audiofx.Equalizer` en su propio módulo; el reproductor de IPTV con
  media3/ExoPlayer, porque el `MediaPlayer` del framework no resuelve `.m3u8` con
  fiabilidad, no informa de bitrate y su `prepare()` bloquea el hilo que lo llame.
- `AudioModule` y `MediaModule` se solapan (los dos son lista + `MediaPlayer`). Se
  mantienen los dos hasta decidir cuál sobrevive; el candidato es `MediaModule`.

## Hecho en el commit siguiente: seis pantallas que reventaban al abrirlas

Encontrado mientras se comprobaban las atribuciones del tema. `CeroXApplication.coreEngine`
crea un `CoreEngine` **legacy vacío** —el getter sólo hace `CoreEngine(applicationContext)`,
nunca registra módulos; los 36 se registran en `lifecycleManager`— y estas pantallas leen
de ahí con un cast no nulo:

| Pantalla | Módulo que pide | Al abrirla |
|---|---|---|
| `ThemeSwitcherFragment` | `theme-engine` | excepción (destino «Diseño» de la barra inferior) |
| `ModeSwitcherFragment` | `mode-manager` | excepción |
| `KisaraUiFragment` | `kisara-reader` | excepción |
| `CatalogUIFragment` | `app-catalog` | excepción |
| `BooruUIFragment` | `booru-viewer` | excepción |
| `StressTestActivity` | `deep-reverse-engineering` | excepción |

`ModuleDashboardFragment` no reventaba pero enseñaba siempre cero módulos, porque
recorría ese registro vacío mientras el panel real tiene 36. Y `CrashHandler` y
`ConnectivityReceiver` publicaban en `coreEngine.eventBus`, un bus distinto del que
escucha el gestor: los eventos no llegaban a ninguna parte.

**Cómo se arregló.** `CoreEngine` está borrado, para que el camino roto no vuelva a
existir, y `core/Pantallas.kt` es el único sitio por el que una pantalla pide un
módulo: devuelve null en vez de reventar, y si el módulo aún no se inicializó pide su
arranque en segundo plano y repinta la pantalla cuando termina. De paso:

- Los catálogos de temas, modos, apps y sitios booru se registran en `init`, no en
  `initialize()`: son datos fijos, y `initialize()` corre en el hilo de arranque, así
  que una pantalla abierta antes se encontraba la lista vacía. El registro es
  idempotente.
- `getCurrentTheme()` y `getCurrentMode()` terminaban en `!!`: devuelven null.
- El botón de favoritos en lote de Kisara hacía `kisaraModule as? KisaraLibraryModule`
  —dos clases distintas— y salía en silencio por el `return`. Usa la instancia de
  `kisara-library`, que es donde vive la operación.
- El filtro por categorías del catálogo y la búsqueda de boorus eran botones que no
  hacían nada con un «Placeholder» al lado. Ahora dicen en pantalla que no están
  implementados.
- Los logs de arranque decían «v1.0.0-beta-kisara-universal initialized — 30 modules
  fused» y «APK Universal: armeabi-v7a, arm64-v8a, x86, x86_64»: versión escrita a
  mano y atrasada, un recuento fijo, «initialized» cuando la inicialización aún no ha
  empezado, y cuatro ABI cuando el APK publicado lleva una. Ahora dicen la versión
  real, el recuento del registro, y el aparato con las ABI que soporta.

## Los «Placeholder» que quedan

El repo tiene sesenta y tantos comentarios «Placeholder». La mayoría son honestos:
están al lado de un `return emptyList()` o de un `false`, o sea que dicen «esto no se
hizo» y devuelven justamente eso. Los que **fabrican** un resultado son otra cosa y
van en un barrido propio, porque tocan módulos con pruebas que hoy afirman el
resultado falso:

- `NeuralColorizer.colorize()` devuelve **una copia del bitmap de entrada** como
  `outputBitmap` con `success = true`: la app enseña la foto original y dice que la
  coloreó.
- `ModelManager.runInference()` devuelve `FloatArray(10) { 0.0f }` como si hubiera
  inferido.
- `DeepReverseEngineeringModule.evaluateAiModel()` devuelve `score = 0.95` y
  «Evaluación completada con DeepSeek Harness».
- `DeepReverseEngineeringModule.runStressTest()` devuelve `durationMs = 0`,
  `passed = true`.
- `DeepReverseEngineeringModule.compareImplementations()` elige
  `implementations.keys.firstOrNull()` y lo llama «óptima».
- `StressTester.runAiInferenceStressTest()` y `runExtensionStressTest()` cronometran
  un bucle vacío y concluyen `passed = true` (0 ms < 2000 ms siempre).
- `StressTester.runThermalStressTest()` duerme los minutos pedidos sin medir nada.
- `ToolsModule.runTool()` devuelve `success = true` y «Tool X executed with N args»
  sin ejecutar nada.
