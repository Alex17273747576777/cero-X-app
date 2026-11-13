# Objetivo todo-en-uno: el «frankie»

Corte: 2026-11-13.

## Lo que pidió el dueño, en sus palabras

> «nuestro objetivo es hacer un todo en uno basado en apps como [las 13 de abajo].
> Escogiendo las mejores opciones del ecosistema actual y evolucionandolas al para
> maximizar su potencial y si se que es un poco loco pero espero no creas que es
> fantancia pues lo primero que querida con la cero app es usar ingeniería inversa en
> todos esos proyectos y luego depurar y demas para armar nuestro propio frankie»

Es el objetivo declarado de cero-X-app y ya estaba en marcha antes de que se escribiera
aquí: el código nombra 14 apps del ecosistema **187 veces** (censo con `grep -rhoiE` sobre
`android/app/src/main/java`), hay un módulo `reverse_engineering` con 5 fuentes y
`docs/ASISTENTE-Y-COMPETENCIA.md` ya comparaba brechas contra Watchtower y Kototoro el
12 de noviembre. Lo que faltaba era esto: el objetivo por escrito, el censo medido de las
13 fuentes y una ruta que no dependa de la memoria.

No es fantasía y no se trata como tal. Se trata como lo que es: un objetivo grande que se
cumple por fases verificables, con la licencia y el presupuesto de la máquina como
restricciones medidas y no como opiniones.

## Las 13 fuentes, censadas por API el 2026-11-13

Medido con `GET /repos/<owner>/<repo>`, no de memoria. JSON crudos en
`work/ci/frankie/*.json`. Dos URLs de la lista del dueño **ya no existen como tales**:
`fmhyplay/android` movió a `AnantPlay/android` y `skepsun/Kototoro` a
`Kototoro-app/Kototoro` (la API respondió `Moved Permanently` y se siguió la redirección).
`playtorrio.xyz` no es un repo: es un sitio (HTTP 200, 57.036 bytes, se declara
«open-source media streaming app», menciona `torrent` 30× y `stremio` 12×); el repo que
enlaza su portada, `ayman708-UX/PlayTorrioV3`, **devuelve 404 y ese usuario tiene 0 repos
públicos**, así que no hay fuente que auditar. Los 15 resultados de buscar `playtorrio` en
GitHub son forks y copias de terceros, ninguno oficial.

| repo | lenguaje | licencia | MB | ★ | empujado | fork | ¿se puede copiar código a GPL-3.0? |
|---|---|---|---:|---:|---|---|---|
| `kodjodevf/mangayomi` | Dart | Apache-2.0 | 496 | 4.475 | 2026-11-13 | no | sí |
| `salmanbappi/AniZen` | Kotlin | Apache-2.0 | 301 | 202 | 2026-11-09 | no | sí |
| `ferelking242/watchtower` | Dart | Apache-2.0 | 292 | 4 | 2026-11-13 | no | sí |
| `Kototoro-app/Kototoro` | Kotlin | Apache-2.0 | 242 | 687 | 2026-11-13 | no | sí |
| `unseensnick/Reikai` | Kotlin | Apache-2.0 | 239 | 49 | 2026-11-12 | no | sí |
| `andarcanum/Tadami-Aniyomi-fork` | Kotlin | Apache-2.0 | 140 | 292 | 2026-11-07 | **sí** (`aniyomiorg/aniyomi`, Apache-2.0, 7.894★) | sí |
| `RyanYuuki/AnymeX` | Dart | MIT | 135 | 1.283 | 2026-11-07 | no | sí |
| `Noirero/Miyorare` | Kotlin | GPL-3.0 | 123 | 22 | 2026-11-13 | no | sí, misma licencia |
| `Arthurpendragon9876/DeepSync` | Kotlin | GPL-3.0 | 61 | 2 | 2026-11-13 | no | sí, misma licencia |
| `Nahiro18/Kiroku` | Kotlin | **NOASSERTION** | 27 | 18 | 2026-11-09 | **sí** (`AsrOfficialDev/ReDantotsu`, NOASSERTION, 156★) | **decisión del dueño** |
| `AnantPlay/android` | Java | MIT | 15 | 392 | 2026-11-13 | no | sí |
| `Foxlations/Foxlations` | Dart | Apache-2.0 | 4 | 3 | 2026-10-29 | no | sí |
| `playtorrio.xyz` | — | — | — | — | 2026-11-13 (sitio vivo) | — | **no hay fuente pública** |

