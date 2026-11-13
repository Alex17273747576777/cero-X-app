# Auditoría del catálogo de apps · medida el 2026-11-13

El catálogo (`modules/catalog/CatalogModule.kt`, 52 fichas) tenía los metadatos
escritos a mano. Esta auditoría los contrastó con la fuente de verdad de cada
uno: la API del alojamiento donde vive el repositorio. No se corrigió nada por
parecido ni por suposición; donde no se pudo comprobar, se marca.

Repetir la comprobación:

```bash
source entorno/local.sh
cd android
CEROX_LIVE_CATALOGO=1 ./gradlew -I ../tools/low-memory.gradle \
  :app:testDebugUnitTest --tests com.ceroX.app.modules.catalog.CatalogoLicenciasRedRealTest
```

Sin `CEROX_LIVE_CATALOGO=1` la prueba se omite. No necesita credenciales: son
endpoints públicos con límite de peticiones por IP, por eso va de uno en uno.

## El resultado, en números

| qué | antes | después de la auditoría |
|---|---:|---:|
| fichas | 52 | 52 |
| licencias comprobadas contra la fuente | **0** | **18** |
| licencias que eran un marcador (`Open Source`, `Unknown`) | 21 | **0** |
| licencias explícitamente `SIN_VERIFICAR` | 0 | 34 |
| licencias **contradichas** por la fuente | — | **3** |
| enlaces muertos (HTTP 404) sin marcar | 4 | **0 sin marcar** |
| repositorios archivados sin marcar | 1 | **0 sin marcar** |
| tiendas separadas de las apps | 0 | **1** |
| directorios curados listados aparte | 0 | **3** |
| categorías de `healthCheck` que no existen en los datos | 2 (`audio`, `servers`) | **0** |
| categorías reales que `healthCheck` omitía | 3 (`server`, `tracking`, `ereader`) | **0** |
| variantes libres de plataforma | 16 textos | vocabulario cerrado de 12 |

## Los tres errores de licencia

No son matices de formato: son fichas que decían una licencia y cuya fuente dice
otra. Quien integrara código basándose en el catálogo habría partido de un
alcance legal equivocado.

| ficha | el catálogo decía | la fuente dice | por qué importa |
|---|---|---|---|
| `cloudstream` | Apache-2.0 | **GPL-3.0** | Apache es permisiva; GPL-3.0 es copyleft fuerte |
| `miru` | Apache-2.0 | **AGPL-3.0** | AGPL añade la cláusula de red: cambia lo que se puede cerrar |
| `gallery_dl` | Public Domain / Unlicense | **GPL-2.0** | de «haz lo que quieras» a copyleft: el caso más grave |

`gallery_dl` mereció una comprobación aparte porque su enlace apunta al espejo de
Codeberg y la API de Codeberg no detecta licencias. Se leyó el archivo
`LICENSE` directamente (18.092 bytes, *GNU GENERAL PUBLIC LICENSE Version 2*,
sin cláusula «or any later version», y ni rastro de texto Unlicense) y se
contrastó con el mismo proyecto en GitHub, cuya API responde `GPL-2.0`. Las dos
vías coinciden; lo que estaba mal era el catálogo.

Otras nueve fichas que decían `Open Source` —un marcador que parece un dato y no
lo es— pasaron a su identificador real: `animiru`, `curd` y `ani_cli` a GPL-3.0,
`suwayomi` a MPL-2.0, `unyo` a BSD-3-Clause, `toru` a MIT y `animetail` a
Apache-2.0. `kotatsu` decía `GPLv3`, que no es un identificador SPDX, y quedó en
`GPL-3.0`.

Cada corrección deja en `licenciaNota` lo que declaraba la fuente antes. Se
reescribe el dato, no se borra la pista del error.

## Los identificadores retirados por SPDX

`GPL-3.0`, `GPL-2.0` y `AGPL-3.0` son identificadores que SPDX retiró por
ambiguos: no distinguen «sólo esta versión» de «o cualquier versión posterior».
GitHub los sigue devolviendo así. **Se conservan literales.** Traducirlos a
`GPL-3.0-only` o `GPL-3.0-or-later` sería elegir un alcance legal que la API no
afirma, y eso es justo lo que esta auditoría no hace. La ficha lleva
`identificadorRetirado = true` para que se vea la ambigüedad en vez de esconderla.

