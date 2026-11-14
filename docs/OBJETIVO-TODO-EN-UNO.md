# Objetivo todo-en-uno: el «frankie»

Corte: 2026-11-14.

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
| `playtorrio.xyz` | — | — | — | — | 2026-11-13 (sitio vivo) | — | **no hay fuente pública** → el hueco de código lo ocupa `komi-store` (cabo ④) |

Total con historial git: **2,03 GB**. Once de las doce fuentes con repo son copiables a
GPL-3.0: Apache-2.0 y MIT son compatibles (GPLv3 permite combinar con Apache-2.0; MIT es
permisiva), conservando `NOTICE` y el aviso de copyright de cada una.

### El hueco de código de PlayTorrio: `komi-store` (cabo ④, cerrado el 2026-11-14)

PlayTorrio no tiene fuente pública que auditar (véase arriba y el cabo ④ de
`docs/PENDIENTES-EXTERNOS.md`). El dueño autorizó sustituirlo por otra referencia de código de
dos listas que aportó. Se censó cada candidata por la API antes de afirmar nada y **las
descripciones no casaron con los repos**: `miyomiorg/Miyomi` y `wotakumoe/wotaku` son TypeScript
de sitio web (no código Android; Wotaku además con licencia NOASSERTION), `SehonYT/AniPirate` es
un índice de datos sin código (127 KB, sin lenguaje ni licencia), y JustIndex, EverythingMoe y
Appteka no citan repo público. Sólo dos tienen código Android real: `omeg4-dev/fmhy-android`
(Kotlin, GPL-3.0, wiki FMHY sin conexión, creado tres semanas antes) y `komi-store/komi-store`.

**`komi-store/komi-store` ocupa el hueco.** No es el «marketplace madre de Appteka» que la prosa
sugería: es *«a free, open-source app store for developers' releases on GitHub, Codeberg &
Forgejo»* (antes «GitHub Store», de ahí sus ★20.656), Kotlin Multiplatform + Compose con
`build-logic`. Apache-2.0 —misma dirección permitida que el resto del linaje, conservar
`NOTICE`—, 100 commits en 30 días, CI verde (Verify, PR checks, CodeQL) y releases maduras
(v1.9.4, 13 recursos). Es la referencia directa de la superficie catálogo+instalación+
actualizaciones de cero-X: resuelve releases por la API de GitHub/Codeberg/Forgejo e instala
APK. Y aporta un modelo que F2 echó en falta en cero-X: **no declara ningún permiso huérfano**
(cero-X tiene 8) y **no trae telemetría** (0 SDK en 49 ficheros de build). Clonado el
2026-11-14: 108 MB, 1.818 ficheros, `19eb744`; fila en `referencias.tsv` y ficha en
`fichas/komi-store.md`. `fmhy-android` queda anotada como secundaria y no se clona. Con
komi-store el cupo de referencias de código queda completo: **12 referencias + cero-X = 13
objetivos auditados**. Las 13 fuentes de la lista del dueño siguen siendo 13 —komi-store no
reemplaza a PlayTorrio en esa lista, reemplaza su *hueco de código auditable*.

### El caso Kiroku / ReDantotsu, leído y no supuesto

La API dice `NOASSERTION` porque el fichero `LICENSE.md` (1.434 bytes, leído entero) no es
una licencia SPDX estándar: es la **«Unabandon Public License (UPL)»**, que en sus propias
palabras «incorporates all terms and conditions of the GNU General Public License Version
3» y añade una condición: el fuente de cualquier obra derivada **debe estar públicamente
disponible**, sin restricciones de acceso ni pago. Kiroku hereda la UPL de su padre
ReDantotsu, que declara la misma.

cero-X-app es GPL-3.0 y su fuente es público en GitHub, así que la condición adicional se
cumple de hecho. **El dueño aceptó la UPL el 2026-11-13** (cabo ① de
`docs/PENDIENTES-EXTERNOS.md`): Kiroku ya se clona como referencia
(`scripts/bajar-referencias.sh`, 22 MB, 1.090 ficheros, `e2d54c4`) y se audita junto a las
otras once fuentes. Si se llega a copiar código de Kiroku, el aviso de la UPL se conserva en
cada fichero que proceda de ahí, como hace `docs/LICENCIA.md` con el linaje Apache/MIT.
ReDantotsu (el padre) sigue sin clonarse: se lee por la API como referencia de producto.