Total con historial git: **2,03 GB**. Once de las doce fuentes con repo son copiables a
GPL-3.0: Apache-2.0 y MIT son compatibles (GPLv3 permite combinar con Apache-2.0; MIT es
permisiva), conservando `NOTICE` y el aviso de copyright de cada una.

### El caso Kiroku / ReDantotsu, leído y no supuesto

La API dice `NOASSERTION` porque el fichero `LICENSE.md` (1.434 bytes, leído entero) no es
una licencia SPDX estándar: es la **«Unabandon Public License (UPL)»**, que en sus propias
palabras «incorporates all terms and conditions of the GNU General Public License Version
3» y añade una condición: el fuente de cualquier obra derivada **debe estar públicamente
disponible**, sin restricciones de acceso ni pago. Kiroku hereda la UPL de su padre
ReDantotsu, que declara la misma.

cero-X-app es GPL-3.0 y su fuente es público en GitHub, así que la condición adicional se
cumple de hecho. Pero **no es una licencia reconocida por SPDX** y combinar código bajo una
licencia personalizada es una decisión jurídica, no técnica: la toma el dueño. Mientras no
la tome, Kiroku y ReDantotsu son **referencia de producto** (se leen, se aprende de su
diseño, se compara) y **no fuente de código copiado**. Es el mismo criterio que ya aplica
`proyectos/LICENCIAS.md` del repo `entorno-de-herramientas-de-trabajo` para
`normas-de-diseno`, que tampoco declara licencia.

### Las cuatro Dart no se portan: se estudian

`mangayomi` (4.475★), `AnymeX` (1.283★), `watchtower` y `Foxlations` están en **Dart/
Flutter**. cero-X-app es Kotlin con núcleo compartido en `comun/`. Portar Dart a Kotlin no
es copiar: es reescribir, y reescribir sin poder ejecutar el original para comparar
produce una versión peor que se cree equivalente. Lo que sí se puede tomar de las cuatro,
y es lo que vale:

- **contratos y formatos**, que son independientes del lenguaje: el formato de backup de
  Mangayomi y AnymeX, que `backup_import/BackupFormatDetector.kt` **ya detecta**;
- **modelos de datos y recorridos**: qué guarda, en qué orden lo pide, cómo pagin;
- **endpoints y protocolos**, que se pueden verificar por red desde aquí sin aparato.

Las siete Kotlin/Java (`AniZen`, `Kototoro`, `Reikai`, `Tadami`, `Miyorare`, `DeepSync`,
`AnantPlay`) sí son fuente de código copiable, con atribución.

## Lo que cero-X ya tiene, medido

El objetivo no arranca de cero: **244 fuentes `.kt` en `main`, 87 en `test`, 2 en
`androidTest`, 191 ficheros repartidos en 48 módulos**. Conteo por área:

