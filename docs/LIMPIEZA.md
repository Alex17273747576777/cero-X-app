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

## El barrido de fabricaciones (commit `8d56dff7` y su continuación)

La lista de ocho sitios que había escrita aquí se quedó corta. El inventario real,
buscando las marcas en todo `src/main`, eran **66 apariciones y unas 45
fabricaciones vivas en 30 ficheros**. La diferencia no es un detalle: una lista
hecha a mano encuentra lo que uno recuerda, y lo que queda fuera sigue mintiendo
con el repo en verde.

Se barrió con una regla única: **o se hace de verdad, o se devuelve un fallo con el
motivo**. Lo que no se hace nunca es devolver éxito con datos inventados.

### Lo que ahora se ejecuta de verdad

| Sitio | Antes | Ahora |
| --- | --- | --- |
| `NeuralColorizer.colorizePage` | copia del bitmap de entrada con `success = true` | inferencia ONNX: entrada `[1,5,H,W]`, salida `[1,3,H,W]` leída con `SalidaTensor` |
| `KisaraNeuralColorizerModule.colorizePage` | lo mismo, y es el módulo registrado `kisara-neural-colorizer` | inferencia con los nombres que exige `verifyTensorProbe()` (`input` → `rgb`) |
| `ModelManager.runInference` | `FloatArray(10) { 0.0f }` | forma declarada por el modelo, entrada comprobada, salida aplanada |
| `HardwareMonitor.getCpuUsage` | `0.0f` fijo | delta entre dos lecturas de `/proc/stat`; `null` cuando SELinux no deja leer |
| `HardwareMonitor.getDeviceTemperature` | `null` siempre | `/sys/class/thermal/thermal_zone*/temp` + `getThermalStatus()` con `PowerManager` |
| `UnifiedReaderModule.extractCoverColor` | `#6200EE` para cualquier archivo | moda de los píxeles cuantizados a 4 bits, sin negros ni blancos |
| `ComicModule.importFromFolder` | «Cómics importados» sin abrir la carpeta | lista la carpeta, filtra por los formatos declarados y añade a la biblioteca |
| `BackupModule.exportLibraryJson` | devolvía la entrada tal cual | valida que sea JSON y construye el sobre con formato, versión y fecha |
| `ServerModule.connectToServer` | «Conectado» sin tocar la red | abre TCP con 5 s de espera y dice la dirección que contestó o la excepción |
| `ExtensionSandboxManager.getActiveExtensionsCount` | `0` fijo | cuenta los directorios de aislamiento que existen |
| `DownloadTask.cleanup` | método vacío | pregunta al `DownloadManager` y retira las terminadas |
| `AntigravityAnalyzer.analyzeApk` / `analyzeDex` | «análisis completado» mirando el tamaño | contenedor ZIP (entradas, DEX, manifiesto, firma) y cabecera DEX (0x38/0x58/0x60); estado `parcial` |
| `AchievementsFramework.getProgress` | 0f o 1f según el desbloqueo | lo que se anota con `recordProgress()` |
| `TrackerSyncManager.syncProgress` | «Sincronizado con X» sin petición | pasa por `UniversalTracker.syncProgress`, que sí decía la verdad |
| `StressTester.runDownloadStressTest` | «50 descargas concurrentes» | mide el pool de 10 hilos y lo llama lo que es: tareas de 100 ms |

### Lo que ahora devuelve un fallo con el motivo

La función no existe en la app y no se finge:

- **`StressTester`**, cinco de las seis pruebas. El bucle de biblioteca estaba vacío
  y decía «Cargadas N series»; el de inferencia no ejecutaba nada y, como 0 ms es
  menor que el umbral de 2000, «pasaba» siempre; el térmico dormía la duración
  pedida —dos horas de hilo bloqueado desde una pantalla— y devolvía `passed = true`
  con la temperatura en `null` porque `throttlingDetected` estaba en `false` fijo; el
  de extensiones devolvía el propio parámetro como si fuera una medida; el de
  compatibilidad marcaba `true` para cada nivel de API pedido, que es algo que hace
  el CI con emuladores, no una app en ejecución.
- **`DeepReverseEngineeringModule.runStressTest`** devolvía `durationMs = 0` y
  `passed = true` para los seis tipos sin llamar al probador. Ahora despacha al
  método que corresponde y devuelve lo que midió. **`evaluateAiModel`** daba 0.95 de
  nota sin cargar el modelo ni ejecutar el benchmark.
