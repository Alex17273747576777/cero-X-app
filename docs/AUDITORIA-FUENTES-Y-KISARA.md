# Auditoría de Kisara, Project Nox, Kotatsu y directorios

Fecha de la instantánea: 10 de noviembre de 2026. Los clones viven fuera del APK, en `work/referencias/`. Ningún JAR, APK o script de terceros se ejecuta automáticamente.

## Decisiones y procedencia

| Proyecto | Commit inspeccionado | Licencia/estado comprobado | Decisión |
|---|---|---|---|
| `ifritraen/kisara` | `d121a8d6bf931adc34b5d32fbe5fd22c4db922af` | Apache-2.0; el README declara WIP/no listo para uso público; CI del commit fallida. Releases como `build-r10637` son prereleases. | No copiar `main` completo ni llamarlo estable. Se implementaron localmente búsqueda de biblioteca, favoritos y fusión. El cargador Kotatsu upstream sí exige modelos y `MangaLoaderContext`; no basta un `DexClassLoader`. |
| `Awerkori/manga-extensoes` | `55b9127ee947795cb76944e3e55f019066c72cba` | Contribuciones del catálogo AGPL-3.0-only; componentes heredados conservan sus licencias, incluida Apache-2.0. Código fuente hermano `781b32ad629601bcec2bdc78c01205926c85e909`. | Se consumen datos del índice, sin copiar código. Hay 1.618 extensiones, URL de release y SHA-256 para cada una. Firma Project Nox obligatoria antes de abrir Android. |
| `Awerkori/anime-extensoes` | `d2b689f3fb1e411b390bb8275ec86a65f7de0b17` | AGPL-3.0-only para contribuciones del catálogo; upstream Aniyomi/Yūzōnō conserva sus licencias. | 273 extensiones indexadas. El índice no publica un manifiesto SHA-256 equivalente: se dice en pantalla y se exige la firma, sin inventar hashes. No se anuncia reproducción dentro de cero-X. |
| `Shebyyy/kotatsu-multi-parsers` | `5a4e2bfcf61c583fed759abe289004a99991e623` | No hay LICENSE en la raíz del clon. Build automatizado desde cinco upstreams. | Sólo exploración de metadatos: 3.662 entradas, 1.113 rotas. `skepsun.json` está vacío. Sin licencia general, hash por versión y contrato completo no se carga ningún JAR. |
| `miyomiorg/Miyomi` | `c53720c21875394b91cf12632ea27dbc899d1be8` | AGPL-3.0; directorio en public preview. | Referencia de descubrimiento; no se copió código ni se convirtieron enlaces en fuentes. |
| `SehonYT/AniPirate` | `8c8366fc60a261c5102325c50532643b846a76d0` | El repositorio público contiene README/logo y no declara LICENSE. | Referencia de descubrimiento solamente; nada copiado. |
| `wotakumoe/wotaku` | `bcf6ebde9b03873dd19c80723a7089467a61d85a` | LICENSE.md: All Rights Reserved. | Referencia documental; prohibido copiar código sin permiso. Nada copiado. |
| `quantavil/everythingmoe` (visor no oficial) | `2f485932d28aa6bf822fa76f4ed69584cf2aa0dc` | MIT para el visor; datos/rankings pertenecen a EverythingMoe. | Se tomaron como criterios NSFW desactivado y comprobaciones bajo demanda; no se importó su dataset como parsers. |

JustIndex se consultó como directorio, pero la búsqueda no identificó un repositorio oficial inequívoco. No se clonó un proyecto homónimo al azar. Los directorios no prueban seguridad, mantenimiento, licencia ni compatibilidad de cada destino.

## Recorrido implementado