| área | fuentes | módulos |
|---|---:|---|
| Fuentes y extensiones | 54 | `source_api`(34), `extension_sandbox`(9), `source_js`(4), `extension_factory`(4), `repoforge`(2), `sources`(1) |
| Seguimiento y trackers | 30 | `houri`(14), `kisara`(13), `sync`(2), `kai`(1) |
| Ver (vídeo/IPTV/audio) | 15 | `iptv`(7), `audio_eq`(4), `media`(2), `video`(1), `audio`(1) |
| Web y booru | 15 | `booru`(8), `web`(7) |
| Catálogo y descubrimiento | 12 | `catalog`(2), `directories`(4), `feed`(4), `updates`(2) |
| Ingeniería inversa y diagnóstico | 12 | `reverse_engineering`(5), `diagnostics`(3), `performance`(2), `hardware_monitor`(1), `tools`(1) |
| Leer (manga/novela/cómic) | 11 | `reader`(9), `novel`(1), `comic`(1) |
| IA | 10 | `ai`(4), `aihub`(3), `ai_training`(1), `neural_colorizer`(1), `prisma`(1) |
| Instalación y permisos | 10 | `installflow`(2), `permissions`(3), `environments`(2), `server`(1), `license`(2) |
| Tema e interfaz | 7 | `theme`(3), `adaptive`(2), `mode`(2) |
| Respaldo e importación | 6 | `backup`(3), `backup_import`(3) |
| Descargas | 4 | `downloads`(3), `downloads_advanced`(1) |
| Traducción | 4 | `translation`(4) |
| Plataforma cruzada | 1 | `crossplatform`(1) |

Módulos con 0 tests propios, que es donde el objetivo tiene que poner pruebas antes que
funciones: `backup_import`(3 fuentes, 0 tests), `theme`(3, 0), `extension_factory`(4, 0),
`updates`(2, 0).

## La ruta, en fases con criterio de salida

No se hace «ingeniería inversa de los 13 y luego se arma». Se hace por capas, cada una con
una comprobación que puede fallar, porque un frankie se muere por acumular código que nadie
puede verificar. El orden lo fija lo que ya se midió: primero lo que no necesita aparato,
después lo que sí.

**Fase F1 — Referencias bajadas fuera del workspace. CERRADA el 2026-11-13.**
`scripts/bajar-referencias.sh` clona las fuentes auditables a `/var/tmp/referencias/` con
`--depth 1 --single-branch`, es idempotente (lo que ya está no se vuelve a clonar), rechaza
empezar un clon si quedan menos de 800 MB libres, y escribe la evidencia en
`work/ci/frankie/referencias.tsv` con el SHA, el tamaño y el número de ficheros de cada una.
Es un script y no un estado guardado porque **`/var/tmp` y `/opt/cerox` no sobreviven al
reinicio del sandbox**: este mismo turno uno a mitad de camino borró `/opt/cerox`, el SDK,
el JDK 21 (quedó 11), el swap y los bits de ejecución de 26 ficheros entre los dos repos.
*Criterio de salida, cumplido:* `REFERENCIAS_OK`, 7 fuentes listas y 0 fallidas, y
`comprobar_entorno.py` en `ENTORNO_OK` con el workspace en 2.252 ficheros / 38,89 MiB al
cerrar la fase (2.256 y 38,90 tras añadir las herramientas de F2).

| fuente | licencia | SHA del clon | MB | ficheros |
|---|---|---|---:|---:|
| `Kototoro-app/Kototoro` | Apache-2.0 | `5238195` | 347 | 6.803 |
| `andarcanum/Tadami-Aniyomi-fork` | Apache-2.0 | `f3af9e7` | 109 | 5.245 |
| `unseensnick/Reikai` | Apache-2.0 | `00206fe` | 100 | 4.132 |
| `Noirero/Miyorare` | GPL-3.0 | `8d6028b` | 73 | 3.247 |
| `Arthurpendragon9876/DeepSync` | GPL-3.0 | `ddaf6f4` | 44 | 2.358 |
| `AnantPlay/android` | MIT | `40156e4` | 40 | 257 |
| `salmanbappi/AniZen` | Apache-2.0 | `95160a7` | 33 | 2.296 |

