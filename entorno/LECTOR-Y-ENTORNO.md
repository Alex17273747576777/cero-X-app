# Leer de verdad, y el entorno que lo comprueba

Fecha: 2026-11-08. Este documento dice qué se construyó en esta tanda, qué se
midió, y qué sigue sin poder afirmarse.

---

## 1. El lector, que era el hueco número uno

Existían `ReaderPipeline`, `PageCache`, `ReaderPrefetcher` y el contrato de
fuentes `eu.kanade.tachiyomi.source` completo… y **ninguna pantalla**. La app no
podía abrir un capítulo. Eso bloqueaba once issues de terceros.

Ahora hay cuatro piezas nuevas y una que se conectó:

| pieza | archivo | qué hace |
|---|---|---|
| catálogo → capítulos → lector | `ui/catalogo/CatalogoActivity.kt` | busca, elige obra, elige capítulo |
| pantalla de lectura | `ui/reader/ReaderActivity.kt` | pinta páginas con caché y prefetch |
| de URL a píxeles | `ui/reader/PageLoader.kt` | descarga, mide, decodifica con muestreo |
| estado de navegación | `ui/reader/ReaderState.kt` | LTR / RTL / webtoon, dobles, progreso |
| marcapáginas | `ui/reader/ReadingProgress.kt` | guarda con `commit()`, no con `apply()` |
| orquestador de fuentes | `sources/ContentRepository.kt` | una fuente caída no tumba la lista |
| fuente que no depende de nadie | `sources/LocalFolderSource.kt` | lee una carpeta: funciona en modo avión |

### Dos fallos que encontraron las pruebas

**1. Las páginas dobles estaban encuadernadas al revés.** La versión que escribí
emparejaba (0,1), (2,3)… y dejaba la última suelta. Un manga va: portada sola,
luego (1,2), (3,4). La prueba falló con `expected:<[0]> but was:<[0, 1]>` y se
corrigió el código, no la prueba.

**2. `epub` en dos tipos a la vez** (esto venía de la tanda anterior y sigue
arreglado): ahora hay un mapa `AMBIGUOUS` explícito y una prueba que falla si
alguna extensión vuelve a estar en dos tipos.

### Lo que NO se ha probado del lector

No se ha abierto un capítulo en un teléfono. Lo que sí está probado: el orden
natural de archivos (2 antes que 10), que un `.nfo` no es una página, que una
subcarpeta vacía no es un capítulo, el sentido de las flechas en RTL, que un
gesto horizontal en webtoon no hace nada, y el marcapáginas.

---

## 2. manga-ocr: ahora lee, y esto está medido

`ON_DEVICE.available = false` en traducción sigue siendo verdad: **leer no es
traducir**. Pero el OCR en sí ya no es una promesa. Se ejecutó el par de modelos
empaquetado con ONNX Runtime en CPU y se comparó la salida con el texto dibujado:

| texto dibujado | lo que devolvió el modelo | |
|---|---|---|
| こんにちは | こんにちは | ✔ |
| ありがとう | ありがとう | ✔ |
| まんが | まんが | ✔ |
| テスト | テスト | ✔ |
| おはようございます | おはようございます | ✔ |

**5 de 5 exactos.** Tiempos medidos: encoder ~350 ms, decoder ~5-7 ms por token
(30-60 ms por línea corta), en CPU de escritorio.

### Las dos trampas que costaron tiempo

1. **La primera medición usó una fuente sin glifos japoneses** (DejaVuSans) y
   devolvió «それは、» para cinco entradas distintas. No era el OCR: era la
   prueba. Cualquier «funciona» basado en eso habría sido falso.
2. **El token de inicio es 2, no 0.** `generation_config.json` dice
   `decoder_start_token_id: 2`, que en el vocabulario de 6 144 entradas es
   `[CLS]`. Empezar por 0 (`[PAD]`) produce basura sin dar ningún error.

El motor (`modules/kisara/MangaOcrEngine.kt`) lee **un recorte**, no una página
entera: la detección de balones es otro paso y no se finge que exista.

---

## 3. Trackers: AniList y MyAnimeList, con una regla que importa

`UniversalTracker.syncProgress` devolvía `success = true` **sin hacer ninguna
petición**. Ahora devuelve `false` con el motivo, y los trackers que funcionan de
verdad están en `modules/houri/tracker/`:

- `TrackerModel.kt`: las reglas. **Nunca se retrocede el progreso**: si tu cuenta
  tiene el capítulo 40 y la app quiere subir el 12, no se envía nada. `COMPLETED`
  exige que el progreso sea el total; sin total conocido no se marca terminado.
- `TrackerClient.kt`: GraphQL de AniList y REST v2 de MAL. **AniList devuelve
  HTTP 200 con `errors`** cuando la mutación falla; mirar solo el código daría
  «OK» en un fallo.
- `TrackerCredentials.kt`: el token va en `EncryptedSharedPreferences`. Si el
  dispositivo no tiene Keystore, **no** se cae a preferencias en claro: no se
  guarda y se dice.
