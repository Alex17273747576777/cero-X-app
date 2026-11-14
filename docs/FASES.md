# Estado secuencial del proyecto

Actualizado: 2026-11-14. Este archivo registra trabajo pendiente y evidencia; no sustituye los resultados XML ni los APK.

## Fase 0 — Recuperar el entorno (completada)

- Síntoma: workspace histórico de 4.038,7 MB/32.149 archivos y procesos terminados con «Something went wrong».
- Recuperación comprobada: JDK 21.0.13, Android SDK/build-tools 36, ADB y Gradle 8.13. Sin emulador ni modelos.
- Se retiraron dos APKs antiguos regenerables; sus SHA-256 quedaron en `work/ci/release-108-sha256.txt`.
- Cierre: 34,71 MiB y 1.046 archivos antes de retirar la caché local regenerable; sin `~/.gradle` ni `~/.m2`.
- Reconstrucción: `scripts/preparar-entorno-android.sh`; rutas: `entorno/local.sh`; auditoría: `tools/comprobar_entorno.py`.
- Verificación final: `work/ci/entorno-host16-validacion-04.log.gz`, BUILD SUCCESSFUL, 528 pruebas/0 fallos/0 errores/8 omitidas, `assembleDebug` y `assembleDebugAndroidTest`.
- Regla aprendida: `systemProp.maven.repo.local` no llega al worker de Robolectric. `CEROX_M2_REPO` se pasa explícitamente en `tools/low-memory.gradle`; Gradle usa `/opt/cerox/gradle`.

## Fase 1 — Host extensions-lib 1.6 / MangaDex (base completada; Android físico pendiente)

- El host compila; los fallos anteriores de Robolectric no son una ejecución DEX.
- Se abandonó dex2jar: produjo `VerifyError` al instanciar MangaDex. No pertenece a producción ni se acepta como evidencia Android.
- JVM: `MangaDexRealHostTest` comprueba hash, firma y contrato del APK auténtico, opt-in con `CEROX_MANGADEX_APK`.
- Android: `MangaDexAndroidTest` usa el cargador real y recorre búsqueda → detalle/capítulos → páginas → imagen.
- Ejecución Android pendiente: copiar el APK fijado al dispositivo y pasar `-e mangadexApk ... -e networkTests 1`. Compilar no equivale a ejecutar este recorrido.
- Cierre JVM/build completado: 528/0/0/8 y ambos APK. El APK debug tiene 56.883.812 bytes y SHA-256 `c41eba66a7e469f46ffd6a41e7dd49fa263d15c6d4470eed2836d8119b10ccad` (build de validación 03; el binario quedó en caché excluida).
- El siguiente commit cierra esta base. La ejecución en LG V60/dispositivo permanece en la fase física y no se afirma.

## Fase 2 — Navegación y cascarones visibles (completada)

- Commit base: `2ad5309`.
- Barra de cinco destinos, estado guardado, Ajustes y host de fragments; catálogo con filtros/enlaces; modos navegables; blur no funcional retirado.
- Inventario completo y deuda explícita: `docs/INVENTARIO-CASCARONES.md`.
- Pruebas dirigidas verdes en `work/ci/navegacion-fase-01.log` y `navegacion-fase-02.log`.
- Cierre completo: `work/ci/navegacion-cierre-02.log.gz`, BUILD SUCCESSFUL, 535 pruebas/0 fallos/0 errores/9 omitidas, `assembleDebug` y `assembleDebugAndroidTest`.
- La primera corrida detectó tres expectativas antiguas de barra; se actualizaron para exigir los nuevos recorridos, sin borrar guardas.
- Commit de cierre: se registra en Git al terminar esta fase.
- No se afirma rediseño visual completo de todas las Views ni validación física.

## Fase 3 — Directorios y búsqueda (completada)

Brave/FMHY existentes y JustIndex, Miyomi, Wotaku, EverythingMoe y AniPirate como orígenes independientes. Appteka Rebuild, FMHY Android y Komi Store son fichas externas, no instalación.

- Destino real en Fuentes → Directorios, filtros combinados, NSFW oculto, caché externa y actualización manual.
- Auditoría y límites en `docs/DIRECTORIOS.md`.
- Prueba real opt-in: Miyomi 220, JustIndex 73, EverythingMoe 432, Wotaku 469, FMHY 3.714; AniPirate HTTP 403 explícito. Evidencia `work/ci/directorios-red-02.log` y `directorios-red-02-resumen.txt`.
- Cierre completo: `work/ci/directorios-cierre-01.log.gz`, BUILD SUCCESSFUL, 543 pruebas/0 fallos/0 errores/10 omitidas, ambos APK.
- Commit de cierre registrado en Git.

## Fases posteriores