## Los enlaces muertos

Cuatro fichas llevan a un repositorio que devuelve HTTP 404 en la API **y** en la
web:

| ficha | enlace | estado declarado |
|---|---|---|
| `tachiyomi` | github.com/tachiyomiorg/tachiyomi | ya estaba `DISCONTINUED` |
| `aniyomi` | github.com/AniyomiDev/Aniyomi | `ACTIVE` |
| `openlib` | github.com/Openlib-app/Openlib | `ACTIVE` |
| `tachiyomi_sy` | github.com/riderkick/TachiyomiSY | `ACTIVE` |

La interfaz los marca «enlace muerto (404)» y avisa al abrirlos. **No se les
cambió el estado a la app**: un repositorio que devuelve 404 puede haberse
mudado, y declarar descontinuada una app que no lo está sería inventar un dato.
Lo que se afirma es el hecho medido —ese enlace no responde— y nada más.

`kotatsu` sí está archivado según la API, y ahora se ve como «repo archivado».

## Lo que no se pudo comprobar, y por qué

| caso | fichas | qué se hace |
|---|---:|---|
| Sin archivo LICENSE | 1 (`dantotsu`) | `SIN_VERIFICAR` con el motivo |
| LICENSE que SPDX no reconoce (`NOASSERTION`) | 2 (`tachiyomi_az`, `dartotsu`) | `SIN_VERIFICAR` con el motivo |
| Enlace muerto | 4 | `SIN_VERIFICAR` + marca de 404 |
| El enlace es un sitio de producto, no código | 11 | `SIN_VERIFICAR`: no hay fuente que auditar |
| El enlace es documentación | 1 (`lanraragi`) | `SIN_VERIFICAR` |
| La ficha no trae enlace | 15 | `SIN_VERIFICAR` + `SIN_ENLACE` |
| **Total sin verificar** | **34** | |

Las 15 fichas sin enlace y las 12 con enlace que no es un repositorio siguen ahí:
se dice lo que son, no se les pega una licencia para que la lista quede bonita.

## Tiendas y directorios curados, separados

El pendiente pedía separar directorios curados y tiendas, y el catálogo no los
tenía separados de nada: era una lista plana de 52 filas.

**Tiendas.** Una ficha, `github_store`, se presenta a sí misma como *«Free,
open-source app store for GitHub releases»*. No es una app del ecosistema: es un
instalador de otras apps. Ahora va en `tiendas()` y la interfaz la lista bajo
«Tiendas e instaladores (aparte de las apps)». Se clasifica por una declaración
explícita con su motivo, no deducida del nombre: `github_store` ni siquiera trae
enlace, así que ninguna URL lo delata, y adivinarlo por el texto sería el falso
positivo que este catálogo no se permite.

**Directorios curados.** EverythingMoe, Miyomi y Wotaku son las fuentes de las que
salió el catálogo (ver `docs/CATALOGO-DE-RECURSOS.md`), no software. Van en
`directoriosCurados()`, en su propio bloque de la pantalla, con las entradas
medidas el 2026-11-08 (798, 149 y 156), qué publica cada uno y **su limitación**:
las 69 entradas que EverythingMoe oculta con JavaScript, las 89 fichas de Miyomi
con categoría vacía y las tablas mezcladas de Wotaku. Las tres URLs se
comprobaron vivas (HTTP 200) el 2026-11-13.

**Clasificación de enlaces.** `clasificarEnlace()` mira el host y la ruta, no el
nombre de la ficha, y devuelve `REPOSITORIO_FUENTE`, `TIENDA`,
`DIRECTORIO_CURADO`, `DOCUMENTACION`, `SITIO_PRODUCTO` o `SIN_ENLACE`. Una página
de releases cuenta como tienda aunque viva en github.com, porque reparte
binarios; `*.github.io` es un sitio, no un repositorio. Reparto actual de las 52
fichas: 25 repositorios, 11 sitios de producto, 1 documentación, 15 sin enlace y
0 tiendas. La acción de la interfaz se llama según eso: antes las 52 filas decían
«Abrir repositorio de …» y 27 no llevan a ninguno.

