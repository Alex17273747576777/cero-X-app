# Booru: cliente Safebooru real

Corte: 2026-11-13. Acceso: **Ajustes → Imágenes Safebooru**. No ocupa un sexto destino en la barra principal.

## Alcance comprobado

- Único origen integrado: `https://safebooru.org/` y su DAPI pública `index.php?page=dapi&s=post&q=index&json=1`.
- Búsqueda reciente o por una etiqueta; siempre se añade `rating:safe`. La API anónima limita etiquetas, por eso no se ofrece un editor de consultas que no pueda ejecutar.
- Páginas `pid`, límite de 1–50, estados de carga, HTTP/JSON error, cero resultados y resultados.
- Parser con respuesta máxima de 2 MiB. Descarta rating distinto de safe/s y URLs que no sean HTTPS en el host exacto `safebooru.org`.
- Miniaturas Coil en la propia pantalla. Tocar la miniatura abre el visor propio; «Ficha» abre la publicación y «Original» abre el archivo HTTPS en otra aplicación.
- Visor con zoom y descarga explícita (detalle abajo).
- Favoritos: hasta 100 metadatos JSON en `AlmacenExterno/booru/favoritos.json`, disponibles sin repetir búsqueda. No guarda binarios ni credenciales.
- Consulta y modo favoritos conservados en estado de vista; último resultado en el módulo para no repetir red al rotar.

## Visor con zoom y descarga explícita

Ruta: Ajustes → Imágenes Safebooru → tocar una miniatura → `FragmentHostActivity` destino `visor`.

- **Zoom real sin bibliotecas de terceros.** `VisorImagenView` traduce pellizco, doble toque y
  arrastre a `VisorTransformacion`, que es donde están las matemáticas y los límites: el mínimo es
  el ajuste a pantalla (o 1:1 si la imagen es más pequeña que la vista), el máximo es 8× el ajuste,
  y el arrastre queda limitado para que la imagen no salga de la vista dejando un hueco.
- **Botones y gestos son la misma operación.** «−», «+», «Ajustar» y «1:1» llaman a las mismas
  funciones que el dedo. Por eso el zoom se puede probar sin `MotionEvent` sintéticos y no hay un
  camino para el tacto y otro distinto para quien usa los botones.
- **Estado visible.** Una línea dice la escala (`1,25×`) y si está ajustada o ampliada; los botones
  se desactivan cuando la operación no tiene sentido (no se puede reducir por debajo del ajuste).
- **Dimensiones declaradas antes de que llegue la imagen.** La API da `1200×800`; con eso el visor
  ya ajusta. Si la carga falla, se dice el motivo y el zoom sigue sobre esas dimensiones. No se
  presenta una imagen en blanco como si fuera la real.
- **Rotación.** Se recupera la escala; el encuadre vuelve al centro porque el hueco cambia.
- **Descarga sólo con botón.** Escribe en `AlmacenExterno/booru/safebooru-<id>.<ext>` pasando por un
  `.tmp` que se renombra: una descarga cortada no deja un archivo que parezca bueno. Rechaza host
  ajeno, `rating` distinto de safe, HTTP de error, respuesta que no sea imagen, respuesta vacía y
  más de 25 MiB. Si no hay almacenamiento externo devuelve el motivo y **no escribe en el interno**;
  está declarado en `InventoryStorage`. Si el archivo ya estaba guardado con ese tamaño, no se
  vuelve a descargar.
- **Ficha y favorito** siguen disponibles dentro del visor.

## Lo que no incluye

No hay Rule34, Gelbooru, Danbooru, Konachan ni Yande.re. Una petición directa a `safebooru.donmai.us/posts.json` respondió HTTP 403 y no se integró. Tampoco hay contenido adulto, vídeo, MediaStore, credenciales ni ejecución de código del proyecto weekanya/Booru. La antigua descripción de esas capacidades fue sustituida por el alcance real.

El visor amplía imágenes: no reproduce WebM ni MP4, y no registra las descargas en MediaStore, así que aparecen en la carpeta de la app y no en la galería del sistema.


## Prueba real opt-in

