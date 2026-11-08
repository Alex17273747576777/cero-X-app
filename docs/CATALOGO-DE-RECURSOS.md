# Catálogo de recursos del ecosistema · medido el 8 de noviembre de 2026

Las tres fuentes se pidieron **en paralelo** y cada una se parseó con su propio
extractor, porque publican cosas distintas. El script es
`scripts/catalogo-recursos.py` y los datos, `data/catalogo-*.json`.

```bash
python3 scripts/catalogo-recursos.py --json data
```

## El número

| fuente | qué se extrajo | entradas |
|---|---|---:|
| **EverythingMoe** | 25 secciones de la portada + las 24 páginas `/section/<id>` + la vista `/simple` | **798** |
| **Miyomi** | `/api/apps`, `/api/extensions`, `/api/guides` | **149 + 47 + 24** |
| **Wotaku** | tablas de `/software`, `/websites`, `/misc` | **156** |
| | **total** | **1174** |

Todas las descargas fueron `HTTP 200`; el informe las lista una por una con sus
bytes. Ninguna fila se ha inventado para «completar» una sección.

## Lo que cada fuente da, y lo que no

### EverythingMoe — el más completo, y el que más esconde

Cada ficha tiene nombre, ficha propia (`/s/<slug>`), icono, sección y etiquetas
(`HUB`, `MULT`, `NEW`, `LOGIN`, `RE`…). Es el catálogo más útil para publicar
porque trae **las secciones del ecosistema ya separadas**:

| sección | en el HTML | declara el sitio |
|---|---:|---:|
| Anime Streaming | 106 | 106 |
| Manga Reading | 94 | 94 |
| Apps | 90 | 90 |
| Manhwa Reading | 42 | **73** |
| Novel Reading | 60 | 60 |
| Download | 50 | **52** |
| Database / Tracker | 45 | 45 |
| Misc Tools | 33 | 33 |
| Music | 31 | 31 |
| Wiki / Guides | 28 | 28 |
| Games | 23 | **41** |
| Asian Drama | 22 | 22 |
| Forums / Community | 22 | **23** |
| Art / Imageboards | 21 | **37** |
| VTuber · Gacha Games | 18 · 18 | 18 · 18 |
| Donghua Streaming | 17 | 17 |
| Release Schedule | 15 | 15 |
| Quiz | 15 | 15 |
| Western Streaming | 12 | 12 |
| Cosplay | 10 | 10 |
| Rank / Trends | 8 | 8 |
| AMV | 6 | **7** |
| General Tools | 3 | 3 |

**Las seis secciones recortadas y por qué** (esto es el 100 % de lo que se puede
tener sin inventar):

- `Manhwa Reading`: faltan **31**, números exactos
  `8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 24, 32, 33, 34, 35, 44, 48, 49, 50,
  51, 52, 53, 54, 55, 56, 57, 58, 71, 72, 73`.
- `Games`: faltan **18**: `4–12, 16–20, 26, 27, 33, 34`.
- `Art / Imageboards`: faltan **16**: `1, 2, 3, 9, 10, 12, 13, 20–28`.
- `Download`: faltan 2 (`16, 36`). `AMV`: falta 1 (`5`).
  `Forums / Community`: falta 1 (`7`).

No están en ninguna otra vista: `/simple`, `?view=all`, `?all=1` y `?page=2`
devuelven exactamente el mismo HTML, y en la página de Manhwa hay **42 botones
`morebtn`** para 73 entradas, es decir, el sitio las oculta por JavaScript y no
las manda. Se registra el hueco con sus números exactos en vez de rellenarlo.

### Miyomi — API estructurada, 36 campos por app

No hizo falta scraping: `/api/apps` devuelve JSON con `id`, `slug`, `name`,
`description`, `shortDescription`, `author`, `category`, `status`, `version`,
`platforms`, `contentTypes`, `tags`, `compatibleWith`, `iconUrl`, `downloadUrl`,
`repoUrl`, `websiteUrl`, `discordUrl`, `upstreamUrl`, **`forkOf`**,
`lastReleaseDate`, `downloadCount`, `likesCount`, `moderatorNote`…

- 40 de las 149 apps declaran de qué fork salen (`forkOf`) — dato que ninguna de
  las otras dos fuentes da.
- Plataformas: Android 109, Windows 52, Linux 40, iOS 27, macOS 21, Web 9.
- Categoría vacía o nula en 89 fichas: **no se rellena**.
- Además: **47 extensiones** y **24 guías** con sus URLs.
- Endpoints que probé y **no existen** (404, no se inventan): `/api/repos`,
  `/api/repositories`, `/api/lists`, `/api/tracking`, `/api/sources`,
  `/api/plugins`, `/api/tools`, `/api/softwares`.

### Wotaku — poco y con estructura floja

156 filas de tablas, en 3 páginas (`software` 65, `misc` 53, `websites` 38), con
las columnas del sitio (`App`, `Source`, `Tracker`, `Sub`, `Platform`). Es la
fuente **menos** aprovechable de las tres para un catálogo: sus tablas mezclan
apps, servidores y enlaces sueltos, y muchas celdas están vacías. Se registra con
su `seccion` y su `columna` para no confundir un tracker con un lector.

## Lo que falta y cómo se cerraría

| hueco | por qué | cómo se cierra |
|---|---|---|
| 69 entradas de EverythingMoe | el sitio no las manda en el HTML | nada legal ni fiable: habría que ejecutar su JavaScript. Se queda registrado. |
| Categorías de 89 apps de Miyomi | la API las devuelve vacías | pedirlas ficha a ficha (149 peticiones) o dejarlo |
| `/api/repos` de Miyomi | no existe | — |
| Fichas individuales | no se ha entrado en `everythingmoe.com/s/<slug>` ni en `/software/<slug>` | siguiente paso, en paralelo, filtrando por lo que aporte (versión, descargas, última actualización) |

## Para qué sirve esto en la app

- `data/catalogo-miyomi.json` es el que tiene forma de catálogo de apps: se puede
  convertir en un feed importable sin transformar nada.
- Las 798 fichas de EverythingMoe dan las **secciones del ecosistema** ya
  separadas, que es lo que necesita el catálogo de cero-X para agrupar fuentes.
- Wotaku sirve para documentos, no para datos: es donde están las guías.

## Regla respetada

Nada de esto se escribe en el almacenamiento interno de la app. Los JSON van al
repositorio (`data/`), y si alguna vez se empaquetan, van como asset del APK —
que es solo lectura. Ver `docs/REGLA-DE-ALMACENAMIENTO.md`.