## Nada se descarga desde el catálogo

`decisionDeApertura()` decide antes de tocar nada, y es una función pura:

| enlace | decisión |
|---|---|
| `https://…` a una página | `ABRIR_NAVEGADOR` |
| `https://…/algo.apk`, `.aab`, `.xapk`, `.apkm` | `RECHAZADO_BINARIO_INSTALABLE` |
| `http://…` (sin TLS) | `RECHAZADO_NO_HTTPS` |
| vacío | `SIN_ENLACE` |

Abrir un `.apk` en el navegador provoca la descarga de un binario instalable:
sería una descarga implícita causada por la app. Se rechaza. Lo único que hace el
catálogo es `Intent.ACTION_VIEW` sobre una URL HTTPS, y en el fuente no hay
`DownloadManager`, `PackageInstaller`, `ACTION_INSTALL_PACKAGE` ni el MIME
`application/vnd.android.package-archive`; `CatalogoAuditoriaTest` lo vigila.

Abrir un repositorio tampoco instala ni verifica una release: enseña la página y
la decisión queda en el navegador y en la persona.

## Plataformas normalizadas

Los 52 textos libres («Android 8.0+», «Android, PC», «Cross-platform (Flutter)»,
16 variantes) se normalizan con `plataformasDe()` a un vocabulario cerrado de 12.
Dos reglas evitan afirmar más de lo que la fuente dijo:

- **Un anuncio no es soporte.** `ehviewer` declara «Android 8.0+, iOS coming
  soon» y sus plataformas son `{ANDROID}`: contar iOS ahí sería enseñar soporte
  que no existe.
- **Un framework no es una plataforma.** `jhentai` declara «Cross-platform
  (Flutter)», que no nombra ningún sistema, y acaba en `{SIN_DETERMINAR}` en vez
  de rellenarse con lo que Flutter suele implicar.

## Cómo se repite

| prueba | qué comprueba | red |
|---|---|---|
| `CatalogoAuditoriaTest` (13) | cifras de la auditoría, licencias sin marcadores, correcciones con su nota, muertos marcados, tiendas y directorios separados, categorías reales en `healthCheck`, plataformas sin soporte inventado, clasificación de enlaces, ausencia de cualquier ruta de descarga | no |
| `CatalogoFiltroTest` (2) | el filtro combinado, que ya existía | no |
| `CatalogoLicenciasRedRealTest` (1) | que la fuente **sigue** diciendo lo que grabó la auditoría, y que los muertos siguen muertos | sí, opt-in |

La prueba de red falla a propósito si una licencia cambia de SPDX o si un enlace
marcado muerto revive. No es un fallo del código: es el aviso de que hay que
volver a auditar, en vez de dejar el catálogo mintiendo por omisión.

Antes de gastar una sola petición consulta `api.github.com/rate_limit`, que no
cuenta contra el límite: sin credencial son 60 peticiones por IP y hora y repasar
la auditoría son 25. Si no quedan suficientes se omite entera diciendo cuántas
faltan, en vez de morir a mitad con un 403 y dejar la duda de qué se llegó a
comprobar.

## Lo que sigue sin estar

- **No se entró en las fichas individuales** de EverythingMoe ni en
  `/software/<slug>` de Wotaku: sigue sin haber versión, número de descargas ni
  fecha de última actualización por app.
- **Las categorías de 89 apps de Miyomi** siguen vacías en su API; no se rellenan.
- **Las 69 entradas ocultas de EverythingMoe** no se pueden tener sin ejecutar su
  JavaScript. Registradas con sus números exactos.
- **Ninguna licencia se verificó leyendo el texto completo del archivo**, salvo
  `gallery_dl`. Se usa el identificador que devuelve la API del alojamiento, que
  es lo que la fuente afirma; leer y opinar sobre el texto sería otra cosa.
- **El catálogo sigue siendo estático**: se actualiza editando el fuente y
  volviendo a auditar, no solo.

## Regla de almacenamiento

El catálogo no escribe nada: ni ficheros, ni preferencias, ni base de datos. Son
datos en memoria construidos desde el código, y los JSON de las fuentes viven en
`data/` del repositorio. Ver `docs/REGLA-DE-ALMACENAMIENTO.md`.