```bash
source entorno/local.sh
cd android
CEROX_LIVE_BOORU=1 ./gradlew -I ../tools/low-memory.gradle \
  :app:testDebugUnitTest --tests 'com.ceroX.app.modules.booru.*RedRealTest'
```

Resultado `booru-red-01`: búsqueda `landscape`, dos resultados safe; miniatura JPEG HTTP 200, 10.045 bytes. Evidencia en `work/ci/booru-red-01.log` y `booru-red-01-resumen.txt`. Esto ejecuta red JSON+imagen en JVM; no demuestra el render de Coil ni interacción en Android físico.

Resultado `descarga-real-01` (2026-11-13): `DescargasBooruRedRealTest` descargó de verdad la publicación 6540805 desde `https://safebooru.org/images/816/27b6c56e46291fdcb43cc51daa0c6cbd.png`: **5.411.582 bytes**, archivo en disco, bytes contados iguales a los escritos, cabecera mágica PNG comprobada, ningún `.tmp` residual, y la segunda llamada devolvió `yaExistia=true` sin volver a bajar. Evidencia en `work/ci/descarga-real-01-resumen.txt`.

Esa descarga escribe en una carpeta temporal de la JVM, no en `AlmacenExterno` de Android: que el volumen externo de un aparato sea escribible de verdad sigue siendo del dispositivo.

## Pruebas locales

Consulta forzada safe, límite anónimo, descarte de adultos/hosts ajenos, diferencia entre vacío y HTTP 403, persistencia externa de favoritos y guarda de pantalla para búsqueda, imágenes, páginas, favoritos y acceso desde Ajustes.

Del visor (corte 2026-11-13, 30 pruebas nuevas):

- `VisorTransformacionTest` (13, JVM pura): ajuste y centrado, doble toque sobre el punto tocado y
  vuelta al ajuste, topes de pellizco, arrastre que no saca la imagen de la vista, imagen pequeña
  que conserva 1:1, recolocación al cambiar la vista, texto de escala sin depender del idioma,
  lectura de dimensiones y extensión limitada a imágenes.
- `DescargasBooruTest` (11, JVM pura sin red): guarda en el destino resuelto, rechaza host ajeno,
  HTTP de error, contenido que no es imagen, falta de almacenamiento externo y publicación no safe
  sin llamar a la red; corta por tope declarado y por tope durante la transferencia sin dejar
  archivo; respuesta vacía; conexión cortada sin archivo parcial; no vuelve a descargar lo que ya
  está; nombre con id y extensión segura.
- `VisorBooruAndroidTest` (6, Robolectric): el Intent lleva la publicación y la pinta, los botones
  amplían/reducen/ajustan con sus estados activados o no, sin argumentos no se inventa una imagen y
  todo queda desactivado, la imagen cargada reajusta con las dimensiones reales, el fallo de carga
  dice el motivo y conserva el zoom, y «Guardar» es explícito y dice dónde escribe.

Lo que estas pruebas no hacen: no lanzan `MotionEvent` sintéticos y no cargan una imagen por red; llaman a los mismos callbacks que Coil invoca. En Robolectric el `ImageLoader` lleva un interceptor que corta antes de tocar la red.

## Recorrido físico automatizado (escrito, sin ejecutar)

`android/app/src/androidTest/java/com/ceroX/app/visor/VisorFisicoAndroidTest.kt` convierte la revisión pendiente del aparato en cuatro pruebas de instrumentación: render de Coil y pellizco real, doble toque y arrastre sin huecos, descarga efectiva en `AlmacenExterno`, y contraste WCAG AA medido sobre el tema resuelto más etiquetas y tamaño táctil de los botones. Inyecta tactos con `sendPointerSync`, no los simula.

**Compilada y empaquetada en el APK androidTest; nunca ejecutada**, porque `adb devices` no muestra aparatos. Se lanza con `-e recorridoFisico 1 -e networkTests 1`; sin esas banderas se omite. Un detalle que la rompe si se toca: nada que espere o lea puede ir dentro de `ActivityScenario.onActivity`, porque ese bloque corre en el hilo principal y `waitForIdleSync`/`runOnMainSync` desde ahí se bloquean para siempre.