**746 MB en total, no los 2,03 GB que reporta la API**: ese `size` cuenta el historial
completo, y `--depth 1` no lo trae. *Dos exclusiones a propósito:* **Kiroku no se clona**
mientras el dueño no decida sobre su licencia UPL —se lee por la API como referencia de
producto—, y **las cuatro Dart sólo se bajan con `--todas`**, porque no se compilan aquí y
para leer un fichero suelto la API de GitHub lo sirve sin gastar disco.

El otro motivo por el que van fuera del workspace, medido: Kototoro clonado dentro de
`work/` llevó el presupuesto a **9.049 ficheros y 367,90 MiB** y rompió el margen operativo;
sacarlo lo devolvió a 2.246 y 38,86 MiB. Y `/opt/cerox` no es escribible para este usuario
(`touch` da `Permission denied`; sólo `preparar-entorno-android.sh` lo recrea, con sudo),
así que no es un destino alternativo.

**Fase F2 — Auditoría contra fuente. Primer tramo hecho.** Qué permisos declaran frente a
qué componentes exponen, medido sobre el `AndroidManifest.xml` con
`tools/auditar-referencias.py` (no necesita aparato ni compilar: es texto). Evidencia en
`work/ci/frankie/auditoria-manifiestos.tsv`.

| fuente | manifiestos | permisos | componentes | exportados | sensibles |
|---|---:|---:|---:|---:|---:|
| Kototoro | 16 | 24 | 96 | 20 | 10 |
| Miyorare | 7 | 15 | 74 | 21 | 10 |
| DeepSync | 2 | 15 | 70 | 16 | 10 |
| AniZen | 15 | 21 | 25 | 5 | **11** |
| Tadami-Aniyomi-fork | 13 | 15 | 29 | 7 | 8 |
| Reikai | 16 | 19 | 25 | 7 | 8 |
| AnantPlay | 1 | 12 | 14 | 2 | 8 |
| **cero-X-app** | **1** | **21** | **16** | **3** | **11** |

Lo que dice la tabla, y es lo primero que la ruta tiene que resolver:

- **cero-X declara 11 permisos sensibles, tantos como la que más** (AniZen), y tres que
  ninguna de las otras pide: `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`,
  `READ_MEDIA_VIDEO`. En cambio no pide `MANAGE_EXTERNAL_STORAGE` ni
  `RECEIVE_BOOT_COMPLETED`, que sí piden seis de las siete. Un todo-en-uno no se resuelve
  sumando permisos: cada uno tiene que tener el código que lo justifique a la vista.
- **cero-X es un solo módulo Gradle con 16 componentes; las referencias tienen entre 2 y 16
  manifiestos y hasta 96 componentes.** La arquitectura modular no es un adorno: es lo que
  permite que una capacidad falle sin arrastrar a las otras, y es el cambio estructural que
  un frankie exige antes de meter más funciones.
- **Exportados: cero-X 3 frente a 16-21 en Miyorare, DeepSync y Kototoro.** Pocas puertas
  es mejor, pero hay que mirar si las suyas están protegidas por permiso propio antes de
  darlo por ventaja.

Faltan los otros tres ejes de F2, aún sin hacer: qué APIs usan de verdad (no sólo qué
declaran), a qué endpoints hablan, y qué telemetría fusionan sus dependencias. *Criterio de
salida:* una ficha por app en `work/ci/frankie/` con los cuatro ejes, y ninguna afirmación
sin el comando que la produjo.

**Fase F3 — Mapa de brechas contra lo que cero-X ya tiene.** Ampliar la tabla de
`docs/ASISTENTE-Y-COMPETENCIA.md` a las 12 fuentes: qué hace cada una que cero-X no hace,
qué hace cero-X mejor, y qué se descarta a propósito. *Criterio de salida:* cada brecha con
su módulo destino y su prueba, o marcada como descartada con el motivo.

**Fase F4 — Los cuatro módulos sin tests, antes que ninguna función nueva.**
`backup_import`, `theme`, `extension_factory`, `updates`. *Criterio de salida:* suite en
verde con tests que fallen si se rompe el módulo, probado rompiéndolos a propósito.