- `TrackerActivity.kt`: pegar el token, poner el id de la obra y el capítulo.
  El id no se busca por título a propósito: coger el primer resultado es cómo se
  acaba con el progreso del manga equivocado.

Lo que se manda: id, capítulo, total, estado. **Nada más.** Hay una prueba por
reflexión que falla si alguien añade un campo de título, fuente o URL.

---

## 4. AxManagerD y OmniShizuku: conectados de verdad

Lo que antes era `UNVERIFIED_NAMES` ahora tiene protocolo comprobado leyendo sus
AIDL, no sus descripciones:

| gestor | repo | hallazgo |
|---|---|---|
| **OmniShizuku** | krishna3163/OmniShizuku | su AIDL vive en `moe/shizuku/server`, así que **habla el protocolo de Shizuku**: mismo paquete, cero código nuevo |
| **AxManagerD** | bufanchen121101/AxManagerD | implementa Shizuku **y** Dhizuku; su `applicationId` no está publicado de forma legible, así que **no se inventa**: se detecta por binder |

Eso es mejor que una lista de nombres: una lista envejece el día que alguien
publica un fork; un protocolo no.

### La ruta fácil no existía

`Shizuku.newProcess(...)` es **privado** en `dev.rikka.shizuku:api 13.1.5`:

```
e: Cannot access 'static fun newProcess(...)': it is private in 'rikka/shizuku/Shizuku'
```

Así que se fue por la ruta real: cuatro interfaces AIDL copiadas con sus números
de transacción (`IShizukuService.newProcess = 7`), y el proceso remoto leído por
`ParcelFileDescriptor`. Eso está en `shell/ShizukuShell.kt` y `main/aidl/`.

### Y un falso positivo que encontró la prueba

La sonda de root lanzaba `su -c id` y veía `uid=0` → declaraba ROOT. En el
contenedor donde corren las pruebas hay un `/usr/bin/su` de Linux que **sí**
concede root, así que la app habría ofrecido instalación silenciosa en un
teléfono sin root y habría fallado delante del usuario.

Corrección: solo se aceptan los binarios `su` **de Android** (`/system/bin/su`,
`/sbin/su`, `/debug_ramdisk/su`, `/data/adb/ksu/bin/su`, `/data/adb/ap/bin/su`…)
y se ejecuta esa ruta concreta, no la del `PATH`. Sin ninguno de esos archivos no
hay root y no se ejecuta nada.

---

## 5. La VM: arranca, no termina de arrancar

Instalado y lanzado **de verdad**, sin `/dev/kvm`:

- `sdkmanager` → emulator 37.2.13 + `system-images;android-30;aosp_atd;x86_64`
- AVD `cerox` en `/opt/vm/avd`, `-accel off` (QEMU con TCG, CPU emulada por software)
- el emulador abre el puerto 5554 y **`adb devices` lo ve como `device`**

Y ahí se queda. Dos intentos, los dos con el mismo final:

| intento | configuración | resultado |
|---|---|---|
| 1 | `-cores 2 -gpu swiftshader_indirect -memory 1024` | ~6 min, `detected a hanging thread 'QEMU2 CPU1 thread'` → **Segmentation fault** |
| 2 | `-cores 1 -gpu off -memory 900 -wipe-data` | mismo aviso en `QEMU2 main loop` → **Segmentation fault** |

En los dos, `getprop sys.boot_completed` estuvo **vacío más de 8 minutos**. No se
completó el arranque.

El límite real, con números: **1 982 MiB de RAM** en total, TCG emulando x86_64
en 2 vCPU, y el propio emulador avisando desde el primer segundo:
`x86_64 emulation may not work without hardware acceleration`.

### La ruta que sí cabe, y queda preparada

El APK universal pesa 475 MB y no se puede instalar aquí. Así que se añadieron
dos propiedades al build:

```bash
gradle :app:assembleDebug -PsinModelos -PsoloAbi=x86_64
```

- `-PsoloAbi=x86_64` → una sola arquitectura
- `-PsinModelos` → deja fuera los 378 MiB de `.onnx` (no hacen falta para abrir
  un capítulo de una carpeta local)

Resultado: **41 829 694 B** en vez de 475 MB. Ese sí cabría.

### Lo que se verificó del APK sin dispositivo

No es lo mismo que ejecutarlo, pero tampoco es nada:

- `aapt2 dump badging`: `com.ceroX.app`, minSdk 26, targetSdk 36
- las cinco actividades están declaradas en el manifiesto, incluidas
  `CatalogoActivity` y `ReaderActivity`
- las 12 clases clave están **dentro del .dex**: lector, catálogo, fuente local,
  motor OCR, tracker, `ShizukuShell` y los stubs `moe.shizuku.server.IShizukuService$Stub`
  y `rikka.shizuku.Shizuku`
- firmado con el certificado de depuración, 19 dex, 64,8 MiB de bytecode

`scripts/arrancar-vm.sh` deja todo listo para una máquina con KVM.

## 6. Lo que sigue sin poder afirmarse

