---
name: cerox-fases-verificadas
description: Usar al continuar cero-X, cerrar una parte grande o recuperar una sesión interrumpida. Evita pérdida de estado, workspaces sobredimensionados y funciones simuladas.
---

# Fases verificadas de cero-X

1. Leer `AGENTS.md`, `docs/FASES.md`, `git status --short` y el último commit. No repetir una acción cuyo log ya tenga resultado.
2. Ejecutar `python3 tools/comprobar_entorno.py`. Si faltan herramientas, usar `scripts/preparar-entorno-android.sh`; no iniciar una VM ni descargar modelos para una tarea que no los necesita.
3. Elegir **una sola fase pendiente** y escribir su criterio de salida antes de editar. Conservar cambios ajenos y no usar reset/checkout sobre ellos.
4. Implementar un recorrido completo: entrada → acción real → estados → persistencia si corresponde → salida. Añadir pruebas que fallen si la acción vuelve a ser un cascarón.
5. Ejecutar primero pruebas dirigidas y después el cierre Android indicado en `AGENTS.md`. Revisar los XML de resultados: «BUILD SUCCESSFUL» no sustituye el número de pruebas/fallos/omisiones.
6. Si una prueba necesita dispositivo, red o credenciales, separarla con opt-in y registrar exactamente qué llegó a ejecutarse. Nunca convertir una omisión en validación.
7. Repetir la auditoría de entorno, eliminar artefactos regenerables del workspace y guardar hashes/logs pequeños cuando sean evidencia.
8. Actualizar `docs/FASES.md` con resultado y bloqueos. Añadir a `AGENTS.md` o a esta habilidad cualquier regla reutilizable aprendida.
9. Revisar `git diff --check` y `git diff --stat`, y hacer un commit acotado sólo cuando la fase esté verde. No publicar ni prometer validación física sin evidencia.