1. **Motor adaptativo (completado en JVM/build):** perfiles por sesión/categoría, señales locales, recomendaciones explicables, deshacer/restablecer y aplicación manual sin reordenar durante lectura. Reglas en `docs/MOTOR-ADAPTATIVO.md`; no es IA generativa. Cierre `work/ci/adaptacion-cierre-01.log.gz`: 549/0/0/10 y ambos APK. Falta revisión visual física.
2. **Rediseño Views y Booru (completado en JVM/build):** Booru Safebooru (`docs/BOORU.md`) y tema DayNight, ActionBar/TopAppBar y catálogo (`docs/DISENO-VIEWS.md`). Cierre `work/ci/views-cierre-01.log.gz`: 558/0/0/11 y ambos APK. Falta revisión visual física.
3. **Pendientes físicos y externos (bloqueados y documentados):** Kotatsu índice real 3.662/1.113 rotas, sin ejecución de parsers; LG V60/MangaDex/visual sin ADB; IA autenticada sin credenciales, prueba opt-in compilada y omitida. Ver `docs/PENDIENTES-EXTERNOS.md`.
4. **Dashboard de módulos (completado en JVM/Robolectric/build):** acceso real desde Ajustes, búsqueda, salud y detalles del gestor, inicio/parada en segundo plano y bloqueo de parada cuando hay dependientes activos. Resultados visibles tras refrescar y manejo de rotación/errores. Pruebas de ciclo/dependencias, filtro+detalle Android y toque semántico Compose. Cierre `work/ci/dashboard-cierre-01.log.gz`: **563/0/0/12** y ambos APK; hashes en `work/ci/dashboard-cierre-sha256.txt`. La revisión física de acciones, foco, contraste y lector de pantalla sigue pendiente. Commit `81915cf`.
5. **Visor de imagen con zoom y descarga explícita (completado en JVM/Robolectric/build):** tocar una miniatura de Safebooru abre un visor propio en vez del navegador. Pellizco, doble toque sobre el punto tocado y arrastre con límites reales; botones −/+/Ajustar/1:1 que hacen la misma operación que el dedo; escala visible y botones desactivados cuando la operación no tiene sentido; dimensiones declaradas por la API usadas antes de que llegue la imagen; fallo de carga con su motivo sin quitar el zoom; escala recuperada tras rotar. Descarga sólo con botón, a `AlmacenExterno/booru/`, por `.tmp` renombrado, con host/rating/tipo/tamaño comprobados y sin caída al interno. Sustituye al `downloadMedia` que devolvía un fallo fijo. 30 pruebas nuevas: `VisorTransformacionTest` 13, `DescargasBooruTest` 11, `VisorBooruAndroidTest` 6. Cierre limpio `work/ci/visor-cierre-limpio-02.log.gz`: **593/0/0/12** y ambos APK; hashes en `work/ci/visor-cierre-sha256.txt`. Los gestos reales, el render de Coil y la escritura en la carpeta externa siguen pendientes de aparato.

6. **Pendientes del visor: lo que se podía cerrar sin aparato (completado; lo físico queda armado y sin ejecutar).** Dos cosas de la lista anterior sí eran cerrables aquí y se cerraron: (a) `DescargasBooruRedRealTest`, opt-in con `CEROX_LIVE_BOORU=1`, descargó de verdad la publicación 6540805 de Safebooru —**5.411.582 bytes**, PNG comprobado por cabecera mágica, ningún `.tmp` residual y segunda llamada `yaExistia=true` sin volver a bajar—, evidencia en `work/ci/descarga-real-01-resumen.txt`; (b) `VisorFisicoAndroidTest`, cuatro pruebas de instrumentación que convierten la revisión pendiente en un comando: tactos reales con `sendPointerSync` (pellizco, doble toque, arrastre), render de Coil con drawable de tamaño real, descarga efectiva en `AlmacenExterno` con ruta/tamaño/cabecera, y contraste WCAG AA medido sobre el tema resuelto por el aparato más etiquetas y objetivo táctil de 48 px. **Compilada y empaquetada —verificada dentro de `classes4.dex` del APK androidTest— pero nunca ejecutada**: `adb devices` sigue vacío. Cierre limpio `work/ci/visor-fisico-cierre-01.log.gz`: **594/0/0/13** y ambos APK; hashes en `work/ci/visor-fisico-sha256.txt`. La omitida nueva es la prueba de red opt-in.

7. **Auditoría del catálogo de apps (completada con red real).** El último pendiente que no necesitaba aparato ni credenciales. Las 52 fichas tenían los metadatos escritos a mano; se contrastaron con la fuente de verdad de cada una, la API del alojamiento. Hallazgos medidos: **3 licencias falsas** (`cloudstream` decía Apache-2.0 y es GPL-3.0; `miru` decía Apache-2.0 y es AGPL-3.0; `gallery_dl` decía Public Domain/Unlicense y es GPL-2.0, comprobado leyendo el archivo LICENSE de 18.092 bytes en Codeberg y contrastándolo con GitHub), **4 enlaces muertos** con HTTP 404 en la API y en la web (`tachiyomi`, `aniyomi`, `openlib`, `tachiyomi_sy`), 1 repositorio archivado (`kotatsu`), 21 licencias que decían «Open Source» —un marcador que parece un dato—, 16 variantes libres de plataforma y un `healthCheck` que declaraba dos categorías inexistentes (`audio`, `servers`) omitiendo tres reales. Resultado: 18 licencias comprobadas, 34 `SIN_VERIFICAR` con su motivo, tiendas y directorios curados separados de las apps, clasificación de enlaces por host y ruta, plataformas normalizadas sin afirmar soporte anunciado («iOS coming soon» no cuenta) y ninguna ruta de descarga de APK. 14 pruebas nuevas: `CatalogoAuditoriaTest` 13 y `CatalogoLicenciasRedRealTest` 1 (opt-in con `CEROX_LIVE_CATALOGO=1`, que repite la auditoría contra la API y avisa si una licencia cambia o un enlace muerto revive). Documentación en `docs/CATALOGO-AUDITORIA.md`.

8. **Latido: reglas y habilidad de autoverificación (completada).** Se convirtieron en reglas y en una comprobación ejecutable dos cosas que hasta ahora se hacían a mano en cada sesión: revisar que los docs no se quedaron atrás y que los pendientes siguen teniendo salida. `tools/latido.py` imprime `HEARTBEAT_OK` sólo si no queda nada obsoleto, vencido ni sin atender; si no, clasifica las anomalías en `BLOQUEO` y `AVISO` y dice cómo arreglar cada una. Revisa árbol de Git, evidencia citada que exista, fechas de corte contra el último commit que tocó cada doc, pendientes con comando de salida, bloqueos que no hayan desaparecido del doc, identidad Git, toolchain y logs crudos. Reglas 13-15 en `AGENTS.md` y sección nueva «Trato con el usuario» con las 16-19; habilidad en `.agents/skills/cerox-latido/SKILL.md`. En su primera ejecución encontró dos anomalías reales: `INVENTARIO-CASCARONES.md` declaraba corte de 2026-11-12 y se había modificado el 13, y el toolchain se había perdido otra vez en el cambio de turno. **No toca ninguna fuente Android**, así que no aplica el cierre Gradle de la regla 9: el último cierre verificado sigue siendo 608/0/0/14 en `30d84c0`.