Un dato de la auditoría que conviene no olvidar: Kiroku aplica Firebase **sin condicional** y
trae `google-services.json` commiteado, así que su telemetría está activa de serie —a
diferencia de houri, AniZen o ShinKu, que la gatean tras `Config.includeTelemetry` o la
existencia del fichero. Eso no afecta a la licencia (la UPL va de disponibilidad del fuente,
no de rastreo), pero sí a qué se copia: cero-X no tiene telemetría y si toma algo de Kiroku no
toma ni el Firebase ni el `google-services.json`.

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

**Fase F2 — Auditoría contra fuente. CERRADA el 2026-11-13.** Los cuatro ejes, medidos con
`tools/auditar-referencias.py` sobre los 13 objetivos (12 referencias + cero-X) sin aparato ni
compilación —es texto—. Evidencia: `work/ci/frankie/f2-{manifiestos,apis,endpoints,telemetria}.tsv`
y una ficha por app en `work/ci/frankie/fichas/`. Cada cifra sale de esos ficheros; ninguna se
transcribió de memoria.

*Eje 1 — manifiesto.* Permisos declarados frente a componentes expuestos.

| fuente | manif | permisos | componentes | exportados | sensibles |
|---|---:|---:|---:|---:|---:|
| Kototoro | 16 | 24 | 96 | 20 | 13 |
| Miyorare | 7 | 15 | 74 | 21 | 12 |
| Kiroku | 1 | 21 | 72 | 17 | 13 |
| DeepSync | 2 | 15 | 70 | 16 | 12 |
| houri | 17 | 22 | 23 | 7 | 14 |
| kisara | 15 | 23 | 29 | 7 | 14 |
| AniZen | 15 | 21 | 25 | 5 | 13 |
| ShinKu | 11 | 20 | 20 | 6 | 12 |
| Tadami | 13 | 15 | 29 | 7 | 10 |
| Reikai | 16 | 19 | 25 | 7 | 10 |
| AnantPlay | 1 | 12 | 14 | 2 | 8 |
| **cero-X** | **1** | **21** | **16** | **3** | **13** |

(«Sensibles» sube a 13 en cero-X frente a los 11 de la serie antigua
`auditoria-manifiestos.tsv` porque el conjunto `SENSIBLES` se amplió al reescribir la
herramienta. Son dos series distintas; la autorizada ahora es `f2-manifiestos.tsv`.)

- **cero-X es un solo módulo Gradle, 16 componentes, 1 manifiesto; las referencias tienen entre
  2 y 17 manifiestos y hasta 96 componentes.** houri y kisara reparten su código en 25 y 18
  módulos Gradle. La arquitectura modular no es un adorno: es lo que permite que una capacidad
  falle sin arrastrar a las otras, y es el cambio estructural que un frankie exige antes de
  meter más funciones. F2 lo señala; lo abordan F4/F5.
- **Exportados: cero-X 3, el que menos junto a AnantPlay (2).** Pocas puertas es mejor, pero
  antes de darlo por ventaja hay que mirar si esas 3 están protegidas por permiso propio.

*Eje 2 — APIs usadas frente a permisos declarados.* Qué ejercita el código de verdad.

- **cero-X declara 8 permisos que su propio Kotlin no ejercita** (`ACCESS_WIFI_STATE`,
  `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `QUERY_ALL_PACKAGES`, `READ_EXTERNAL_STORAGE`,
  `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `WAKE_LOCK`) y usa 1 sin declarar
  (`REQUEST_DELETE_PACKAGES`, del manifiesto fusionado de una biblioteca). Es el que más
  permisos declara sin usarlos de los 11.
- **Pero «huérfano» no es «borrable».** Leído el manifiesto entero, cada uno tiene su motivo
  escrito: `QUERY_ALL_PACKAGES` va con un bloque `<queries>` y existe porque sin él
  `getPackageInfo("moe.shizuku.privileged.api")` da falso negativo; `FOREGROUND_SERVICE`
  acompaña a un `<service>` declarado `foregroundServiceType="dataSync"`; `INSTALL_PACKAGES`
  está documentado como vía de Shizuku/root que la app no se concede a sí misma. El hallazgo
  honesto no es «quitar 8 permisos»: es que cero-X pide por adelantado capacidades cuyo código
  aún no está escrito —superficie regalada que F4/F5 tienen que rellenar o recortar, no un
  listado de bajas mecánico.
