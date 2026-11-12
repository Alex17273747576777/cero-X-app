# cero-X

> **Estado, 2026-11-10.** La beta `1.0.8-beta-fuentes-verificadas` incorpora índices Project Nox/Kotatsu fijados por commit, descarga de APK con hash y certificado comprobados, y operaciones reales de biblioteca Kisara. La instalación de extensiones Nox se etiqueta **para Mihon/Aniyomi**: el host antiguo de cero-X todavía no implementa `extensions-lib 1.6`, y los JAR Kotatsu no se ejecutan. La conversación con proveedores IA y la validación visual en el LG V60 siguen pendientes. Véanse [auditoría de fuentes](docs/AUDITORIA-FUENTES-Y-KISARA.md), [asistente](docs/ASISTENTE-Y-COMPETENCIA.md) y [firma](docs/FIRMA-NUEVA.md).


### Un lector de manga, novelas y anime que trae la IA dentro del APK.

`Android 8.0 – 16` · `arm64 · armv7 · x86 · x86_64` · `0 modelos ONNX empaquetados` · `GPL-3.0`

---

Esto no es una promesa de lo que cero-X llegará a ser. Es lo que hace hoy,
medido, con lo que todavía no hace escrito al lado sin adornos. Si algo de aquí
no se puede comprobar con un comando, no está en este README.

**[Descargar la beta](../../releases/latest)** · **[Cómo se construye](entorno/COMPILAR-Y-FIRMAR.md)** · **[Qué dicen los modelos](android/app/src/main/assets/models/README_MODELS.md)**

---

## Índice