9. **Entorno de pruebas en Android 16 (completado en JVM; el aparato sigue sin existir).** Se pidió probar contra Android 16 o la beta 3 de Android 17, con un perfil de aparato reciente o una ROM GSI, OmniShizuku para shell, y 10-15 GB de swap o mejor zram. Lo alcanzable y lo que no, todo medido:

   - **Las pruebas corrían en Android 14.** `compileSdk` y `targetSdk` eran 36, pero las 18 clases con `@Config` fijaban `sdk = [34]`: el build compilaba contra Android 16 y la suite probaba contra Android 14, en verde, sin que nada lo avisara. Se subieron a 36 y se añadió `android/app/src/test/resources/robolectric.properties` con `sdk=36`, para que ninguna prueba nueva vuelva a heredar un nivel arbitrario.
   - Eso exigió subir Robolectric de **4.13 a 4.16.1**. Una sonda temporal de cuatro niveles dio con 4.13 **1 de 4** (SDK 36 y 37 reventaban con `IllegalArgumentException` en `UnknownSdk.java:45`); con 4.16.1 dio **3 de 4: API 34, 35 y 36 arrancan, 37 no** («API level 37 is not available»). Android 17 beta 3 queda fuera aunque `platforms/android-37.2-beta3` esté instalado: la plataforma del SDK sirve para compilar, el jar `android-all-instrumented` es otra cosa y para API 37 aún no hay soporte estable.
   - Subir de nivel no cambió el resultado de la suite: **611 pruebas, 0 fallos, 0 errores, 14 omitidas** en 87 suites —las 608 de antes más las 3 de la guarda nueva. El `maxHeapSize = '384m'` de `tools/low-memory.gradle` bastó tal cual; no hizo falta tocarlo.
   - Guarda nueva `core/NivelDeSdkDePruebasTest` (3 pruebas): comprueba en tiempo de ejecución `SDK_INT == 36` y `RELEASE == "16"`, que `robolectric.properties` declara `sdk=36`, y escanea `src/test` para que ninguna clase vuelva a anclar un nivel inferior.
   - **Swap de 12 GiB** activo, verificado por `/proc/swaps` y `free -m` sobre 1.982 MB de RAM. La receta de `scripts/preparar-entorno-android.sh` pasó del rango 5-12 GiB al pedido **10-15 GiB**, con un suelo de 9 GiB libres justificado por medida (toolchain ~3 GiB + builds ~3 GiB + los 3 GiB por debajo de los cuales `tools/comprobar_entorno.py` da ERROR) y un respaldo de 5 GiB cuando el rango no cabe, porque sin nada de swap R8 mata el demonio a mitad del cierre. En este sandbox (25 GiB de disco, ~21 libres al rearmar) la receta elige 12 GiB; con 25 GiB libres elegiría 15.
   - **zram no es posible, y aquí tampoco sería mejor:** `sudo modprobe` no existe, no hay `/sys/block/zram*` ni `zramctl`, así que no se puede cargar el módulo aunque haya sudo sin contraseña. Con 1,9 GiB de RAM un zram útil cabría en 2-4 GiB comprimidos que además consumen RAM al llenarse; swap en disco es lo correcto para esta máquina.
   - **No hay emulador posible:** `/dev/kvm` no existe y `/proc/cpuinfo` tiene **cero** flags `vmx`/`svm`, con 2 CPUs. Sin virtualización por hardware no arranca un AVD, ni una imagen GSI, ni Cuttlefish; `adb devices` sigue vacío. `mobolist.net/en` resultó ser un catálogo comercial de precios y especificaciones, no una fuente de perfiles de dispositivo ni de ROMs.
   - **OmniShizuku, auditado contra su fuente y no adoptado —por falta de aparato, no por su firma.** Es un **fork de Shizuku** (clases en `moe.shizuku.manager.*`, `libshizuku.so` y `librish.so` dentro, README: «feature-rich fork of Shizuku»), Apache-2.0, 31 MB y 613 ficheros Kotlin, repo creado el 2026-10-24. Que sólo publique `*_debug.apk` tiene explicación y no es siniestra: `build_apk.sh` sólo construye `:manager:assembleDebug` y `signing.gradle` cae al keystore de depuración cuando falta `signing.properties`. **La primera versión de este documento decía que «la regla 7 no deja incorporar un artefacto sin firma de release», y eso es falso**: la regla 7 no dice tal cosa, era una interpretación propia presentada como cita. Lo que de verdad aparece al auditar contra la fuente es otra cosa: `firebase-analytics` y `firebase-crashlytics` con su `google-services.json` (project `omnishiziku`) en una herramienta con `WRITE_SECURE_SETTINGS` en 96 ficheros, salidas a `openrouter.ai` y a la API de Gemini, una grabadora de llamadas real (`callrecorder/ShellRecordingEngine.kt`), 17 componentes `exported=true` sin permiso que los proteja, y `READ_CONTACTS`/`READ_CALL_LOG` declarados sin uso encontrado. En contra: el proyecto publica su propia auditoría con 427 alertas de mobsfscan clasificadas una a una y las correcciones están en `main`. Medidas completas en `work/ci/omnishizuku-auditoria-01.txt`; criterio de salida en `docs/PENDIENTES-EXTERNOS.md`.

## Rearme del entorno tras el reinicio del sandbox (2026-11-13)

El workspace sobrevivió, el toolchain no. Al reanudar: JDK 21 ausente (quedaba JDK 11), Android SDK ausente, `/opt/cerox` (Gradle y m2) vacío, sin swap, y **seis archivos versionados habían perdido el bit de ejecución** (`android/gradlew`, `scripts/*.sh`, `tools/comprobar_entorno.py`, `apk/como-verificar.sh`), lo que dejaba el árbol sucio sin cambio de contenido.