- Un permiso puede ejercerlo una dependencia o un recurso XML sin dejar rastro en `src/main`;
  la ficha separa «ejercido en código» de «sólo en recursos» y ambas se confirman leyendo.

*Eje 3 — endpoints por host.* Fuera del ruido (GitHub, Discord, TLD reservados de prueba,
strings de traducción).

- **cero-X: 49 hosts.** Los tres primeros, leídos y no grepados: `everythingmoe.com` (8) y
  `miyomi.app` (6) en `CatalogModule.kt`, `anipirate.xyz` (7) en `DirectoryParsers.kt`.
  Catálogos y directorios, no rastreo.
- Kototoro es el que más habla (173 hosts, `huggingface.co` 41 veces para modelos de ML);
  kisara 119, houri 103. El eje excluye i18n y comentarios, que antes inflaban la cuenta (el
  FlareSolverr por defecto `192.168.1.202` de AniZen salía 22 veces, una por idioma).

*Eje 4 — telemetría de dependencias.* Qué SDK de rastreo entra en el APK, distinguiendo
declarado en el catálogo de aplicado de verdad.

- **cero-X: ninguno.** Cero SDK en sus 5 ficheros de build reales, confirmado por grep directo
  además de por la herramienta. Es la postura más limpia posible y no se toca.
- **9 de 12 referencias cablean telemetría.** Seis usan Firebase (AniZen, houri, kisara, Reikai,
  ShinKu, Kiroku) y tres ACRA —reporte de fallos abierto y autoalojable, no un rastreador como
  Firebase— (DeepSync, Kototoro, Miyorare). Tadami no tiene; komi-store tampoco (0 SDK en 49
  ficheros de build); AnantPlay compila con `build.sh` y
  no Gradle, así que su telemetría **no se midió** (la herramienta reporta `ficheros_build=0`,
  no «ninguna»).
- **El patrón que importa para el frankie:** houri, AniZen, Reikai y kisara aíslan la
  telemetría en un módulo Gradle propio (`telemetry/`, 3-5 ficheros) que sólo se compila con
  `if (Config.includeTelemetry)`, falso salvo `-Pinclude-telemetry`. ShinKu la condiciona a que
  exista `google-services.json`. Once de los doce clones **no** traen ese `google-services.json`
  commiteado —está en `.gitignore`—, así que no compilan con telemetría activa de salida; houri
  lo dice en `app/build.gradle.kts:66`: «no telemetry in the lean variant». **La excepción es
  Kiroku**, que aplica Firebase sin condicional y trae `google-services.json` commiteado: su
  telemetría sí está activa de serie. cero-X ya está donde esas variantes «lean» quieren llegar,
  y si toma algo de Kiroku no toma ni el Firebase ni ese fichero.

*Disciplina aplicada, que es parte del cierre.* La primera corrida del eje 4 era falsa en todas
las apps: buscar subcadenas minúsculas hizo que «adjust» casara con `adjustResize` y «umeng» con
«Diumenge» (domingo en catalán). Se validó cada coincidencia leyendo la línea antes de escribir
nada, se reescribieron los ejes 2, 3 y 4 con coordenadas reales y raíces correctas, y sólo
entonces se publicó. Cuatro falsos positivos cazados, documentados en el cabo ③ de
`docs/PENDIENTES-EXTERNOS.md`.

*Criterio de salida: cumplido.* Una ficha por app (11) en `work/ci/frankie/fichas/` con los
cuatro ejes, cuatro TSV, y ninguna afirmación sin el comando o la línea que la produjo.

**Fase F3 — Mapa de brechas contra lo que cero-X ya tiene.** Ampliar la tabla de
`docs/ASISTENTE-Y-COMPETENCIA.md` a las 12 fuentes: qué hace cada una que cero-X no hace,
qué hace cero-X mejor, y qué se descarta a propósito. *Criterio de salida:* cada brecha con
su módulo destino y su prueba, o marcada como descartada con el motivo.

**ABIERTA el 2026-11-14, paso 1 hecho.** Las cuatro fuentes Dart (el cabo ⑤) están censadas
por la estructura de su `lib/` en `docs/ASISTENTE-Y-COMPETENCIA.md`, con tres brechas concretas
anotadas: servidor autoalojado e índice local (watchtower), trackers unificados (AnymeX) y motor
de extensiones (mangayomi/watchtower frente al QuickJS de cero-X). Queda censar las 8 fuentes
Kotlin/Java —`komi-store` incluida— hasta completar la matriz de las 12.

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