1. `IntegracionesActivity` se abre desde el icono de fuentes del panel.
2. `ClienteRepositorios` descarga índices HTTPS fijados a commits, con límite de tamaño y tiempo. No sigue ramas `main`, `repo` ni `latest` para las instantáneas.
3. `ParserIndices` valida paquetes, nombres de APK, versiones, idiomas, fuentes y URLs. Conserva NSFW/rotos en lugar de ocultar esos datos en el modelo. La interfaz oculta NSFW y fuentes rotas por defecto.
4. Al solicitar una instalación, `ClienteRepositorios` consulta la API pública de GitHub Releases (máximo 20 páginas de 25 y 64 MiB por página) y relaciona la entrada elegida con su release real. El catálogo inicial no paga esa descarga. El mapa no se empaqueta: tras la primera resolución se cachea como `nox-release-urls-cache.json` en el almacenamiento externo propio, con escritura atómica y límite de 4 MiB. `tools/auditar_project_nox.py` comprueba las 1.618 relaciones sin tokens ni ejecución de extensiones.
5. `InstaladorProjectNox` descarga a almacenamiento externo propio con límite de 100 MiB, exige SHA-256 cuando existe y valida el certificado mediante `ApkVerifier`.
6. Sólo después abre el instalador del sistema con `FileProvider`. El mensaje es «Android pedirá confirmación», nunca «instalada».

### Prueba binaria real

MangaDex `1.6.5`, paquete `eu.kanade.tachiyomi.extension.all.mangadex`:

- bytes: 103.438;
- SHA-256: `176784fcf18d5ec5e29d22039b8bc4bb2b939865f2d301ce712229a8cf6b36cf`;
- APK Signature Scheme v2 válido;
- certificado SHA-256: `144f44e865cbfae6bde110489ef7ec142f0b1681f627a093a218650e21db5dad`;
- clase declarada: `keiyoushi.source.Generated`, mediante `tachiyomi.extension.class`;
- contrato anunciado por el APK: `tachiyomix.extensionLib=1.6`.

La prueba de red opt-in se ejecuta con `CEROX_NETWORK_TESTS=1`; en la suite normal queda saltada para no convertir una caída de GitHub en un falso fallo.

## Biblioteca Kisara

- Consultas AND, frases entre comillas, `-exclusión` y campos `title:`, `author:`, `tag:`.
- Favorito individual y por lote sobre una única lista compartida entre lector y pantalla.
- Fusión de copias conservando progreso máximo, capítulos máximos (no suma duplicados), fuentes, etiquetas, tracking y favorito.
- Selección y fusión accesibles desde `KisaraActivity`.

Son operaciones de biblioteca local. No demuestran recomendaciones AniScore, OCR, traducción, tracking remoto ni reproducción de anime.

## Límites que NO se ocultan

- **Un APK instalado no equivale todavía a una fuente ejecutable en cero-X.** El contrato antiguo (`SeriePage`, `getPopular`, `ServiceLoader`) no coincide con `extensions-lib 1.6` (`SManga`, `getPopularManga`, `tachiyomi.extension.class`). La interfaz etiqueta la instalación «para Mihon/Aniyomi». La migración del host y su recorrido buscar → capítulos → páginas sigue pendiente.
- Kotatsu JAR no es Mihon APK. El cargador de Kisara incorpora modelos Kotatsu, `MangaLoaderContext`, red, configuración, JS/WebView y adaptadores. El explorador actual no ejecuta JAR ni ofrece hacerlo.
- El certificado esperado prueba continuidad del firmante, no que cada sitio sea seguro, legal o esté disponible.
- Un hash del manifiesto prueba integridad respecto a ese manifiesto, no ausencia de código dañino.
- No se instaló ni validó visualmente este incremento en el LG V60. No se afirma respuesta de contenido de MangaDex ni de otro proveedor.
- Las extensiones comparten UID/permisos con su host si se cargan en proceso; un `ClassLoader` no es un sandbox de seguridad. La confianza explícita y los límites no sustituyen aislamiento del sistema operativo.

## Siguiente incremento exigido

