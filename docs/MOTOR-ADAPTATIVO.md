# Motor adaptativo local por categoría y sesión

Ubicación: **Fuentes → Directorios**. Implementación: `modules/adaptive` y `DirectoryScreen.kt`.

## Qué es y qué no es

Es un motor **heurístico, determinista y explicable** de diseño y orden. No es IA generativa, no inventa metadatos, no evalúa seguridad y no llama a un proveedor externo. «Afinidad» significa acciones locales previas; no una recomendación editorial ni una garantía.

## Señales permitidas

Sólo tipo de acción (`CATEGORIA`, `ORIGEN`, `FICHA`, `ENLACE`, `SINCRONIZAR`), categoría, origen, identificador aleatorio de sesión e instante. No se registran consultas, nombres de obras, URLs, tokens, contenido leído ni credenciales.

- Máximo 400 eventos, conservando los más recientes.
- JSON en `AlmacenExterno/adaptacion/eventos.json`, escritura temporal+renombrado.
- La sesión usa `rememberSaveable` y sobrevive a rotación; no es una cuenta.
- Desactivar impide nuevas señales y vuelve al diseño base. Deshacer elimina la última señal de esta sesión. Restablecer borra el historial adaptativo, no las cachés de directorios.

## Diseño por categoría

| Categoría | Base |
|---|---|
| Manga / Novelas | Cómoda, 5 líneas de contexto |
| Guías | Cómoda, 6 líneas |
| Apps / Anime / Herramientas | Compacta, 3 líneas |
| Datos | Compacta, 2 líneas |
| Resto / Todas | Cómoda, 4 líneas |

Cuatro aperturas en la sesión actual reducen a 2 líneas. Dos consultas de ficha, si superan aperturas directas, aumentan a 6 para dar contexto. Las plataformas declaradas siguen visibles cuando existen.

## Sesión y orden

La sesión actual pesa más: enlace 4, ficha 3, selección de origen 2; historial anterior 1; sincronización 1. Se priorizan hasta cuatro orígenes. La búsqueda aporta puntos explicables por nombre exacto/prefijo/coincidencia. Los empates conservan el orden original.

Cada tarjeta puede mostrar «Por qué», incluyendo coincidencia y puntos de afinidad. No hay una cifra de «seguridad» ni una probabilidad ficticia.

## Sin cambios mientras se lee

Las acciones crean **señales pendientes**, pero el perfil no se recalcula por cada apertura. El orden y la densidad permanecen estables hasta pulsar **Aplicar**. Cambiar de categoría, deshacer, restablecer o activar/desactivar también es una acción deliberada que recalcula.

## Pruebas

- Base y desactivación.
- Mayor peso de sesión y separación por categoría.
- Densidad por aperturas/fichas y bases por categoría.
- Explicación, puntuación y empates estables.
- Persistencia externa, límite de 400, deshacer/restablecer y desactivar.
- Guarda de pantalla para aplicación manual, sesión guardable y ausencia de consulta en eventos.

Validación JVM no certifica apariencia ni interacción en el LG V60; la revisión visual física sigue pendiente.