- Recuperación: `scripts/preparar-entorno-android.sh` (JDK 21.0.13, build-tools 35/36, platform 36, platform-tools, swap de 3 GiB) y `chmod +x` de los seis archivos. `entorno/local.sh` no lleva bit de ejecución: se dejó como estaba.
- Línea base reproducida **byte a byte** sobre `81915cf`: 563/0/0/12 y los mismos SHA-256 que antes del reinicio. Evidencia `work/ci/base-post-rearmado-01.log.gz` (el crudo pesaba 49,9 MB por el volcado de un aviso de D8; se filtraron las líneas de traza).
- Regla aprendida: un reinicio de entorno no sólo borra JDK/SDK, también **los modos de archivo**. `git status` con «mode change 100755 => 100644» es un síntoma, no ruido.

## Regla aprendida sobre los hashes de APK

Un APK debug **no es comparable entre un build limpio y uno incremental**. Sobre el mismo commit `81915cf`:

| build | bytes | SHA-256 (prefijo) |
|---|---|---|
| limpio, antes del reinicio | 57.055.703 | `6e859cd9` |
| limpio, tras rearmar | 57.055.703 | `6e859cd9` |
| incremental, tras rearmar | 58.621.682 | otro |

La diferencia de 1,57 MB no es código: es el empaquetado de los 21 dex. Comprobado con `git stash` + rebuild, el visor añade 26.622 bytes en limpio (57.055.703 → 57.082.325, `3c509e47`). Para comparar artefactos hay que usar siempre el mismo modo de build, y los hashes que se registran en `work/ci/` son de builds limpios.

## Fusión con origin/main, cadena de suministro JS y herramientas al día (2026-11-13)

- Base: rama `fases-verificadas-2026-11-13`, commit de fusión `7bbc2ae` con dos padres (`57538ab` de la rama y `1fdad35` de `main`). Base común de la divergencia `0aaad47768ef`.
- Los cinco conflictos se resolvieron leyendo las dos versiones (`git show :2:ruta` y `:3:ruta`), no eligiendo lado a ciegas. Dos eran `add/add`: `main` y la rama crearon `InstaladorProjectNox.kt` e `IntegracionesActivity.kt` por separado. Ganó la rama en los cinco por evidencia y no por preferencia —`InstaladorInternoNox.kt` llama a `preparar()`, que la versión de `main` no tiene, así que ésa **no compilaba**— y se auditó lo que se pierde: de las 385 líneas que `main` añadió sobre la base en esos ficheros, 27 no están y las 27 son formato o texto sustituido a propósito. En los tres que Git fusionó solo se hizo la misma comprobación: las 5 líneas que `main` añadió están presentes y ninguna de las que borró sigue ahí.
- La intención de honestidad de `main` se conservó aunque su texto quedara obsoleto: decía que el contrato de ejecución dentro de cero-X «aún está en migración», y ya no es cierto porque se implementó, pero nunca se ejecutó en aparato. La pantalla ahora dice «Ejecutar no se ha verificado en un aparato físico: sin dispositivo no hay prueba de que las fuentes carguen de verdad» y `NavegacionTest` lo afirma, así que no se puede borrar en silencio. Pendiente con criterio de salida en `docs/PENDIENTES-EXTERNOS.md`.
- Dependabot: las 3 alertas abiertas (1 alta RCE, 1 moderada DoS, 1 baja XSS) son todas de `serialize-javascript` 6.0.2 en `comun/kotlin-js-store/yarn.lock`, arrastrado por `mocha@10.7.0` del target `js(IR) { nodejs() }`. Cerrarlas exige `>= 7.1.3`, un salto mayor que el `^6.0.2` de mocha no permite, y forzarlo rompería el target JS **sin poder verificarlo aquí** (`comun/build` no existe, `yarn` no está instalado, ningún workflow lo compila). Se midió en cambio que no alcanza al producto: la app consume `comun/` por fuentes, el APK lleva **0 ficheros `.js`** y CI no toca el target. Nuevo `core/CadenaDeSuministroJsTest` (3 pruebas) ata esa conclusión, y afirma la versión 6.0.2 explícitamente para que un cambio obligue a releer las alertas.
- Herramientas: `tools/latido.py` gana `comprobar_remoto`, que era lo que faltaba para que la regla 11 tuviera contraparte —el sandbox nace sin `origin`, sin `credential.helper` y sin identidad, y nada lo cazaba. Comprueba remoto existente, URL sin token (BLOQUEO si lo hay), helper presente y ejecutable, alguna forma de autenticarse y rama con upstream. Siete caminos probados con ruptura deliberada. No hace llamadas de red: un corte de red convertido en aviso de respaldo sería un falso positivo.
- Se corrigió el docstring de `comprobar_respaldo`, que seguía afirmando que el repositorio no tiene remoto.
- Regla 18 nueva y hueco real cerrado: Gradle sólo vigila classpath y fuentes compilados, así que una prueba que lee `LICENSE`, `build.gradle` o un lockfile **no se re-ejecuta** cuando cambian. Medido: un comentario añadido a `android/app/build.gradle` y `BUILD SUCCESSFUL in 2 s` sin ejecutar ninguna prueba. Declarados `inputs.files(...)` en `tasks.withType(Test)` y comprobado que un cambio de sólo texto en `LICENSE` vuelve a ejecutar la suite (de paso demostró que `LicenciaRepoTest` falla si se corrompe el LICENSE: guardia real).
- Cabeceras `SPDX-FileCopyrightText: 2026 La Bandita` y `SPDX-License-Identifier: GPL-3.0-only` en las cuatro herramientas, que era lo que faltaba para que el fichero suelto dijera de dónde viene. Titular y licencia salen del `NOTICE` del repo, no inventados.
- Higiene de ramas: la rama local se llamaba `fusion-main-2026-11-13` sin upstream y existía un `main` local obsoleto que no era el `main` remoto. Renombrada a `fases-verificadas-2026-11-13`, upstream fijado y el `main` local borrado (era ancestro del HEAD; el real vive en `origin/main`).
- Cierre completo `work/ci/fusion-main-cierre-01.log.gz` y resumen en `fusion-main-cierre-01-resumen.txt`: `clean :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` → **BUILD SUCCESSFUL in 3m 28s**, 83 tareas, **88 suites, 614 pruebas, 0 fallos, 0 errores, 14 omitidas**, todos los XML con menos de 4 minutos de edad. `app-debug.apk` 57.102.093 B `581b2de7ee2e3d6f…` —byte a byte idéntico al del commit de fusión, que es la comprobación de que las herramientas y la guardia nueva no tocan el producto— y `app-debug-androidTest.apk` 655.982 B `8ab2d4d1198d27a2…`.
- Limpieza de entorno: borrados los jars huérfanos de Robolectric `14-…-i6`, `14-…-i7` y `15-…-i7` (336 MB) después de medir que 18 ficheros piden `sdk = [36]` y **ninguno** 34 o 35. Disco de 4,5 G a 4,8 G libres.