1. Sustituir el contrato incompatible por `extensions-lib 1.6` completo y mantener una adaptación explícita para la fuente local.
2. Leer `tachiyomi.extension.class` del manifiesto, cargar sólo una copia inmutable previamente verificada y registrar fuentes reales en `RepositorioGlobal`.
3. Probar en dispositivo una extensión concreta: buscar → detalle → capítulos → páginas, con errores de red visibles.
4. Separar Aniyomi/anime y Kotatsu/JAR; cada uno necesita su contrato y pruebas propios.
5. Después, valorar funciones adicionales de Kisara y de los proyectos descubiertos, sin copiar código WIP o restrictivo como si fuera estable.

## Kisara frente a ShinKu y houri, medido el 2026-11-13

El dueño preguntó cuál conviene como fuente de manga: `ifritraen/kisara` —el fork de Komikku
con más opciones, aunque con el README sin actualizar— o una combinación de `Harrys-HQ/ShinKu`
y `PineappleTwilight/houri`. Se midió en vez de opinar. Los tres clones están en
`/var/tmp/referencias/` con su SHA en `work/ci/frankie/referencias.tsv`, y los JSON de la API
en `work/ci/frankie/`.

**Primero la categoría, porque cambia la pregunta.** Ninguno de los tres es una fuente de
manga: los tres son lectores que cargan extensiones, y resuelven el catálogo del mismo modo,
contra `https://api.github.com/repos/${repository}/releases` con el contrato `extensions-lib`.
Las fuentes las pone el repositorio de extensiones que se configure, no el lector. Así que
elegir entre estos tres no elige qué manga hay disponible: elige arquitectura de lector. Qué
fuentes hay es una decisión que cero-X ya tomó por su cuenta y está más arriba en este doc —
índice de `Awerkori/manga-extensoes` fijado a commit, 1.618 extensiones con SHA-256, firma
Project Nox obligatoria antes de abrir Android.

| | `ifritraen/kisara` | `Harrys-HQ/ShinKu` | `PineappleTwilight/houri` |
|---|---|---|---|
| licencia | Apache-2.0 | Apache-2.0 | Apache-2.0 |
| linaje | fork de Komikku (no marcado como fork por GitHub) | fork de TachiyomiSY/Mihon | **fork de `komikku-app/komikku`** (★5.019), confirmado por `parent` en la API |
| último commit | 2026-08-30 (**74 días**) | 2026-11-08 (4 días) | 2026-11-04 (8 días) |
| commits en 30 días | **0** | 40 | **100** de los últimos 100 |
| CI de build | `Kisara CI/CD` **fallida** en `main` y `master` (2026-10-01, y también el 2026-08-14) | `build_check.yml` y `build_push.yml` declarados activos y **sin ninguna corrida** | `CI` **success** en `master` (11-04, 11-02) y `PR build check` success (11-05) |
| releases | sólo **prereleases** `build-r10637` (2026-08-13) | `v2.8.1` (2026-11-07), 5 APK, no prerelease | `v1.25.3` (2026-11-04), 6 APK, no prerelease |
| README | sin tocar hace **184 días** | hace 12 días | hace 26 días |
| fuentes `.kt` | **2.847** | 1.407 | 1.795 |
| módulos Gradle | 18 | 14 | **25** |
| ficheros que tocan novela | **696** | 6 | 73 |
| ficheros que tocan anime/vídeo | **492** | 29 | 33 |
| namespace dominante | `eu.kanade.tachiyomi` (1.065) | **`com.shinku.reader` (1.327)**, sólo 68 en `eu.kanade.tachiyomi` | `eu.kanade.tachiyomi` (640) |
| ★ | 2 | 48 | 47 |

Lo que dicen esos números:

- **Kisara tiene razón en el alcance y no en el estado.** Es el que más abarca —2.847 fuentes,
  novela en 696 ficheros y anime/vídeo en 492, más que los otros dos juntos— y su README lleva
  184 días sin tocarse, como decía el dueño. Pero está **parado hace 74 días, con 0 commits en
  el último mes, su CI fallida en las dos ramas y sólo publica prereleases** `build-r*`. Alcance
  en código que no compila en su propio CI no son «más opciones»: es más superficie sin
  verificar.
- **No hay nada nuevo que mirar en Kisara.** Su HEAD de hoy, `d121a8d6bf931adc34b5d32fbe5fd22c4db922af`,
  es **byte a byte el commit que la instantánea del 10 de noviembre inspeccionó** (está en la
  tabla de procedencia de arriba). La decisión de entonces sigue vigente con la misma evidencia
  y no hace falta revisarla: no copiar `main` completo ni llamarlo estable.
- **houri es el único de los tres con build verificado en público.** CI verde en `master`,
  `PR build check` verde, releases estables con semver y 6 APK, y 100 commits en los últimos 30
  días. Además es el que menos cuesta de leer para este proyecto: mantiene el namespace
  `eu.kanade.tachiyomi` del linaje Komikku/Mihon, que es el que cero-X ya usa, y `modules/houri/`
  ya tiene piezas tomadas de ahí (`UniversalTracker`, `Upscaler`, `WebGPUViewer`,
  `WebtoonSupport`, `AchievementsFramework`, `SingleSQLiteConnection`, `DarkModeFilter`).
- **ShinKu está vivo pero no demuestra que compile.** Cuarenta commits en 30 días, HEAD de hace
  4, README de hace 12 y 5 APK por release: hay binarios recientes. Pero sus dos workflows de
  build están declarados `active` y **no tienen ninguna corrida**, así que los APK se producen
  fuera de la vista pública. No es lo mismo que no compilar; es que no hay evidencia de que lo
  haga, y este proyecto no afirma lo que no midió.
- **«Combinar ShinKu y houri» no es combinar, es portar.** ShinKu renombró el árbol completo a
  `com.shinku.reader` (1.327 ficheros) y dejó sólo 68 en `eu.kanade.tachiyomi`, mientras houri
  conserva ese namespace (640). Cada pieza que se tome de ShinKu hay que re-empaquetarla y
  reconciliar dos arquitecturas de 14 y 25 módulos Gradle. En cambio kisara y houri **sí**
  comparten namespace, así que entre esos dos las piezas son intercambiables.

Decisión que se propone, y que es reversible porque es de referencia y no de dependencia:

1. **houri como referencia principal** de arquitectura de lector: es el que está vivo, el que
   demuestra que compila, el del mismo linaje y namespace que cero-X ya usa, y el que ya tiene
   piezas integradas aquí.
2. **ShinKu como referencia secundaria** de interfaz y de lectura pura de manga —es el más
   enfocado (6 ficheros tocan novela)— tomándolo por **comportamiento y no por código**, por el
   rename de namespace y por la falta de evidencia pública de build.
3. **Kisara como referencia de alcance**, no como base: mirar qué intentó con novela y anime,
   que es justo lo que cero-X quiere para el todo-en-uno, sabiendo que está parado y no compila
   en su CI. Si algún día retoma actividad y su CI vuelve a verde, se reevalúa con las mismas
   cuatro medidas de esta tabla.
4. Y sobre las fuentes de manga en sí: ninguna de estas tres opciones las cambia. Lo que las
   determina es el repositorio de extensiones y el contrato `extensions-lib 1.6`, que es el
   «siguiente incremento exigido» de la sección anterior.

Las cuatro medidas que deciden esta tabla y que se pueden volver a ejecutar para reevaluar:
`GET /repos/<r>` (linaje y `pushed_at`), `GET /repos/<r>/commits?per_page=100` (actividad),
`GET /repos/<r>/actions/runs?per_page=30` (CI) y `GET /repos/<r>/releases` (binarios). Los JSON
crudos de las cuatro están guardados, así que la comparación se puede reproducir sin volver a
consultar nada.
