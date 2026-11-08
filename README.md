# cero-X

### Un lector de manga, novelas y anime que trae la IA dentro del APK.

`Android 8.0 – 16` · `arm64 · armv7 · x86 · x86_64` · `453 MiB con 5 modelos` · `Apache-2.0`

---

Esto no es una promesa de lo que cero-X llegará a ser. Es lo que hace hoy,
medido, con lo que todavía no hace escrito al lado sin adornos. Si algo de aquí
no se puede comprobar con un comando, no está en este README.

**[Descargar la beta](../../releases/tag/v1.0.0-beta)** · **[Cómo se construye](entorno/COMPILAR-Y-FIRMAR.md)** · **[Qué dicen los modelos](android/app/src/main/assets/models/README_MODELS.md)**

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
| 🎨 | **Colorización de páginas** con modelo ONNX dentro del APK | funciona (falta pantalla) |
| 🔍 | **Reescalado ×4** con dos modelos, elegidos según la página | funciona (falta pantalla) |
| 📖 | **Caché de páginas** con tope en bytes y prefetch adaptado a la red | funciona |
| 🔌 | **Extensiones JVM** del ecosistema Mihon, cargadas en aislamiento | funciona |
| 💾 | **Importador de backups** de 6 formatos, detectados por bytes | funciona |

«Funciona (falta pantalla)» merece una explicación, porque es fácil decirlo y
difícil que sea verdad. El modelo carga, ejecuta inferencia —medido— **y existe
un pipeline que recibe la página, aplica el motor y devuelve el bitmap listo para
pintar** (`ReaderPipeline`, 6 pruebas). Lo que todavía no hay es la pantalla de
lector que llame a ese pipeline: cero-X no tiene aún una vista de lectura.

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

El ecosistema tiene **tres** formas incompatibles de escribir una extensión. La
mayoría de las apps cubre una y lo llama compatibilidad.

| capa | formato | quién lo usa | cero-X |
|---|---|---|---|
| **1 · JVM** | APK con `eu.kanade.tachiyomi.source.SourceFactory` | Mihon, Aniyomi, Komikku, Neko, Kotatsu, houri, Kototoro | ✅ `modules/source_api` |
| **2 · JavaScript** | archivo JS evaluado en un motor embebido | Mangayomi, AnymeX, Dartotsu | ⚠️ `modules/source_js` — falta elegir motor |
| **3 · datos** | backups `.tachibk` / `.proto.gz` / JSON | todos | ✅ `modules/backup_import` |

La capa 1 carga el APK con `DexClassLoader` aislado, descubre la fábrica por
`META-INF/services/` y verifica la firma antes de ejecutar nada.

La capa 3 detecta el formato **por los primeros bytes, no por la extensión**:
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

**Nada de esto es código copiado.** Todos son Apache-2.0 o GPL-3.0; varios se
podrían portar legalmente, pero arrastran su base de datos y su paquete
`eu.kanade`. Se tomó la idea y se reescribió con las piezas que cero-X ya tiene.
Cuando una idea vino de un GPL-3.0 (DropSauce, Miyorare) se tomó **solo el
concepto**, nunca una línea: cero-X es Apache-2.0 y así se queda.

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

Esta sección existe porque un README sin ella es publicidad.

- ❌ **No está probado que arranque en un aparato físico.** La máquina donde se
  construyó no tiene `/dev/kvm` ni RAM para un emulador. Lo verificado es
  estático: actividad de arranque declarada, tema presente, 26 899 clases en 3
  dex, icono en 6 variantes, firma v2 válida.
- ❌ **No lee manga de punta a punta todavía.** El pipeline de IA está hecho y
  probado; falta la pantalla de lector que lo invoque. cero-X no tiene vista de
  lectura, así que no hay nada que abrir.
- ❌ **No sincroniza con AniList ni MyAnimeList** contra servidores reales. Está
  el código y faltan las credenciales.
- ⚠️ **Las extensiones JavaScript no tienen motor elegido.** Hay tres candidatos
  viables: QuickJS 0.9.2 (MIT), J2V8 6.3.4 y Rhino 1.9.2. La elección tiene
  consecuencias de tamaño y licencia y no se ha tomado a la ligera.
- ❌ **No hay versión de escritorio ni iOS.** Están en el código como módulos; no
  hay binarios y no se anuncia lo que no se puede instalar.

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
| pruebas unitarias | **40, todas pasando** |
| errores de compilación | **0** (venían de 519) |
| tamaños de backup detectados | 6 de 6 |

```bash
# verificable en cualquier clon
$ ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest
40 tests completed, 0 failed
# FeedManager 6 · SuggestionEngine 6 · PageCache 7 · ReaderPipeline 6
# ModelHonesty 5 · StressBackup 5 · StressFeed 5

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

Apache-2.0, igual que el linaje del que sale cero-X. Verificado por la API de
GitHub el 2026-11-08: `mihonapp/mihon`, `komikku-app/komikku` y
`PineappleTwilight/houri` son los tres Apache-2.0.

Apache-2.0 **no** obliga a publicar el código modificado —eso es la GPL— pero sí
obliga a conservar el aviso y el `NOTICE`. Por eso existe
[`NOTICE`](NOTICE), con la cadena completa y las licencias de los cinco modelos.

Un aviso que no es cosmético: **`anime4x.onnx` no tiene licencia declarada en su
repositorio de origen**. Está escrito en el `NOTICE` porque descubrirlo después de
publicar es peor que decirlo ahora.

Los modelos tienen las suyas, en
[`README_MODELS.md`](android/app/src/main/assets/models/README_MODELS.md).

---

<sub>cero-X · La Bandita · 2026 · Los números de este README se midieron; los que
no se pudieron medir están marcados como no medidos.</sub>
