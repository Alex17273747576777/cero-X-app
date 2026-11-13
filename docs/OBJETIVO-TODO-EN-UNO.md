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

**Fase F1 — Referencias fuera del workspace y censo completo.** Clonar las 12 fuentes a
`/var/tmp/referencias/` con `--depth 1`, nunca a `work/`. *Criterio de salida:* los 12
clones presentes, `du -sm` de cada uno anotado, y `comprobar_entorno.py` en `ENTORNO_OK`
con el workspace por debajo de 8.500 ficheros / 110 MiB. *Ya medido por qué:* Kototoro
clonado dentro de `work/` llevó el workspace a **9.049 ficheros y 367,90 MiB** y rompió el
presupuesto; sacarlo lo devolvió a 2.246 y 38,86 MiB. Y `/opt/cerox` **ya no es escribible**
(`touch` da `Permission denied`, no soy root), que era donde vivían el SDK y las cachés, así
que el sitio ahora es `/var/tmp`.

**Fase F2 — Auditoría contra fuente de las siete Kotlin/Java.** Qué permisos declaran
frente a qué APIs usan, qué componentes exportan sin protección, a qué endpoints hablan,
qué telemetría fusionan sus dependencias. Es el método ya usado en ProjectNox y
OmniShizuku. *Criterio de salida:* una ficha por app en `work/ci/frankie/` con los cuatro
ejes, y ninguna afirmación sin el comando que la produjo.

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

2 CPU, 1.983 MB de RAM, **swap en 0** (los 11.264 MB de `/swapfile-cerox` no están: se
perdieron con `/opt/cerox`), JDK revertido a **11** en `/usr/bin/java` (el proyecto exige
21), SDK de Android ausente, `gradlew` sin bit de ejecución. O sea: **el entorno de build
Android está caído ahora mismo** y rearma con `scripts/preparar-entorno-android.sh` es el
paso previo a cualquier fase que compile. Un cierre completo cuesta 46,2 s con caché y
225,0 s en frío (`docs/PARALELISMO.md`), y deja `MemAvailable` en 29-58 MB: dos builds a la
vez son más lentos que en serie (113 s frente a 78 s). Clonar 12 repos y compilar a la vez
no es una opción en esta máquina.

Y el presupuesto de workspace es la restricción que de verdad manda sobre esta ruta:
128 MiB / 10.000 ficheros, con margen operativo en 110 MiB / 8.500. Las 12 fuentes suman
**2,03 GB con historial**, así que viven fuera del workspace y de cada una se extrae sólo
lo que se va a usar, no se arrastran al repo.