## Cierre acumulado de esta continuación

- Commits: navegación `3407ebb`; directorios `985ec17`; motor adaptativo `4b75d0f`; Booru `7a05c0b`; Views `9f53201`; pendientes externos `e3260b0`; dashboard `81915cf`; visor de imagen `3b9e7f4`; descarga real y recorrido físico `566a4c5`; auditoría del catálogo `30d84c0`; latido y reglas `d02b311`; pruebas en Android 16 `4e56382`.
- Validación previa `work/ci/cierre-final-01.log.gz`: **559 pruebas, 0 fallos, 0 errores, 12 omitidas**, `assembleDebug` y `assembleDebugAndroidTest`.
- Validación con dashboard `work/ci/dashboard-cierre-01.log.gz`: **563 pruebas, 0 fallos, 0 errores, 12 omitidas**, ambos APK.
- Línea base tras rearmar el entorno `work/ci/base-post-rearmado-01.log.gz`: **563/0/0/12** y los mismos SHA-256 que antes del reinicio.
- Validación con el visor `work/ci/visor-cierre-limpio-02.log.gz`: **593 pruebas, 0 fallos, 0 errores, 12 omitidas**, `assembleDebug` (57.082.325 bytes, `3c509e47…`) y `assembleDebugAndroidTest` (634.852 bytes, `6b02dceb…`).
- Validación con la descarga real opt-in y el recorrido físico `work/ci/visor-fisico-cierre-01.log.gz`: **594 pruebas, 0 fallos, 0 errores, 13 omitidas**, `assembleDebug` (57.082.373 bytes, `d8dd01f7…`) y `assembleDebugAndroidTest` (655.982 bytes, `8ab2d4d1…`).
- Validación con la auditoría del catálogo `work/ci/catalogo-cierre-01.log.gz`: **608 pruebas, 0 fallos, 0 errores, 14 omitidas** en 86 suites, `assembleDebug` (57.102.001 bytes, `19e7471f…`) y `assembleDebugAndroidTest` (655.982 bytes, `8ab2d4d1…`, idéntico al anterior porque ninguna fuente instrumentada cambió). El catálogo añade 19.628 bytes en limpio.
- Validación con las pruebas en Android 16 `work/ci/cierre-android16-01.log.gz`: **611 pruebas, 0 fallos, 0 errores, 14 omitidas** en 87 suites, `clean` incluido. `assembleDebug` (57.102.001 bytes, `19e7471f…`) y `assembleDebugAndroidTest` (655.982 bytes, `8ab2d4d1…`) salen **byte a byte idénticos** a los de `30d84c0`, que es la comprobación de que subir Robolectric no toca el artefacto que se envía. Hashes en `work/ci/cierre-android16-sha256.txt`; la sonda de niveles, los tamaños de los jars y lo que no se pudo hacer, en `work/ci/cierre-android16-01-resumen.txt`.
- Red real del catálogo `work/ci/catalogo-red-01-resumen.txt`: las 25 fichas auditadas repasadas contra `api.github.com`, opt-in con `CEROX_LIVE_CATALOGO=1`. Sin credenciales y sin aparato.
- Enriquecimiento del catálogo con datos de release: **medido y sin empalmar.** La API de Miyomi (`https://miyomi.app/api/apps`) da versión, fecha de última release, número de descargas y `forkOf`, que es un dato que ninguna otra fuente consultada ofrece. Casan **25 de las 52 fichas por URL exacta** (16 por `repoUrl`, 9 por `websiteUrl`); las 27 restantes quedan `SIN_VERIFICAR` y no se rellenan por parecido de nombre. La instantánea con las 25 entradas, el método de casamiento, las trampas encontradas y los casos límite está en `work/ci/catalogo-releases-miyomi-01.kotlin.txt`. No necesita aparato ni credenciales: necesita declarar `DatosRelease`, añadir la línea de release a `item_catalog_app.xml` y pasar el cierre de la regla 9.
- Red real ejecutada sin credenciales: Safebooru JSON+miniatura, **descarga completa de 5.411.582 bytes** y **auditoría de las 25 licencias del catálogo contra la API del alojamiento** (que encontró 3 licencias falsas y 4 enlaces muertos). Sin aparato y sin credencial IA: MangaDex DEX, recorrido físico del visor y proveedor IA autenticado.
- Validación de la fusión con `main` y de las herramientas `work/ci/fusion-main-cierre-01.log.gz`: **614 pruebas, 0 fallos, 0 errores, 14 omitidas** en 88 suites, `clean` incluido. `assembleDebug` 57.102.093 bytes (`581b2de7…`, idéntico al del commit de fusión) y `assembleDebugAndroidTest` 655.982 bytes (`8ab2d4d1…`).
- Red real separada: directorios cinco orígenes con datos y AniPirate 403; Safebooru JSON+JPEG; Kotatsu índice. IA autenticada y Android físico no ejecutados por falta de credencial/aparato. Las descargas del visor se prueban sin red, con respuestas fingidas: una descarga real a Safebooru sólo se hace en el aparato.

