# Estado secuencial del proyecto

Actualizado: 2026-11-13. Este archivo registra trabajo pendiente y evidencia; no sustituye los resultados XML ni los APK.

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

## Cierre acumulado de esta continuación

- Commits: navegación `3407ebb`; directorios `985ec17`; motor adaptativo `4b75d0f`; Booru `7a05c0b`; Views `9f53201`; pendientes externos `e3260b0`; dashboard `81915cf`; visor de imagen `3b9e7f4`; descarga real y recorrido físico `566a4c5`; auditoría del catálogo `30d84c0`; latido y reglas, al cerrar esta fase.
- Validación previa `work/ci/cierre-final-01.log.gz`: **559 pruebas, 0 fallos, 0 errores, 12 omitidas**, `assembleDebug` y `assembleDebugAndroidTest`.
- Validación con dashboard `work/ci/dashboard-cierre-01.log.gz`: **563 pruebas, 0 fallos, 0 errores, 12 omitidas**, ambos APK.
- Línea base tras rearmar el entorno `work/ci/base-post-rearmado-01.log.gz`: **563/0/0/12** y los mismos SHA-256 que antes del reinicio.
- Validación con el visor `work/ci/visor-cierre-limpio-02.log.gz`: **593 pruebas, 0 fallos, 0 errores, 12 omitidas**, `assembleDebug` (57.082.325 bytes, `3c509e47…`) y `assembleDebugAndroidTest` (634.852 bytes, `6b02dceb…`).
- Validación con la descarga real opt-in y el recorrido físico `work/ci/visor-fisico-cierre-01.log.gz`: **594 pruebas, 0 fallos, 0 errores, 13 omitidas**, `assembleDebug` (57.082.373 bytes, `d8dd01f7…`) y `assembleDebugAndroidTest` (655.982 bytes, `8ab2d4d1…`).
- Validación con la auditoría del catálogo `work/ci/catalogo-cierre-01.log.gz`: **608 pruebas, 0 fallos, 0 errores, 14 omitidas** en 86 suites, `assembleDebug` (57.102.001 bytes, `19e7471f…`) y `assembleDebugAndroidTest` (655.982 bytes, `8ab2d4d1…`, idéntico al anterior porque ninguna fuente instrumentada cambió). El catálogo añade 19.628 bytes en limpio.
- Red real del catálogo `work/ci/catalogo-red-01-resumen.txt`: las 25 fichas auditadas repasadas contra `api.github.com`, opt-in con `CEROX_LIVE_CATALOGO=1`. Sin credenciales y sin aparato.
- Red real ejecutada sin credenciales: Safebooru JSON+miniatura, **descarga completa de 5.411.582 bytes** y **auditoría de las 25 licencias del catálogo contra la API del alojamiento** (que encontró 3 licencias falsas y 4 enlaces muertos). Sin aparato y sin credencial IA: MangaDex DEX, recorrido físico del visor y proveedor IA autenticado.
- Red real separada: directorios cinco orígenes con datos y AniPirate 403; Safebooru JSON+JPEG; Kotatsu índice. IA autenticada y Android físico no ejecutados por falta de credencial/aparato. Las descargas del visor se prueban sin red, con respuestas fingidas: una descarga real a Safebooru sólo se hace en el aparato.

## Regla aprendida en esta fase

Una recuperación de workspace puede eliminar también JDK/SDK y procesos. Comprobar herramientas, presupuesto y resultados; no basta con que los fuentes sigan presentes. La receta y la habilidad persistente están en `AGENTS.md` y `.agents/skills/cerox-fases-verificadas/SKILL.md`.
