# Booru: cliente Safebooru real

Corte: 2026-11-12. Acceso: **Ajustes → Imágenes Safebooru**. No ocupa un sexto destino en la barra principal.

## Alcance comprobado

- Único origen integrado: `https://safebooru.org/` y su DAPI pública `index.php?page=dapi&s=post&q=index&json=1`.
- Búsqueda reciente o por una etiqueta; siempre se añade `rating:safe`. La API anónima limita etiquetas, por eso no se ofrece un editor de consultas que no pueda ejecutar.
- Páginas `pid`, límite de 1–50, estados de carga, HTTP/JSON error, cero resultados y resultados.
- Parser con respuesta máxima de 2 MiB. Descarta rating distinto de safe/s y URLs que no sean HTTPS en el host exacto `safebooru.org`.
- Miniaturas Coil en la propia pantalla. «Ficha» abre la publicación; «Original» abre el archivo HTTPS.
- Favoritos: hasta 100 metadatos JSON en `AlmacenExterno/booru/favoritos.json`, disponibles sin repetir búsqueda. No guarda binarios ni credenciales.
- Consulta y modo favoritos conservados en estado de vista; último resultado en el módulo para no repetir red al rotar.

## Lo que no incluye

No hay Rule34, Gelbooru, Danbooru, Konachan ni Yande.re. Una petición directa a `safebooru.donmai.us/posts.json` respondió HTTP 403 y no se integró. Tampoco hay contenido adulto, zoom, vídeo, MediaStore, descargas, credenciales ni ejecución de código del proyecto weekanya/Booru. La antigua descripción de esas capacidades fue sustituida por el alcance real.

## Prueba real opt-in

```bash
source entorno/local.sh
cd android
CEROX_LIVE_BOORU=1 ./gradlew -I ../tools/low-memory.gradle \
  :app:testDebugUnitTest --tests com.ceroX.app.modules.booru.SafebooruRedRealTest
```

Resultado `booru-red-01`: búsqueda `landscape`, dos resultados safe; miniatura JPEG HTTP 200, 10.045 bytes. Evidencia en `work/ci/booru-red-01.log` y `booru-red-01-resumen.txt`. Esto ejecuta red JSON+imagen en JVM; no demuestra el render de Coil ni interacción en Android físico. Esa revisión sigue pendiente junto al LG V60.

## Pruebas locales

Consulta forzada safe, límite anónimo, descarte de adultos/hosts ajenos, diferencia entre vacío y HTTP 403, persistencia externa de favoritos y guarda de pantalla para búsqueda, imágenes, páginas, favoritos y acceso desde Ajustes.