## Regla aprendida en esta fase

Una recuperación de workspace puede eliminar también JDK/SDK y procesos. Comprobar herramientas, presupuesto y resultados; no basta con que los fuentes sigan presentes. La receta y la habilidad persistente están en `AGENTS.md` y `.agents/skills/cerox-fases-verificadas/SKILL.md`.

## Entorno para trabajo en paralelo: caché de build y concurrencia medidas (2026-11-13)

- Base: rama `main`, HEAD `2dc023e`. Banco `tools/medir-build.py`, histórico `work/ci/paralelismo.tsv` (16 corridas, todas con el mismo cierre completo `clean :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`). Detalle y porqués en `docs/PARALELISMO.md`.
- Máquina medida: 2 CPU, 1.983 MB de RAM + 11.264 MB de swap, sin `/dev/kvm`, `GRADLE_USER_HOME=/opt/cerox/gradle`. Un demonio de Gradle ocioso ya retiene ~714 MB de RSS.
- **Decisión 1 — caché de build activada** (`org.gradle.caching=true`): recorta el cierre de 225,0 s a 136,7 s (−39 %) con los mismos 614/0/14 y los mismos hashes de APK.
- **Decisión 2 — paralelismo de tareas rechazado**: `--parallel --max-workers=2 -PceroxForks=2` costó 237,7 s, *más* que la base. `org.gradle.parallel` es placebo con un solo subproyecto (`:app`; `comun/` entra por `java.srcDirs`), y dos forks de Robolectric no caben en 1.983 MB cuando un solo cierre deja `MemAvailable` en 29 MB.
- **Decisión 3 — las pruebas fuera de la caché** (`outputs.cacheIf { false }` en `tasks.withType(Test)`). Es la que costó el hallazgo: con la caché activada, un `clean` + suite dio `> Task :app:testDebugUnitTest FROM-CACHE` en 9,6 s e imprimió «614 pruebas, 0 fallos» **sin ejecutar una sola prueba** —la caché restauró los XML, y los XML restaurados tienen `mtime` nuevo, así que la comprobación de edad del banco no lo cazó. Es el verde hueco de la regla 18 con otra cara. Con el arreglo, el cierre completo honesto cuesta **46,2 s** (`pruebas_ejecutadas: true`, XML de 7 s), no 9,6 s.
- Contraparte medida sobre el commit real: repetir el cierre que antes daba 9,6 s hueco ahora ejecuta las pruebas —`contraparte-cache-pruebas` 52,2 s, `pruebas_ejecutadas: true`, XML de 9 s, `MemAvailable` 54 MB (el fork arrancó), mismos hashes de APK. El detector de `medir-build.py` se validó contra 11 corridas de veredicto conocido: 11/11.
- Concurrencia de dos worktrees con las pruebas ejecutándose: los dos a la vez 113 s de pared (`conc3-A` 111,6 / `conc3-B` 84,8), los mismos dos **en serie** 78 s (`serie-A` 38,4 / `serie-B` 37,6). En esta máquina dos builds pesados simultáneos son más lentos que en serie y cuestan 685 MB de disco. Worktrees limpiados tras medir.
- Herramienta nueva `tools/comprobacion-paralela.py`: 9 comprobaciones baratas en hilos, 0,4 s con 2 hilos frente a 0,8 s en serie. Dos defectos propios corregidos al estrenarla: el regex de SPDX no admitía el prefijo de comentario (anunciaba «20 de 20 sin SPDX» sobre ficheros que lo llevan) y el cronómetro estaba fuera del hilo (imprimía 0,0 s). Ejecutada: EXIT=1 con sólo `latido` en rojo por árbol sucio, que es correcto.
- `scripts/rearmar-git.sh`: rearme idempotente de la capa de Git (regla 11). `git status --porcelain` **no** muestra cambios de modo, sólo `git diff --summary`; por eso el bucle manual del turno anterior no arregló los 6 bits de ejecución perdidos y este script sí. **Precisión del 2026-11-13, medida en edt:** `--porcelain` sí *lista* los ficheros cuyo modo cambió (aparecen como ` M`), lo que no dice es *que* sea un cambio de modo; y `git diff --numstat` los reporta con `0 0`, así que «16 ficheros modificados» puede ser cero contenido. Quién lo dice es `git diff --summary` (`mode change 100755 => 100644`). La conclusión práctica no cambia —sin `--summary` se puede commitear modos por accidente— pero la afirmación tal como estaba redactada llevaba a descartar un árbol que sí estaba sucio.
- Prueba de estrés arreglada a mejor-de-3 (`StressSuggestionEngineTest`): era intermitente por medir una sola vez. Bajo dos procesos quemando las 2 CPUs dio `[1002, 837, 703]` ms —la medición única habría fallado por 2 ms— y pasa en 703 ms. Una aserción de tiempo de pared mide la máquina, no el código (regla 6 ampliada).
- Reglas ampliadas en `AGENTS.md` **en el sitio, sin renumerar** (hay 27 citas «regla N» que la comprobación `reglas` caza si quedan colgando): la 18 con la cara `FROM-CACHE` del verde hueco y la 6 con la aserción de tiempo de pared.
- Cierre de fase sobre el HEAD empujado `work/ci/paralelismo.tsv` filas `cierre-doc-paralelismo` y `contraparte-cache-pruebas`: **88 suites, 614 pruebas, 0 fallos, 14 omitidas**, `assembleDebug` 57.102.093 B `581b2de7…` y `assembleDebugAndroidTest` 655.982 B `8ab2d4d1…`, byte a byte idénticos a los de la fusión. CI verde por SHA: corrida `43437675315` sobre `2dc023e`, `completed`/`success`.
- Espejo en `entorno-de-herramientas-de-trabajo` repuntado a `main` (apuntaba a la rama congelada) y 4 fallas nuevas en sus registros (C7b, C8, C9 comunes; 17-18 de cero-X); CI de edt verde por SHA, corrida `43443313382` sobre `c4d5e91`, `completed`/`success`.