- **`CodeComparator.compareFunction` / `compareReaders`** elegían «la óptima»
  quedándose con la primera clave del mapa, que es el orden de llegada.
- **`ReverseEngineeringModule.cloneEcosystem`** devolvía `status = "cloned"` con la
  herramienta `git` sin ejecutar nada: no hay git en el dispositivo. La clonación del
  ecosistema se hace en el ordenador con `scripts/clone_all_repos.sh`.
- **`ToolsModule.runTool`** devolvía «Tool X executed with N args». Las seis
  herramientas del catálogo son binarios de escritorio (`gallery-dl`) o scripts de
  navegador (JAM, HDoujin, MangaPlus NG): `ToolInfo` gana un campo `ejecutable` que
  vale `false` por defecto y el `healthCheck` pasa sus seis claves a `false`.
- **`KaiModule.runLinuxCommand`** devolvía «Ejecutado en sandbox Linux» para
  cualquier comando. No hay proot ni rootfs en la app.
- **`RepoForgeSourceModule.bypassCloudflare`** y **`RepoForgeGenerator.bypassCloudflare`**
  devolvían éxito con «Bypass simulado». Resolver un desafío de Cloudflare exige un
  navegador o un cliente que replique su TLS.
- **`ExtensionCompiler`** y **`ExtensionBuilder`** devolvían `success = true` con la
  ruta de un `.jar` o un `app-debug.apk` que nadie había escrito. No hay JDK ni
  Gradle en el dispositivo; la comprobación de estructura del `ExtensionBuilder` sí
  es real y se conserva.
- **`ReaderModule.search`**, **`NovelModule.searchNovels`** y
  **`BooruModule.searchBooru`** devolvían éxito con resultados inventados o con una
  lista vacía que se leía como «buscado, cero resultados». Ahora la lista va vacía y
  `BooruSearchResult` gana un campo `motivo`, porque sin él no se distinguía «no
  encontré nada» de «no llegué a buscar».
- **`BooruModule.downloadMedia`** devolvía «Descargado» sin abrir una conexión.
- **`PrismaModule.processDownloadQueue`** marcaba las tareas como `DOWNLOADING`
  respetando los huecos libres y ahí acababa todo: la pantalla enseñaba descargas en
  curso que no existían. Ahora pasan a `ERROR`, que es lo que le ocurre a una tarea
  que nadie va a descargar.
- **`WebGPUViewer.detectWebGPUSupport`** devolvía `true` en cualquier Android 12+.
  Android no expone WebGPU a una `GLSurfaceView` y la app no lleva Dawn: se
  renderiza GLES 3.

### Lo que se borró

`ExtensionStore` registraba tres extensiones inventadas —Madara Manga, WordPress
Anime y Novel Updates— con `downloadUrl` apuntando a `extensions.cero-x.app`, un
dominio que no existe, y `sha256 = "placeholder"`. Una huella falsa en un instalador
de extensiones es lo que menos se puede fingir: es el valor con el que se va a
verificar lo que se instala. El catálogo empieza vacío y lo llenará un repositorio
de extensiones externo, con su índice y su huella de firma, que vive fuera del APK.

### La puerta

`FabricacionTest` (nueve pruebas) prohíbe las marcas en `src/main` y comprueba que
los sitios donde estaban siguen haciendo lo que dicen: que los dos colorizadores
crean el tensor y leen la salida con el lector que comprueba la forma, que el gestor
de modelos consulta la forma declarada, que la pantalla de estrés llama al módulo
fuera del hilo principal, que `getCpuUsage` puede devolver `null`, que el catálogo
de herramientas distingue entre estar listado y poder ejecutarse, que los
analizadores dicen `parcial`, y que nadie afirma una conexión o una ejecución que no
ocurre.

Prohibir la palabra no basta —lo siguiente sería borrar la palabra y dejar la
mentira—, por eso la mitad de las pruebas son positivas.

Dos detalles que costaron tiempo y quedan anotados para quien toque esto:

1. **El texto que explica por qué algo está prohibido no puede citar lo
   prohibido.** Los comentarios de `src/main` que describían arreglos anteriores
   citaban las marcas y se reescribieron en el mismo barrido. Es el mismo error que
   hizo fallar `PantallasTest` tres veces en este repo.
2. **Las mayúsculas importan.** Buscar `TODO:` sin distinguirlas acusa a cualquier
   `metodo:`, que contiene esa secuencia.