**Fase F5 — Integración por capacidad, una por fase, con cierre de regla 9.** Empezar por
la que más valor aporta y menos riesgo: importación de bibliotecas ajenas, porque
`BackupFormatDetector` ya detecta los formatos y no hace falta aparato para probar la
detección. *Criterio de salida por capacidad:* `clean :app:testDebugUnitTest
:app:assembleDebug :app:assembleDebugAndroidTest` en verde con las pruebas ejecutadas
(`pruebas_ejecutadas: true`), hashes de APK registrados, y la atribución de licencia en el
fichero que entra.

## Lo que no se puede hacer desde aquí, y no se finge

- **Sin aparato y sin `/dev/kvm`**: nada se ejecuta en Android. `adb devices` vacío. Los
  recorridos reales de una fuente, el `DexClassLoader` y el reproductor de vídeo se
  verifican en `androidTest` **en un dispositivo**, no aquí (regla 6).
- **Sin `dex2jar`**: se abandonó porque produjo `VerifyError` al instanciar MangaDex y no
  es evidencia de nada. La auditoría es contra fuente, que para estas 12 existe.
- **Sin compilar las Dart**: no hay toolchain de Flutter instalada y no se instala para una
  fase que no la necesita. Se leen.
- **Sin credenciales de IA** (`CEROX_AI_BASE_URL/MODEL/API_KEY`): el asistente se prueba
  con transporte falso, que verifica protocolo y errores, no que un proveedor conteste.
- **Sin eludir 403 ni Cloudflare**: AniPirate respondió 403 y así se registra.
  `RepoForgeSourceModule.bypassCloudflare` ya devolvía éxito con «Bypass simulado» y se
  limpió por eso. Un desafío de Cloudflare no se resuelve fingiendo que se resolvió.
- **PlayTorrio**: sin fuente pública no hay nada que auditar. Se puede medir su sitio por
  red (qué anuncia, qué endpoints declara) y nada más.

## Restricción dura de la máquina, medida este turno

2 CPU, 1.983 MB de RAM, **sin `/dev/kvm`**. El entorno de build se cayó a mitad de turno
(JDK revertido a 11, SDK ausente, swap en 0, `gradlew` y 25 ficheros más sin bit de
ejecución, remoto de Git perdido) y se rearmó: `scripts/rearmar-git.sh` con la URL explícita
dio `REARME_OK` y `scripts/preparar-entorno-android.sh` devolvió JDK 21.0.13, SDK completo y
swap de 11 GiB, con `ENTORNO_OK`. **Coste medido del rearme: el disco libre pasó de 20 GB a
7,8 GB.** Ese es el techo real con el que trabaja la ruta, no los 20 GB que había antes.

Con 7,8 GB libres, 746 MB de referencias y 324 MB de salidas de build, el margen existe pero
no es holgado: **no se clonan las cuatro Dart** (927 MB según la API, que cuenta
historial; su peso real con `--depth 1` no se midió porque no se bajaron) mientras no haga
falta algo que la API no pueda servir.

Un cierre completo cuesta 46,2 s con caché y 225,0 s en frío (`docs/PARALELISMO.md`), y deja
`MemAvailable` en 29-58 MB: dos builds a la vez son más lentos que en serie (113 s frente a
78 s). Bajar referencias y compilar a la vez no es una opción en esta máquina.

Y el presupuesto de workspace es la restricción que manda sobre toda la ruta: 128 MiB /
10.000 ficheros, con margen operativo en 110 MiB / 8.500. Hoy está en 2.256 ficheros y
38,90 MiB, medido con `comprobar_entorno.py`. De cada referencia se extrae sólo lo que se va a usar; ninguna se arrastra al
repo, ni siquiera las fichas de auditoría, que viven en `work/ci/frankie/`.