## Objetivo todo-en-uno anotado, referencias bajadas y primer tramo de auditoría (2026-11-13)

- Base: rama `main`, HEAD `d641487` al empezar. El dueño declaró el objetivo —un todo-en-uno construido sobre 13 proyectos del ecosistema, eligiendo lo mejor de cada uno y evolucionándolo, empezando por ingeniería inversa— y pidió dos cosas: la mejor ruta y que el objetivo quedara anotado. Hasta ahora vivía sólo en la conversación, contra la regla 14.
- `docs/OBJETIVO-TODO-EN-UNO.md` (247 líneas): el objetivo en las palabras del dueño al principio, el censo de las 13 fuentes medido por API, qué se puede copiar según la licencia de cada una, la ruta en cinco fases con criterio de salida, y lo que no se puede hacer desde aquí. Commit `79b084c`.
- El censo cambió dos cosas que la lista del dueño daba por supuestas. `fmhyplay/android` movió a `AnantPlay/android` y `skepsun/Kototoro` a `Kototoro-app/Kototoro` (la API respondió `Moved Permanently`; sin `-L` se pierde el repo). Y `playtorrio.xyz` **no tiene fuente pública**: el sitio vive (HTTP 200, 57.036 bytes) pero el repo que enlaza su portada, `ayman708-UX/PlayTorrioV3`, da 404 y ese usuario tiene 0 repos públicos; los 15 resultados de buscar «playtorrio» son copias de terceros. Se anota como «no hay nada que auditar» en vez de inventarle un origen.
- Licencia medida y no supuesta: 11 de las 12 fuentes con repo son copiables a GPL-3.0 (Apache-2.0 y MIT son compatibles, conservando `NOTICE`). **Kiroku y su padre ReDantotsu declaran `NOASSERTION`** porque usan una «Unabandon Public License» que incorpora GPLv3 y añade la obligación de publicar el fuente de cualquier obra derivada; se leyó el `LICENSE.md` entero (1.434 bytes). cero-X la cumple de hecho, pero no es SPDX estándar y combinar código bajo una licencia personalizada es decisión del dueño: mientras no la tome, Kiroku es referencia de producto y **no se clona**.
- Las cuatro fuentes Dart (`mangayomi` 4.475★, `AnymeX` 1.283★, `watchtower`, `Foxlations`) no se portan: se estudian. Portar Dart a Kotlin sin poder ejecutar el original para comparar produce una versión peor que se cree equivalente. De ellas se toman contratos y formatos, que son independientes del lenguaje —el de backup de Mangayomi y AnymeX ya lo detecta `backup_import/BackupFormatDetector.kt`.
- **Fase F1 cerrada.** `scripts/bajar-referencias.sh` (nuevo, idempotente, con margen mínimo de 800 MB de disco) bajó las 7 fuentes Kotlin/Java a `/var/tmp/referencias/`: `REFERENCIAS_OK`, 7 listas y 0 fallidas, **746 MB** y 24.338 ficheros. Evidencia con SHA de cada clon en `work/ci/frankie/referencias.tsv`. Van fuera del workspace por medida: Kototoro clonado dentro de `work/` llevó el presupuesto a **9.049 ficheros y 367,90 MiB** y rompió el margen operativo de 8.500/110 MiB; sacarlo lo devolvió a 2.246 y 38,86 MiB. Los 2,03 GB que reporta la API son con historial completo, que `--depth 1` no trae.
- **Segundo rearme del entorno en el mismo día, y más grave que el primero.** A mitad de turno desaparecieron `/opt/cerox` (Gradle y m2), el SDK de Android, el JDK 21 (quedó 11 en `/usr/bin/java`), el swap (`/proc/swaps` vacío), el remoto de Git y los bits de ejecución de **26 ficheros versionados: 10 en este repo y 16 en edt**. El disco libre saltó de 5,7 GB a 20 GB, que fue la pista de que algo se había borrado. `latido` lo cazó: `HEARTBEAT_FALLIDO` con 4 avisos, cada uno con su arreglo.
  - `scripts/rearmar-git.sh` hizo 4 pasos y **se detuvo en el quinto**: encontró 32 URLs de GitHub distintas en los docs y se negó a elegir una sola, que es para lo que se escribió. Pasándole la URL explícita dio `REARME_OK` (remoto, `credential.helper`, identidad, `fetch --tags`, upstream) y devolvió `HEAD = origin/main = d641487`.
  - En edt los 16 cambios eran puros cambios de modo: `git diff --numstat` dio 0 líneas añadidas y 0 borradas. Se restauraron con `chmod +x` y el árbol quedó limpio **sin commitear modos**.
  - `scripts/preparar-entorno-android.sh` devolvió JDK 21.0.13, build-tools 35/36, platform 36, platform-tools y swap de 11 GiB; `ENTORNO_OK`. **Coste medido: el disco libre pasó de 20 GB a 7,8 GB.** Ese es el techo real de la ruta.