1. [Lo que hace hoy](#lo-que-hace-hoy)
2. [Los cinco modelos, y por qué están dentro](#los-cinco-modelos-y-por-qué-están-dentro)
3. [El feed multi-entrada](#el-feed-multi-entrada)
4. [Sugerencias calculadas en el teléfono](#sugerencias-calculadas-en-el-teléfono)
5. [El lector rápido](#el-lector-rápido)
6. [Extensiones: tres arquitecturas](#extensiones-tres-arquitecturas)
7. [Compatibilidad con backups](#compatibilidad-con-backups)
8. [De dónde viene cada idea](#de-dónde-viene-cada-idea)
9. [Lo que no hace](#lo-que-no-hace)
10. [Los números](#los-números)
11. [Compilar](#compilar)
12. [Pruebas](#pruebas)

---

## Lo que hace hoy

| | función | estado |
|---|---|---|
| 📚 | **Panel de módulos** con el estado real de cada uno, no una lista decorativa | funciona |
| 🎨 | **Cambio de diseño** con desenfoque ajustable y temas del ecosistema | funciona |
| 🔀 | **Modos** de interfaz | funciona |
| 🖼️ | **Booru** y **Catálogo** de apps del ecosistema | funciona |
| 📰 | **Feed multi-entrada**: seis tipos de entrada, cada una con su cursor | funciona |
| ✨ | **Sugerencias sin red** calculadas con lo que ya lees | funciona |
| 🎨 | **Colorización de páginas** con modelo ONNX dentro del APK | funciona · interruptor en el lector |
| 🔍 | **Reescalado ×4** con dos modelos, elegidos según la página | funciona · interruptor en el lector |
| 📖 | **Lector** con tres modos, orden natural, marcapáginas y caché en bytes | funciona (falta verlo en un panel) |
| 📖 | **Caché de páginas** con tope en bytes y prefetch adaptado a la red | funciona |
| 🔌 | **Catálogo Nox/Kotatsu** con procedencia, NSFW/rotos y verificación de APK | funciona · ejecución Mihon/Kotatsu pendiente |
| 📚 | **Biblioteca Kisara** con búsqueda avanzada, favoritos y fusión local | funciona |
| 💾 | **Importador de backups** de 6 formatos, detectados por bytes | funciona |
| 🩺 | **Estado del sistema**: privilegios, motor JS y fuentes, medidos y visibles | funciona |
| 🔗 | **Camino completo** Panel → Catálogo → Lector → Tracker, declarado y probado | funciona |

Hasta el 9-nov-2026 estas dos líneas decían «funciona (falta pantalla)», y era
verdad: existía el modelo, existía `ReaderPipeline` probado, y **no había ningún
sitio en la app desde donde encenderlo**. El lector decodificaba la página y la
pintaba sin pasar por el pipeline: 373 MiB de modelos dentro del APK que nunca se
ejecutaban.

Ahora hay pantalla (`ui/ia/PantallaIa.kt`) y está conectada: el botón de la barra
del lector abre los interruptores, cada uno con su coste medido al lado, y una
prueba que **ejecuta el modelo de verdad** sobre una imagen sintética y dice los
milisegundos y el tamaño de antes y después. El camino completo quedó así:

    PageLoader → IaDePagina → ReaderPipeline → KisaraColorizer / KisaraUpscaler

Y de paso se arreglaron dos fallos que impedían que esos modelos devolvieran nada:

- **El casteo de la salida.** Los dos módulos leían el tensor como
  `Array<Array<FloatArray>>` y la salida tiene cuatro niveles. Lanzaba
  `ClassCastException` en cada página y el `catch` lo contaba como «el modelo
  falló». Medido con los modelos del APK: `colorize.onnx` [1,5,H,W] → [1,3,H,W];
  `anime4x.onnx` y `esrgan_x4.onnx` [1,3,H,W] → [1,3,4H,4W].
- **El tamaño del reescalado.** El alto se deducía dividiendo el total de píxeles
  entre el ancho, lo que con una salida ×4 da cuatro veces el alto real y crea un
  `Bitmap` del tamaño equivocado. Ahora las dimensiones se leen del tensor
  (`SalidaTensor`), y si no cuadran se dice en vez de inventarse un tamaño.

Sigue apagado por defecto, y por el mismo motivo de antes: 1 490 ms por página de
colorización y entre 2 673 y 24 587 ms por página de reescalado. La diferencia es
que ahora se enciende, se apaga y se ve cuánto tarda.

Es un avance real sobre «motor listo» —antes no había nada entre el modelo y la
nada— y sigue sin ser «lector completo». Las dos cosas son verdad a la vez.

---

## Los cinco modelos, y por qué están dentro

Cada uno se **cargó y ejecutó una inferencia real** con ONNX Runtime 1.17 antes
de entrar en el APK. Los tiempos son de un contenedor x86 de 2 núcleos: sirven
para comparar modelos entre sí, **no son tiempos de móvil**.

| modelo | peso | qué hace | CPU |
|---|---:|---|---:|
| `encoder_fp16.onnx` | 163,89 MiB | manga-ocr: imagen → tokens (ViT 224×224) | 715–824 ms |
| `colorize.onnx` | 117,26 MiB | colorización de páginas en blanco y negro | 1 490 ms |
| `esrgan_x4.onnx` | 63,95 MiB | reescalado ×4 general | 24 587 ms |
| `decoder_model_quantized.onnx` | 28,26 MiB | manga-ocr: tokens → caracteres (vocabulario 6 144) | 7–19 ms |
| `anime4x.onnx` | 4,92 MiB | reescalado ×4 de línea clara | 2 673 ms |

**Total: 378,28 MiB.** Los modelos van `Stored` (sin comprimir), para que el
runtime los mapee desde el archivo en vez de descomprimir cientos de MB en RAM.

### Lo que se descartó, con el error exacto

Esto es la parte que casi nadie escribe, y es la que ahorra las tardes.

- **`encoder_model_int8.onnx` y `decoder_model_int8.onnx`: no cargan.**
  `ORT_NOT_IMPLEMENTED: Could not find an implementation for ConvInteger(10)`.
  Habrían sido **111 MB de peso muerto** que solo se descubre cuando el usuario
  abre la app y la IA no responde.
- **`decoder_model_fp16.onnx`: no carga.** Error de tipos al construir el grafo:
  `tensor(float16)` donde espera `tensor(float)`.
- **`decoder_model.onnx` (fp32, 112 MiB): funciona**, pero el quantized hace lo
  mismo con 28,26 MiB. Se prefiere el pequeño.
- **`real_esrgan_x2.onnx` (64 MiB): funciona**, pero el general ×4 ya cubre el
  caso y el de anime resuelve el otro nueve veces más rápido.

---

## El feed multi-entrada

Seis tipos de entrada, conviviendo:

| entrada | qué trae |
|---|---|
| **Mi biblioteca** | novedades de lo que ya sigues |
| **Descubrir** | lo que las listas de fuera recomiendan para ti |
| **Para ti** | calculado en el teléfono, sin enviar tu historial |
| **Continuar leyendo** | lo que dejaste a medias |
| **Nuevos capítulos** | actualizaciones que aún no has abierto |
| **Una fuente** | solo las novedades de una fuente elegida |

Cuatro reglas que lo separan de un feed cualquiera:

1. **Cursor por entrada, no global.** Dos fuentes que paginan a ritmos distintos
   no se pisan; una lenta no frena a las demás.
2. **Una entrada caída no tumba el inicio.** Se marca, se reintenta y las otras
   siguen. Es la diferencia entre un feed usable y uno en blanco porque un
   proveedor cambió su HTML.
3. **Tres fallos seguidos y la entrada se aparca sola.** Se reactiva a mano.
   Reintentar para siempre es gastar batería sin que el usuario sepa por qué.
4. **Desactivar no borra.** Nada desaparece por accidente.

La fusión es por identificador: la misma obra en «biblioteca» y en «nuevos
capítulos» se muestra una vez, con el motivo de la entrada que manda en el orden.

---

## Sugerencias calculadas en el teléfono

**Tu historial no sale del aparato.** Cero red, cero telemetría, cero «mejora tu
experiencia» enviando lo que lees a un servidor.

El motor es TF-IDF con distancia de coseno sobre **autores y etiquetas**, no
sobre las palabras del título: en manga los títulos son largos y llenos de
nombres propios, y dos obras del mismo autor no comparten ninguna palabra. El
autor es la señal más fuerte que existe.

Y se explica: cada sugerencia lleva su motivo —*«Porque lees naoki urasawa»*—,
no un porcentaje suelto. Una recomendación que no se puede justificar es ruido.

```kotlin
val motor = SuggestionEngine()
val perfil = motor.perfil(historial)                       // autor + etiquetas, normalizado
val sugerencias = motor.recomendar(catalogo, historial)     // ordenadas por afinidad real
```

El peso de cada obra se **amortigua con logaritmo**: leer 300 capítulos de una
sola obra no debe tapar todo lo demás. Sin eso, el término más frecuente domina
el vector y la recomendación se vuelve «más de lo mismo».

---

## El lector rápido

Rápido no es una sensación: es una política que se puede medir.

**Caché con tope en bytes, no en páginas.** Un caché «de 20 páginas» no significa
nada: 20 páginas de webtoon a 1080 px son 8 MB; 20 de manga a doble página en un
tablet de 2560 px son 160 MB. Contar páginas es la forma más fácil de comerse la
memoria de un móvil sin que nadie entienda por qué.

**Prefetch adaptado a la red.** Tres páginas por delante en wifi, una sola con
datos medidos, ninguna sin red. Descargar un capítulo entero por delante gasta la
tarifa del usuario sin pedirle permiso. Y al cambiar de capítulo, las peticiones
en vuelo se cancelan: prefetch que sigue trayendo lo que ya no vas a leer es la
forma más tonta de gastar batería.

`PageCache.hitRate()` devuelve un número. Eso es lo que permite decir «la caché
sirve» en vez de suponerlo.

---

## Extensiones: tres arquitecturas

El ecosistema tiene formatos incompatibles. Una URL en un índice no demuestra que
el host pueda ejecutarla.

| capa | formato | estado comprobado en cero-X |
|---|---|---|
| **Mihon/Aniyomi** | APK `extensions-lib`, clase declarada en `tachiyomi.extension.class` | Catálogo, descarga, SHA-256/firma e instalador de Android. Ejecución dentro del lector: **pendiente de migrar el contrato 1.6**. |
| **Kotatsu** | JAR con modelos y `MangaLoaderContext` | Explorador de índices y estado roto/NSFW. Ejecución: **no incluida**. |
| **JavaScript** | archivo JS evaluado en un motor embebido | Host QuickJS propio y pruebas; no se afirma compatibilidad universal con extensiones externas. |
| **Datos** | backups `.tachibk` / `.proto.gz` / JSON | Detección por bytes en `modules/backup_import`. |

Project Nox se consume en instantáneas fijadas por commit: 1.618 extensiones de
manga y 273 de anime. MangaDex 1.6.5 se descargó y verificó con hash y firma
Project Nox reales. Esto demuestra integridad del paquete, no seguridad del sitio
ni compatibilidad del parser. Kotatsu multi-parsers aporta 3.662 entradas de
metadatos, 1.113 marcadas rotas; no se cargan sus JAR.

La capa de datos detecta el formato **por los primeros bytes, no por la extensión**:
`.bak` puede ser zip, gzip o SQLite según quién lo exportó, y rechazar un backup
válido por su nombre es el tipo de fallo que hace que la gente abandone.

> **Nota de campo:** el repositorio con más estrellas del nicho,
> `gedoor/legado` (47 217 ★), está **vaciado**: dos archivos, un README que es un
> aviso legal y cero releases. Las cuatro webs del ecosistema lo siguen
> recomendando como la número uno. Cualquier plan basado en «fuentes de reglas al
> estilo Legado» se apoya en un formato cuyo implementador ya no existe.

---

## Compatibilidad con backups

Seis formatos, detectados por bytes. Probado con archivos reales generados en la
misma sesión: **6/6**.

```
.tachibk      gzip   1F 8B        Mihon y derivados
.proto.gz     gzip   1F 8B        los mismos datos, otro nombre
.bak          zip    PK\x03\x04   backups empaquetados
.bak          gzip   1F 8B        depende de quién exportó
.db           SQLite "SQLite format 3\0"
.json         JSON   { o [
```

Un backup es un archivo de fuera que se descomprime en memoria. Eso es
literalmente la receta de una bomba de descompresión: 10 MB de gzip pueden dar
varios GB. Se limita **antes** de abrirlo: 256 MiB de archivo, 512 MiB
descomprimidos, 10 000 entradas de zip, 2 niveles de anidamiento.

---

## De dónde viene cada idea

cero-X no inventó esto de la nada. Estas son las fuentes, con el archivo concreto
que se estudió y qué se tomó de cada una.

| repo | ★ | qué se tomó | archivo de referencia |
|---|---:|---|---|
| **Kisara** `ifritraen/kisara` | 2 | los modelos de colorización y superresolución, y el sistema de sugerencias locales | `data/ai/MangaColorizeEngine.kt`, `data/suggestions/SuggestionsWorker.kt` |
| **ShinKu** `Harrys-HQ/ShinKu` | 46 | cursor de paginación por fuente; IA que funciona sin red (`MangaEmbeddingJob`) | `ui/browse/feed/SourcePageCursor.kt` |
| **AniZen** `salmanbappi/AniZen` | 201 | feed por pestañas y un asistente de IA con preferencias propias | `ui/home/FeedScreen.kt`, `data/ai/AiManager.kt` |
| **Reikai** `unseensnick/Reikai` | 47 | la disciplina: 21 tests de lector que convierten «va rápido» en algo verificable | `androidTest/presentation/reader/*` |
| **Kototoro** `Kototoro-app/Kototoro` | 677 | el motor de sugerencias más grande del ecosistema (35 archivos) | `discover/ui/DiscoverViewModel.kt` |
| **Miyorare** `Noirero/Miyorare` | 20 | fallar con un motivo, no con «error inesperado» | `IncompatiblePluginException.kt` |
| **DropSauce** `HuzaifaKhalid1311/DropSauce` | 233 | feed ligero y sugerencias sencillas sin base de datos pesada | `ui/` |

**Nada de esto es código copiado, con una excepción que se dice.** De los siete se
tomó la idea y se reescribió con las piezas que cero-X ya tiene; portarlos enteros
habría arrastrado su base de datos. La excepción es el contrato de extensiones: el
paquete `eu.kanade.tachiyomi.source` **sí existe** en este repo (seis ficheros),
porque sin esos nombres las extensiones del ecosistema no cargan. Mihon es
Apache-2.0 y permite reimplementar o reutilizar ese contrato conservando el aviso,
que está en [`NOTICE`](NOTICE).

Cuando una idea vino de un GPL-3.0 (DropSauce, Miyorare) se tomó **solo el
concepto**, nunca una línea: mientras cero-X fue Apache-2.0 era lo único posible.
Desde el 2026-11-11 cero-X es GPL-3.0, así que ese código ya se podría usar; sigue
sin usarse porque no se ha hecho, no porque no se pueda. Los detalles en
[`docs/LICENCIA.md`](docs/LICENCIA.md).

### Una corrección sobre «Houri, el reemplazo de Komikku»

Houri **no es** el reemplazo de Komikku. Los números:

| | Komikku | Houri |
|---|---:|---:|
| estrellas | **5 001** | 45 |
| archivos | 3 004 | 3 758 |
| último push | 2026-11-07 | 2026-11-04 |
| licencia | Apache-2.0 | Apache-2.0 |

Komikku tiene **111 veces** más estrellas y sigue vivo. Houri es un fork pequeño
de la misma cadena (Tachiyomi → TachiyomiSY → Mihon → Komikku → Houri), no su
sucesor. Se usa la cadena como referencia de compatibilidad de extensiones, que
es donde sí manda, y no se presenta a Houri como algo que no es.

Y sobre **Reikai como base por velocidad**: es cierto que es el único de los
cuatro con tests de lector reales (25 tests de UI, 21 de ellos del lector:
`PageSlideTest`, `TiledPictureTest`, `WebtoonResizeAfterZoomTest`,
`RenderedLinesParityTest`). Komikku, Yokai y Houri tienen **cero**. Eso no
demuestra que Reikai sea más rápido, demuestra que es el único que **mide** si lo
es. Sin emulador no hay milisegundos, y sin milisegundos «es más rápido» es una
opinión. Se tomó su disciplina de prueba, no su código.

---

## Lo que no hace

Esta sección existe porque un README sin ella es publicidad. Cada línea dice
**qué se midió** y **qué falta**, no una impresión.

- ❌ **No está probado que arranque en un aparato físico.** La máquina donde se
  construyó no tiene `/dev/kvm`. Se intentó dos veces con emulación por software
  (TCG) y las dos veces QEMU murió con `exit 139` y
  `hanging thread 'QEMU2 main loop'`; los dos intentos están en
  `entorno/LECTOR-Y-ENTORNO.md` §5 con sus comandos y su salida. Lo verificado es
  estático: actividad de arranque declarada, tema presente, 26 899 clases en 3
  dex, icono en 6 variantes, firma v2 válida. **No se afirma que arranque.**

- ⚠️ **Lee manga de punta a punta en las pruebas; falta verlo en una pantalla.** Lo
  que sí hay y está probado:
  `CatalogoActivity` → ficha → `ReaderActivity` → `ReaderState` → `PageLoader`,
  con 14 pruebas que recorren la cadena contra un servidor HTTP real
  (`LectorExtremoAExtremoTest`). Lo que no se ha visto es **un píxel en un panel**:
  aquí no hay dispositivo ni emulador. La decodificación final usa
  `BitmapFactory`, que en la JVM de pruebas devuelve `null` y por eso no se
  simula. Dicho de otro modo: la lógica está medida, el dibujado no.

- ✅ **Sincroniza con AniList contra el servidor real.** Medido con `curl`:
  consulta pública `HTTP 200` con `ONE PIECE`; token inválido `HTTP 400`
  (`errors[].status`); MAL sin token `HTTP 403`. Esa medición destapó y corrigió
  un fallo real —el `400` se clasificaba como error reintentable y ahora es
  `RECHAZADO`— y quedó fijada en 12 pruebas (`TrackerRespuestasRealesTest`).
  Lo que **sigue faltando** son credenciales de una cuenta real: no hay token de
  AniList ni de MAL en este entorno, así que no hay ninguna lista sincronizada.
  Se prueba la conversación con el servidor, no una cuenta.

- ✅ **Las extensiones JavaScript tienen motor, elegido con números medidos.**

  | motor | licencia | binario | bytecode | veredicto |
  |---|---|---|---|---|
  | `app.cash.quickjs:quickjs-android:0.9.2` | Apache-2.0 | 854 KB (arm64) | Java 8 | **elegido** — es el que usa Mihon |
  | j2v8 6.3.4 | EPL-1.0 | 53 MB por ABI | — | descartado: tamaño y licencia |
  | Rhino 1.9.2 | MPL-2.0 | 1,6 MB, 710 clases | Java 11 | descartado: sin JIT |

  QuickJS ejecuta JavaScript real: 11 pruebas de sintaxis con el `qjs` oficial
  (`scripts/probar-quickjs-linux.py`), 11 del anfitrión en Android
  (`MotorJsTest`) y 8 que **lanzan el binario** y comparan su salida
  (`ExtensionEjecutadaTest`). Lo que no hace: `quickjs-android` no expone
  `interruptHandler`, así que un script que no termina deja el motor marcado
  como contaminado y el hilo atascado no se puede matar. En escritorio sí se
  mata, porque es un proceso.

- ⚠️ **Hay escritorio en forma de biblioteca ejecutable; no hay aplicación de
  escritorio.** Lo que existe y se ejecuta:

  ```bash
  java -jar cerox-comun-todo.jar estado      # plataforma, motor y tabla de capacidades
  java -jar cerox-comun-todo.jar ordenar     # orden natural, leyendo de la entrada
  java -jar cerox-comun-todo.jar tracker 12 40 200
  java -jar cerox-comun-todo.jar extension ejemplo.js   # ejecuta la extensión en QuickJS
  ```

  Ese `.jar` (3,29 MB, con stdlib y coroutines dentro) y el `.js`
  (`cerox-comun.js`, 39 KB, con 15 comprobaciones en verde desde Node) salen del
  **mismo** fuente Kotlin que la app, no de una copia. Lo que no hay es ventana,
  ni instalador, ni empaquetado por plataforma. No hay una app de escritorio que
  abrir.

- ⚠️ **iOS está declarado y compila a medias: no hay binario.** Los targets
  `iosX64`, `iosArm64` e `iosSimulatorArm64` están en el build y el código común
  los contempla, pero enlazar un framework de Kotlin/Native necesita macOS con
  Xcode. En esta máquina Gradle los desactiva y lo dice. **No hay aplicación de
  iOS.**

- ❌ **No hay traducción neuronal japonés→español.** Hay reglas de variante
  (`es-DO`, `es-AR`, neutra) y glosario del usuario, probadas. Un modelo de
  traducción no está, y no se presenta como si estuviera.

- ❌ **No hay lector de EPUB ni de novelas ligeras** más allá de la fuente de
  carpetas con imágenes.

- ⚠️ **La instalación silenciosa no se ha ejecutado nunca en un teléfono.** Están
  el sondeo de privilegios (`PrivilegeProbe`: root > Shizuku > Dhizuku > normal,
  con `usable = instalado && servicioVivo && autorizado`) y el instalador
  (`SilentInstaller`: sha256 → `pm install -r -t` → `"Success"` +
  `getPackageInfo`), con sus pruebas. Y ahora **se pueden ver** desde la app:
  el botón de estado del sistema muestra el nivel detectado y el motivo de cada
  negativa. Lo que no se ha hecho es instalar una APK de verdad por ese camino:
  requiere un teléfono con Shizuku o root, y aquí no hay ninguno.

---

## Los números

Todo lo de esta tabla se midió con un comando en la máquina donde se construyó.

| | valor |
|---|---:|
| APK universal con modelos | **453,45 MiB** (475 478 043 B) |
| APK universal sin modelos | 74,97 MiB |
| modelos ONNX dentro | 378,28 MiB |
| librerías nativas (4 ABIs) | 62,32 MiB |
| `classes.dex` | 39,84 MiB |
| archivos Kotlin | 125 |
| pruebas unitarias (app Android) | **393 descubiertas: 387 ejecutadas y 6 omitidas; 0 fallos y 0 errores** ([CI del código Android](https://github.com/Alex17273747576777/cero-X-app/actions/runs/42867247425)) |
| pruebas del núcleo compartido | **41 en JVM y 33 en JS, todas pasando** |
| comprobaciones del `.js` desde Node | **15, todas en verde** |
| errores de compilación | **0** (venían de 519) |
| tamaños de backup detectados | 6 de 6 |

```bash
# verificable en cualquier clon
$ ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest
393 pruebas · 0 fallos · 0 errores · 6 saltadas (CI 2026-11-10)

# el núcleo compartido, en las dos plataformas que esta máquina compila
$ cd comun && gradle jvmTest jsNodeTest
jvmTest: 41 pruebas, 0 fallos
jsNodeTest: 33 pruebas, 0 fallos

# y el artefacto web, ejecutado
$ node comun/scripts/probar-web.mjs
15/15 en verde

$ apksigner verify --print-certs app-release.apk
Signer #1 certificate DN: CN=cero-X, O=La Bandita, C=DO
```

---

## Compilar

```bash
sudo apt install -y openjdk-21-jdk

# swap: sin esto el daemon de Gradle muere a mitad de compilación
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile

# SDK y Gradle en /opt, no en /home
sudo mkdir -p /opt/android-sdk/cmdline-tools
sudo unzip -q commandlinetools-linux-*.zip -d /tmp/sdkx
sudo mv /tmp/sdkx/cmdline-tools /opt/android-sdk/cmdline-tools/latest
sudo unzip -q gradle-8.11.1-bin.zip -d /opt
sudo chown -R $USER /opt/android-sdk /opt/gradle-8.11.1

yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# los modelos no van en git (GitHub rechaza >100 MB): se bajan con hash
python3 scripts/bajar-modelos.py

cd android && /opt/gradle-8.11.1/bin/gradle assembleRelease \
  --no-daemon --console=plain --max-workers=1
```

Las tres trampas que cuestan una tarde cada una —swap, heap de R8 y el orden de
`signingConfigs`— están explicadas en
[`entorno/COMPILAR-Y-FIRMAR.md`](entorno/COMPILAR-Y-FIRMAR.md).

---

## Pruebas

```bash
cd android && /opt/gradle-8.11.1/bin/gradle testDebugUnitTest --no-daemon
```

| suite | qué comprueba |
|---|---|
| `FeedManagerTest` | fusión por id, entrada caída, aparcado tras 3 fallos, desactivar no borra, máximo de entradas, orden por fecha |
| `SuggestionEngineTest` | mismo autor primero, no recomienda lo leído, sin historial no inventa, normalización, coseno en 0–1 |
| `PageCacheTest` | tope en bytes, página mayor que el caché no lo vacía, hit rate, recorte, claves distintas |
| `ReaderPipelineTest` | sin IA no se toca ni un píxel, fallo devuelve el original, excepción no se propaga, reescalar antes de colorizar |
| `ModelHonestyTest` | ningún módulo anuncia un modelo que no esté en el APK |
| `StressBackupDetectorTest` | 4 000 entradas de ruido, firmas truncadas, 16 MiB en <2 s, 1 000 aperturas sin fugar descriptores |
| `StressFeedConcurrencyTest` | 8 hilos × 400 ops, ids únicos en paralelo, entrada caída aparcada, borrar durante refresco no resucita |

El caso más importante es el de **normalización**: sin él, una obra con 20
etiquetas genéricas gana siempre a una que comparte las dos etiquetas reales del
usuario. Es el error clásico de todo sistema de recomendación casero y está
cubierto por una prueba que falla si se rompe.

---

## Licencia

**GPL-3.0 desde el 2026-11-11.** Antes este README declaraba Apache-2.0 «igual que
el linaje» y presentaba esa licencia como definitiva. Cambió por decisión del autor,
que es el único autor del repositorio (los cuatro identificadores que aparecen en la
lista de contribuidores son suyos), y por una razón práctica: lo que se quiere
fusionar a partir de ahora —RootlessViPER4Android, Echo-Music, N-Zik, cloudstream—
es GPL-3.0, y el código GPL no entra en un proyecto Apache-2.0. La compatibilidad va
en una dirección: Apache dentro de GPL sí, GPL dentro de Apache no.

Qué cambia de verdad:

  · Quien distribuya esta app o una versión modificada **debe ofrecer el código
    fuente correspondiente**. El repositorio es público, así que la obligación
    está cumplida, y `LicenseCompliance` la comprueba en vez de darla por hecha.
  · El linaje Apache-2.0 (Mihon, Komikku, Houri, Kisara, Reikai, Foxlations) y el
    MIT (ONNX Runtime) **conservan sus términos y sus avisos**: por eso
    [`NOTICE`](NOTICE) sigue existiendo y sigue empaquetado en el APK.
  · Este cambio es de ida. La GPL no permite volver a algo más permisivo quitando
    derechos a quien ya recibió el código bajo GPL.

Lo que ninguna licencia arregla: **namidaco/namida no es software libre**, es una
EULA propietaria que prohíbe redistribuir, reempaquetar y usar partes del código.
No se copia nada de ahí. Los detalles, con la procedencia de los ocho repositorios
de referencia comprobada uno a uno, están en [`docs/LICENCIA.md`](docs/LICENCIA.md).

Un aviso que no es cosmético: **`anime4x.onnx` no tiene licencia declarada en su
repositorio de origen**. Está escrito en el `NOTICE` porque descubrirlo después de
publicar es peor que decirlo ahora.

Los modelos tienen las suyas, en
[`README_MODELS.md`](android/app/src/main/assets/models/README_MODELS.md).

---

<sub>cero-X · La Bandita · 2026 · Los números de este README se midieron; los que
no se pudieron medir están marcados como no medidos.</sub>
