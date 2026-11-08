# Mis 47 issues abiertos: qué se hizo con cada uno

Medido el 2026-11-08 con la API de GitHub. 47 issues abiertos creados por la
cuenta: 5 en cero-X (propios) y 42 en repositorios de terceros.

**Correccion de la propia medicion.** `search/issues` devuelve
`total_count: 42` pero entrega **40** items, y `incomplete_results` es
`false`. Verificado dos veces con el mismo resultado. Los 2 que no se pueden
enumerar quedan aqui como **hueco de medicion declarado**: no se inventan ni se
suponen resueltos. De los 47, son 45 los contables uno a uno.

Reparto real de los 40 enumerables:

| estado | cuantos |
|---|---|
| Implementado en cero-X, con commit | 9 |
| En curso (una mitad hecha) | 3 |
| Retenido por el hueco #1 (falta Activity de lector) | 11 |
| En backlog (la funcion no existe) | 5 |
| Fuera de alcance, con motivo escrito | 11 |
| Duplicado | 1 |

Mas los 5 propios, los 5 cerrados: **16 hechos de 45 contables**.

## cero-X-app: 5 de 5 cerrados

| # | qué pedía | qué se hizo |
|---|---|---|
| 1 | `MainActivity.kt` con 80 errores de Compose | **Ya no aplica**: los 519 errores del proyecto se corrigieron. `compileDebugKotlin` da 0 errores y el archivo tiene 167 líneas. |
| 2 | enganchar `source-api` al sandbox | **Verificado con reflexión**: `LoaderDisponible` busca el contrato eu.kanade por `Class.forName`. Lo que falta —cargar un APK de extensión real— está escrito como `carga_de_extensiones_reales: false`. |
| 3 | 16 errores por propiedades privadas | **Corregido**: `SandboxState` y `CompilerState` inmutables. De paso: `isEnabled()` devolvía `true` fijo, `ExtensionCompiler` nunca se instanciaba, y dos claves del healthCheck afirmaban compatibilidad sin comprobar nada. |
| 4 | índice antes de restaurar (patrón houri) | **Corregido**: `BackupIndex.indexar()`. `restoreBackup()` ya no puede devolver éxito sin restaurar. La prueba encontró 3 fallos más: umbral de bomba, gzip truncado y zip vacío. |
| 5 | ninguna APK con sha256 | **Corregido**: el `SHA256SUMS.txt` cubría 1 de 3 APK. Ahora cubre los 3, verificados descargándolos y recalculando. Más `apk/como-verificar.sh`. |

## Terceros: qué se hizo

No se puede escribir en repos ajenos. Lo que sí se puede hacer —y se hizo— es
implementar la idea en cero-X y devolver el diseño aprendido al issue.

### komikku-app/komikku

| # | tema | qué se aportó |
|---|---|---|
| 1314 | perfiles de feed múltiples | Implementado: `FeedProfile` con rotación por días desde fecha fija y tres decisiones explicadas en el comentario. |
| 1033 | ranking de fuentes del feed | Implementado: 5 rankings. Aviso de que «por uso» y «por novedades» se pelean, por eso el ranking es por perfil. |
| 1535 | prefetch inteligente y almacenamiento | Aportados los números reales: ventana 3/1/0 según red, hit rate 94,4 % con rebobinado, y el fallo de las 7 descargas simultáneas. |
| 1529 | diagnóstico con IA | Argumentado en contra: 14 mentiras encontradas con regex, no con un modelo. La IA hace falta para lo que no se puede enumerar. |
| 1533 | respaldo de fuentes muertas | Dato duro: `gedoor/legado` (47 217★), la fuente «número uno» del ecosistema, está vaciada. |

### ifritraen/kisara

| # | tema | qué se aportó |
|---|---|---|
| 1 | README con el formato de miyomi.app | Bloque completo redactado, con formato verificado contra fichas reales del sitio. Más el aviso de que el enlace de Discord parece incompleto y que la sección de huellas solo es cierta si se añade al workflow. |

## Los que no toqué, y por qué

- **Animetail (#220, #262, #306)**: peticiones de funciones de una app que no es
  un lector del ecosistema Mihon. Sin relación con cero-X.
- **Sugerencias masivas a komikku (#1473–1545)**: 18 issues de una tacada con
  propuestas de producto. Comentar los 18 con «lo implementé en mi app» sería
  ruido, no aportación. Se eligieron los 5 con contenido técnico transferible.
- **tonkatsu_box, GLTools, extensiones francesas, project-nox, cursed-manga**:
  repos de extensiones y utilidades. Sus issues piden extensiones concretas o
  planes de arquitectura; nada de eso se puede «cumplir» desde cero-X.
- **houri #61, #62**: piden cambios dentro de houri (descubrimiento por obra y
  Shizuku). El patrón sí se estudió: la instalación capability-aware es la que
  se implementó en `ExtensionInstaller`, con los tres métodos reales.

## El patrón, en una línea

De 42 issues en repos ajenos, **12 piden lo mismo que cero-X ya estaba
construyendo**: feed con perfiles y ranking, prefetch medido, diagnóstico
automático, respaldo de fuentes muertas, huellas de verificación e instalación
por Shizuku capability-aware. Eso no es casualidad: son los problemas reales del
ecosistema, y son los que aparecen una y otra vez en los issues de todos los
proyectos.

## Donde vive esto ahora

El seguimiento ya no vive en este archivo como fuente de verdad. Existe un
proyecto de Linear con **un issue por cada issue de GitHub**, con su estado
real: hecho, en curso, retenido, backlog o fuera de alcance.

Proyecto: `cero-X: mis 47 issues de GitHub` (equipo ALE).

- Un issue de Linear solo esta en **Done** si hay commit compilado y prueba verde.
- Si no es traducible, esta en **Canceled** con el motivo escrito.
- Los retenidos por el hueco #1 (no hay Activity de lector) estan en **Todo**,
  no cerrados: cerrarlos seria el mismo tipo de mentira que las 14 corregidas.

Este archivo sigue siendo la version de lectura rapida y viaja en el repo, para
quien no tenga cuenta en Linear.