- **Primer tramo de la fase F2.** `tools/auditar-referencias.py` (nueva) compara los `AndroidManifest.xml` de las 7 referencias con el de cero-X sin necesitar aparato ni compilar. Evidencia en `work/ci/frankie/auditoria-manifiestos.tsv`. Dos hallazgos que la ruta tiene que resolver antes de meter más funciones:
  - cero-X declara **11 permisos sensibles, tantos como la que más** (AniZen), y tres que ninguna otra pide: `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`. Seis de las siete piden `MANAGE_EXTERNAL_STORAGE` y `RECEIVE_BOOT_COMPLETED`, que cero-X no pide.
  - cero-X es **un solo manifiesto con 16 componentes y 3 exportados**; las referencias tienen entre 2 y 16 manifiestos y hasta 96 componentes (Kototoro) con 20-21 exportados (Kototoro, Miyorare). La modularización no es adorno: es lo que permite que una capacidad falle sin arrastrar a las otras.
- Sin cierre Gradle en estos commits: no tocan ninguna fuente Android (dos docs, un script de bash y una herramienta Python), así que no aplica la regla 9. Las comprobaciones baratas sí se ejecutaron: `reglas`, `spdx` (23 ficheros GPL-3.0-only) y `sin-secretos` en verde, y `latido` en `HEARTBEAT_OK` tras el empuje de `79b084c`.

## Cabo ④ cerrado, tres reinicios recuperados y F3 abierta (2026-11-14)

- **Cabo ④ (PlayTorrio) cerrado.** PlayTorrio no tiene fuente pública; el dueño autorizó
  sustituirlo por otra referencia de código de dos listas que aportó. Censadas por API, no por la
  descripción: Miyomi y Wotaku son TypeScript de sitio web (Wotaku además NOASSERTION), AniPirate
  es un índice de datos sin código, y sólo `omeg4-dev/fmhy-android` (GPL-3.0) y
  `komi-store/komi-store` (Apache-2.0) tienen código Android real. Decisión: **komi-store** —
  «app store for developers' releases on GitHub, Codeberg & Forgejo», antes «GitHub Store»,
  Kotlin Multiplatform + Compose con `build-logic`, ★20.656, 100 commits en 30 días, CI verde,
  v1.9.4—. Clonado: 108 MB, 1.818 ficheros, `19eb744`. Su ficha de F2: **0 permisos huérfanos y
  0 telemetría** (0 SDK en 49 ficheros de build), justo la higiene que a cero-X le falta (8
  huérfanos). Censo: **12 referencias + cero-X = 13 objetivos**. Commit `1dde9d5`; espejo de
  `bajar-referencias.sh` republicado en edt `c2b1b44`, `comprobar-copias.sh --estricto-origen`
  en `COPIAS_OK` (8 ficheros fieles a `1dde9d5`).
- **Tres reinicios del sandbox en el mismo turno, recuperados.** Cada uno perdió `.git/config` de
  los DOS repos (remoto, helper, identidad, upstream), los bits de ejecución de los ficheros
  versionados (13 y 18), el modo 0600 de `~/.cerox-keys/tokens.txt` (lo dejó en 644, legible), el
  toolchain (JDK 21 → 11, SDK ausente), el swap y `/var/tmp/referencias` (los 12 clones). La pista
  fue el disco libre subiendo de golpe a 20 GB. Se rearma con **`scripts/recuperar-entorno.sh`**
  (habilidad nueva): UNA orden que rearma Git en los dos repos —URL limpia, **nunca el token
  dentro**—, restaura modos y `tokens.txt` a 0600, rearma toolchain y referencias y verifica con
  `git ls-remote` real. Probada de verdad bajo un reinicio real: `RECUPERADO_OK`, 9 comprobaciones
  en verde, `ENTORNO_OK`, `BUILD SUCCESSFUL` local (`:app:compileDebugKotlin`, 3m13s, 19 tareas) y
  `HEARTBEAT_OK`. Commits `74c4617` (herramienta + parche a `rearmar-git.sh`: el helper se
  contrasta con `cmp` y se reescribe canónico si deriva o está en 644, la causa del 401 histórico)
  y `316c520` (paso 3b: restaura también las referencias).
- **Falla C13 registrada** (edt `cd7c834`, CI success): el rearme manual del turno anterior
  incrustó el token en la URL del remoto, una fuga en un repo público; `latido` (`comprobar_remoto`)
  lo cazó como BLOQUEO antes del push. Contraparte: `recuperar-entorno.sh` + el parche `cmp` de
  `rearmar-git.sh`. Es la contraparte operativa de C11.
- **F3 abierta, paso 1.** `docs/ASISTENTE-Y-COMPETENCIA.md` extendido de 2 a 6 referencias: las
  cuatro fuentes Dart censadas por la estructura de su `lib/` (mangayomi: motor `eval` dart-eval +
  Isar + FFI a Go/Rust + 5 sistemas; AnymeX: trackers multi-servicio AL/MAL/Simkl + módulo `ai` +
  web; watchtower: `remote`/`local_indexer`/`cli`/`deployment` = servidor autoalojado; Foxlations:
  `l10n` + `presentation` ligera). Tres brechas concretas anotadas, en orden de utilidad:
  ① servidor autoalojado e índice local (decidir alcance), ② trackers unificados (refactor F5),
  ③ motor de extensiones (endurecer `extension_factory`, que F2 marcó con 4 fuentes y 0 pruebas).
  El cabo ⑤ queda leído y absorbido.
- **Bloqueos:** sin aparato (`adb devices` vacío, sin `/dev/kvm`) y sin toolchain de Flutter, así
  que las Dart se leen por API, no se compilan ni se portan; el servidor autoalojado, los
  recorridos visuales y la ejecución Android no se prueban aquí. El entorno cuesta ~12 GB y se
  pierde en cada reinicio: hay 7-8 GB libres tras rearmar, y `recuperar-entorno.sh` es la orden
  única para volver a estar al 110 %.
- **Siguiente:** F3 paso 2 —censar las 8 fuentes Kotlin/Java (komi-store incluida) por capacidad y
  completar la matriz de brechas de las 12— y luego F4 (los cuatro módulos sin pruebas:
  `backup_import`, `theme`, `extension_factory`, `updates`).
