# Estado secuencial del proyecto

Actualizado: 2026-11-12. Este archivo registra trabajo pendiente y evidencia; no sustituye los resultados XML ni los APK.

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

1. **Motor adaptativo:** perfiles por sesión/categoría, señales locales, recomendaciones explicables, deshacer/restablecer y bloqueo de cambios durante lectura/escritura. No llamarlo IA generativa si son reglas/heurísticas.
2. **Rediseño Views y Booru:** terminar lenguaje visual/accesibilidad y construir un cliente real antes de devolver Booru a navegación.
3. **Pendientes físicos y externos:** Kotatsu, LG V60, pruebas visuales y proveedor IA autenticado. Declarar bloqueo si no hay aparato/credencial.

## Regla aprendida en esta fase

Una recuperación de workspace puede eliminar también JDK/SDK y procesos. Comprobar herramientas, presupuesto y resultados; no basta con que los fuentes sigan presentes. La receta y la habilidad persistente están en `AGENTS.md` y `.agents/skills/cerox-fases-verificadas/SKILL.md`.