- **Traducción local**: sigue sin existir. `ON_DEVICE.available = false`, con el
  motivo escrito. Lo que sí hay es `LocalTranslator`, que cambia la variante
  (dominicano, rioplatense) y aplica glosario; eso funciona y tiene 17 pruebas.
- **Dhizuku**: detectado por paquete, pero cero-X no trae su AIDL (no está en
  Maven Central), así que no puede hablar con su servicio. Está escrito en el
  código en vez de disimulado.
- **Inpainting (LaMa)**: no existe. `inpaintBubbles` devuelve `success = false`.
- **Gobernadores de CPU y Hz del panel**: no se tocan. No es una limitación
  técnica, es una decisión: eso cambia el teléfono entero y cero-X solo ajusta lo
  suyo.

---

## 7. Números

- **311 pruebas unitarias, 0 fallos** (leídas del XML de JUnit, no del log).
  Ese número está verificado con la última ejecución de esta máquina.
- De ellas, **106 nuevas** en esta tanda: 16 estado del lector, 17 traducción
  local, 16 planificador de trackers, 19 gestores y privilegios, 10 motor OCR,
  14 carpeta local, 14 orquestador de fuentes.
- Un falso positivo de root corregido, una encuadernación invertida corregida,
  una ruta de API inexistente descubierta y sustituida por la real.

---

## 6. Cómo se probó el lector sin un teléfono (y qué no se probó)

El README decía «no lee manga de punta a punta: falta pantalla de lector». La
pantalla **ya existía** en el árbol (`ReaderActivity`, 416 líneas, con `LectorScreen`,
`ReaderState`, `PageLoader`, `PageCache`, `ReadingProgress`) y lo que faltaba era
una prueba que recorriera la cadena. Se escribió, y en el camino aparecieron
cuatro cosas:

1. **La descarga no se podía probar** porque vivía en un método privado de
   `PageLoader`, junto a `BitmapFactory`. Se extrajo a
   `modules/reader/DescargadorDeBytes.kt` — sin relajar ninguna regla — y ahora se
   apunta a un servidor HTTP real.
2. **`com.sun.net.httpserver` no está en `android.jar`**, así que la primera
   versión de la prueba no compilaba. Se hizo un `ServerSocket` mínimo que sirve
   HTTP/1.1 de verdad, con 302, 404, bucle de redirecciones y un caso **sin**
   `Content-Length`.
3. **Los nombres con acento llegaban como `Cap?tulo`**: la JVM arranca con
   `LC_CTYPE=POSIX` en este contenedor y decodifica los nombres de archivo como
   ASCII. No era un fallo del lector, era el entorno. Se fijan
   `file.encoding`/`sun.jnu.encoding`/`LC_ALL` en `app/build.gradle` para que la
   prueba mida el lector y no la máquina.
4. **`ReaderState.desdePaginas`** no existía y hacía falta: con cero páginas el
   `coerceIn(0, -1)` revienta y con un marcapáginas mayor que el capítulo hay que
   abrir en la última página sin romper. Ahora es un método probado.

`LectorExtremoAExtremoTest` = **14 pruebas**: carpeta temporal → fuente local →
`RepositorioGlobal` → `ContentRepository.detalle` → `.paginas` → bytes por
`DescargadorDeBytes` → `ReaderState`. Lo que **no** cubre: `BitmapFactory` (en la
JVM devuelve `null`) y el dibujado en pantalla. Eso necesita un dispositivo, y
sigue sin haberlo.

## 7. El núcleo compartido: escritorio y web, con artefacto ejecutado

Módulo `comun/` (Kotlin Multiplatform, targets JVM + JS(IR) + iOS declarado):

| artefacto | tamaño | cómo se comprobó |
|---|---:|---|
| `build/libs/cerox-comun-todo.jar` | 3,29 MB | `java -jar … estado / ordenar / tracker / extension` |
| `build/dist/js/productionLibrary/cerox-comun.js` | 39 KB | `node scripts/probar-web.mjs` → 15/15 |
| `jvmTest` | — | 41 pruebas, 0 fallos |
| `jsNodeTest` | — | 33 pruebas, 0 fallos |

Tres defectos reales que solo aparecieron **al ejecutar**:

- `ReferenceError: 'ceroX' is not defined`: el sobre termina llamando a cinco
  objetos que en `qjs` pelado no existen. Se escribió `AnfitrionJs`, que los
  define (`log`, `prefs`, `dom`, `ceroX`) y deja `http` **cerrado a propósito**
  con un motivo, porque la red la pone cada anfitrión.
- `ReferenceError: 'exports' is not defined`: el sobre definía `module.exports`
  pero no `exports`, y todas las extensiones de Mihon usan `exports.sources`.
- El sobre salía con sangría: `trimIndent()` **no recorta** cuando una de las
  líneas interpoladas tiene menos sangría. Se construye con `append`.

Y uno de arnés, no de producto: `var SALIDA = var ceroX = …` era un `SyntaxError`
propio de la prueba nueva. Se separó en `AnfitrionJs.scriptConSalida`.

iOS: los tres targets están declarados y Gradle los **desactiva** en Linux con un
aviso explícito. No hay framework y no se afirma que lo haya.