---

## Tabla completa de los 25 repositorios auditados

| ficha | enlace | HTTP | licencia que decía | licencia comprobada | SPDX de la API |
|---|---|---:|---|---|---|
| `ani_cli` | github.com/pystardust/ani-cli | 200 | Open Source | `GPL-3.0` | `GPL-3.0` |
| `animetail` | github.com/Animetailapp/Animetail | 200 | Open Source | `Apache-2.0` | `Apache-2.0` |
| `animiru` | github.com/quickdesh/Animiru | 200 | Open Source | `GPL-3.0` | `GPL-3.0` |
| `aniyomi` | github.com/AniyomiDev/Aniyomi | 404 | Apache-2.0 | **no verificable**: enlace muerto | — |
| `cloudstream` | github.com/recloudstream/cloudstream | 200 | Apache-2.0 | `GPL-3.0` | `GPL-3.0` |
| `curd` | github.com/Wraient/curd | 200 | Open Source | `GPL-3.0` | `GPL-3.0` |
| `dantotsu` | github.com/itsmechinmoy/dantotsu-updater | 200 | Open Source | **SIN_VERIFICAR**: sin archivo LICENSE | ninguna |
| `dartotsu` | github.com/aayush2622/Dartotsu | 200 | Open Source | **SIN_VERIFICAR**: LICENSE no reconocible | NOASSERTION |
| `gallery_dl` | codeberg.org/mikf/gallery-dl/ | 200 | Public Domain / Unlicense | `GPL-2.0` | `GPL-2.0` |
| `houbi` | github.com/PineappleTwilight/houri | 200 | Apache-2.0 | `Apache-2.0` | `Apache-2.0` |
| `kisara` | github.com/ifritraen/kisara | 200 | Apache-2.0 | `Apache-2.0` | `Apache-2.0` |
| `komikku` | github.com/komikku-app/komikku | 200 | Apache-2.0 | `Apache-2.0` | `Apache-2.0` |
| `kotatsu` | github.com/KotatsuApp/Kotatsu | 200 | GPLv3 | `GPL-3.0` **(archivado)** | `GPL-3.0` |
| `mangayomi` | github.com/kodjodevf/mangayomi | 200 | Apache-2.0 | `Apache-2.0` | `Apache-2.0` |
| `mihon` | github.com/mihonapp/mihon | 200 | Apache-2.0 | `Apache-2.0` | `Apache-2.0` |
| `miru` | github.com/miru-project/miru-app | 200 | Apache-2.0 | `AGPL-3.0` | `AGPL-3.0` |
| `openlib` | github.com/Openlib-app/Openlib | 404 | Apache-2.0 | **no verificable**: enlace muerto | — |
| `reikai` | github.com/unseensnick/Reikai | 200 | Apache-2.0 | `Apache-2.0` | `Apache-2.0` |
| `suwayomi` | github.com/Suwayomi/Suwayomi-Server | 200 | Open Source | `MPL-2.0` | `MPL-2.0` |
| `tachiyomi` | github.com/tachiyomiorg/tachiyomi | 404 | Apache-2.0 | **no verificable**: enlace muerto | — |
| `tachiyomi_az` | github.com/az4521/TachiyomiAZ | 200 | Apache-2.0 | **SIN_VERIFICAR**: LICENSE no reconocible | NOASSERTION |
| `tachiyomi_sy` | github.com/riderkick/TachiyomiSY | 404 | Apache-2.0 | **no verificable**: enlace muerto | — |
| `toru` | github.com/sweetbbak/toru | 200 | Open Source | `MIT` | `MIT` |
| `unyo` | github.com/K3vinb5/unyo-app | 200 | Open Source | `BSD-3-Clause` | `BSD-3-Clause` |
| `yokai` | github.com/null2264/yokai | 200 | Apache-2.0 | `Apache-2.0` | `Apache-2.0` |

Los otros 27 enlaces (11 sitios de producto, 1 documentación, 15 fichas sin
enlace) no tienen repositorio que consultar y aparecen como `SIN_VERIFICAR` con
su motivo.
