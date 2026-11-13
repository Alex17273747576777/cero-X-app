# Paralelismo y caché de build: lo que se midió y lo que se decidió

Este documento existe porque dos ficheros lo citan —`android/app/build.gradle` (la línea
de `maxParallelForks`) y `android/gradle.properties` (el bloque de `org.gradle.caching`)—
y porque las decisiones que esos ficheros aplican se tomaron midiendo, no suponiendo.
Aquí están las cifras, la máquina donde se tomaron y los dos errores propios que salieron
por el camino. Todo lo numérico viene de `tools/medir-build.py` sobre el histórico
`work/ci/paralelismo.tsv` (14 corridas, todas con el mismo cierre completo).

## La máquina (medida, no la de un runner de CI)

```
CPU          2 × Intel Xeon 2,60 GHz   (sin virtualización por hardware, sin /dev/kvm)
RAM          1.983 MB MemTotal
swap         /swapfile-cerox 11.264 MB, activo
disco /      25 GB, ~5,7 GB libres durante las pruebas (77 % usado)
GRADLE_USER_HOME  /opt/cerox/gradle (2,0 GB; caché de build caches/build-cache-1 ≈ 32 MB)
JDK          21.0.13
```

Un demonio de Gradle **ocioso** ya retiene ~714 MB de RSS. Esto importa: cuando se habla
de «pico de RSS java 1.748 MB» en un cierre, ~700 MB de eso es el demonio que ya estaba
ahí, no el trabajo nuevo.

## El cierre completo que se midió siempre

```
./gradlew -I ../tools/low-memory.gradle --console=plain \
    clean :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

`clean` delante a propósito: sin él, la segunda corrida no hace nada y mide la nada. El
mismo comando en todas las variantes, sobre el mismo commit, es lo único que hace
comparables los tiempos.

## Los resultados

Todos los verdes comparten **88 suites, 614 pruebas, 0 fallos, 14 omitidas** y los **mismos
hashes de los dos APK**:

- `app-debug.apk` — 57.102.093 B — `581b2de7ee2e3d6fbabf62dbd1f94f2a95af4ca07df0cdea81403ca10a731172`
- `app-debug-androidTest.apk` — 655.982 B — `8ab2d4d1198d27a285dcf8ee0768b9bc9e3678656a89b7199f799e9808954e35`

Que el producto no cambie un byte entre la base de 512 s y el cierre de 46 s es la prueba
de que reutilizar salidas no altera lo que se entrega.

| variante | s | exit | proc. java | MemAvail mín | pruebas ejecutadas | nota |
|---|---|---|---|---|---|---|
| `base-frio` | 512,6 | 0 | – | 33 MB | sí | descarga de dependencias, **no es línea base** |
| `base-caliente` | 225,0 | 0 | – | 29 MB | sí | **línea base** con caché apagada |
| `cache-fria` | 162,4 | 1 | – | 35 MB | sí | 1 fallo: la prueba de estrés (ver abajo) |
| `cache-caliente` | 136,7 | 0 | – | 30 MB | sí | caché de build, −39 % sobre la base |
| `forks2-workers2` | 237,7 | 1 | – | 30 MB | sí | 2 forks + 2 workers + `--parallel`: **más lento** |
| `cierre-config-elegida` | 95,9 | 0 | – | 32 MB | sí | caché en `gradle.properties`, antes del `cacheIf` |
| `conc-A` / `conc-B` | 136,2 / 122,7 | 0/0 | – | 44 / 49 MB | sí | dos worktrees a la vez (antes del `cacheIf`) |
| `individual-repetido` | 9,6 | 0 | 2 | 548 MB | **NO** | ⚠ pruebas servidas de caché; **no vale como tiempo** |
| `cierre-sin-cache-de-pruebas` | 46,2 | 0 | 3 | 58 MB | sí | con `outputs.cacheIf { false }`: **el número honesto** |
| `conc3-A` / `conc3-B` | 111,6 / 84,8 | 0/0 | 6 | 57 MB | sí | dos worktrees a la vez, pruebas fuera de caché |
| `serie-A` / `serie-B` | 38,4 / 37,6 | 0/0 | 3 | 51 / 66 MB | sí | los mismos dos worktrees, **en serie** |

## Decisión 1: la caché de build se queda activada

`org.gradle.caching=true` en `gradle.properties` recorta el cierre de **225,0 s a 136,7 s**
(−39 %) con los mismos 614/0/14 y los mismos hashes de APK. Es ganancia real y gratuita:
reutiliza compilación, recursos y dex de corridas anteriores.

Pero hay una trampa, y es la razón de la Decisión 3.

## Decisión 2: el paralelismo de tareas NO se activa

Medido, no supuesto. Con `--parallel --max-workers=2 -PceroxForks=2` el cierre costó
**237,7 s**, *más* que la base de 225,0 s. Dos motivos, los dos estructurales aquí:

1. **`org.gradle.parallel` es placebo en este repo.** `android/settings.gradle` incluye un
   solo subproyecto (`:app`); el núcleo compartido `comun/` entra por `java.srcDirs` en
   `build.gradle`, no como módulo aparte. Gradle paraleliza *entre* subproyectos, y aquí
   no hay más que uno. La bandera no tiene nada que paralelizar.
2. **Dos forks de Robolectric no caben.** Cada fork pide su propio heap (384 m vía
   `tools/low-memory.gradle`) sobre los `-Xmx2048m` del demonio. Con 1.983 MB de RAM y un
   solo cierre dejando `MemAvailable` en 29 MB, dos forks compiten por memoria que no
   existe y el resultado es swap y contexto, no velocidad.

`maxParallelForks` queda en `1` por defecto y se sube sin editar el fichero
(`-PceroxForks=2`) sólo para volver a medir si cambia la máquina.

## Decisión 3: las pruebas NO entran en la caché de build

Esta es la decisión importante y la que costó un error propio.

Con la caché activada, al repetir el cierre sin cambios el log dio:

```
> Task :app:testDebugUnitTest FROM-CACHE
BUILD SUCCESSFUL in 9.6s
```

9,6 s, `614 pruebas, 0 fallos`. **Y no se ejecutó una sola prueba.** La caché restauró los
XML de resultados de una corrida anterior y Gradle dio por bueno el verde. Es el verde
hueco de la regla 18 (`UP-TO-DATE` no es verde) con otra cara, y más difícil de ver: los
XML restaurados tienen `mtime` nuevo, así que la comprobación de edad del banco —que sí
caza un `UP-TO-DATE`— no lo cazó. La corrida `individual-repetido` de la tabla es esa
trampa: 548 MB de `MemAvailable` (el fork de pruebas nunca arrancó) y 9,6 s que **no son un
tiempo real**.

El arreglo va por los dos lados:

- **En el build:** `outputs.cacheIf { false }` en `tasks.withType(Test)` en
  `android/app/build.gradle`. Un cierre de fase tiene que ejecutar las pruebas siempre. El
  resto del build sí se cachea, que es de donde viene la ganancia de la Decisión 1. Con
  esto, el cierre completo honesto cuesta **46,2 s** (`cierre-sin-cache-de-pruebas`), 4,9×
  la base de 225 s, con las pruebas ejecutadas (`pruebas_ejecutadas: si`, XML de 7 s) y los
  mismos hashes de APK.
- **En el banco:** `tools/medir-build.py` lee el registro de Gradle y dice si las tareas de
  tipo `Test` se ejecutaron o se sirvieron de `FROM-CACHE`/`UP-TO-DATE`/`SKIPPED`. Añade
  `pruebas_ejecutadas` al JSON y una columna al histórico, y avisa a stderr cuando una
  corrida no ejecutó nada. El detector se validó contra **11 corridas ya hechas** cuyo
  veredicto se conocía por inspección manual: 11/11 aciertos. (La primera versión fallaba 8
  de 11 porque el regex `\S*[Tt]est\S*` casaba con `kspDebugUnitTestKotlin` y
  `generateDebugAndroidTestAssets`, que compilan pero no ejecutan pruebas, y marcaba «no se
  ejecutaron» hasta en las verdes. Un detector que siempre grita no se lee.)

## Concurrencia de dos worktrees: viable en tiempo, cara en disco

Para trabajo multi-paralelo de verdad (dos ramas a la vez) se probó con dos worktrees
(`git worktree add --detach`) sobre el mismo commit. **Con las pruebas ejecutándose de
verdad** (`conc3-A`/`conc3-B`):

- los dos a la vez: **111,6 s y 84,8 s**, pareja terminada en ~113 s de pared;
- los mismos dos, **en serie** (`serie-A`/`serie-B`): 38,4 s y 37,6 s, pareja en 78 s.

Es decir: en esta máquina, lanzar los dos cierres a la vez es **más lento** que lanzarlos
uno detrás de otro, porque los dos forks de Gradle compiten por 2 CPUs y ~1.983 MB. La
concurrencia sirve cuando las dos tareas no son dos cierres completos (p. ej. un cierre y
un trabajo de edición), no cuando son dos builds pesados simultáneos. Y cuesta disco: cada
worktree son ~340 MB; los dos sumaron 685 MB sobre 5,7 GB libres.

## Los dos errores propios que quedan registrados

1. **La prueba de estrés era intermitente por diseño.** `StressSuggestionEngineTest`
   afirmaba «esto tarda < 1.000 ms» sobre **una sola medición**. Falló en `cache-fria` y
   `forks2-workers2` con el mismo commit que dio 0 fallos en otras cuatro corridas: lo que
   medía no era el código, era si la máquina estaba despejada. Arreglado a mejor-de-3
   (`PRESUPUESTO_MS`/`INTENTOS` en un `private companion object`); medido bajo dos procesos
   quemando las 2 CPUs dio `[1002, 837, 703]` ms —la medición única habría fallado por 2 ms—
   y pasa en 703 ms. Una aserción de tiempo de pared mide la máquina, no el código.
2. **Se citó este documento desde dos ficheros antes de escribirlo**, y así entró en el
   commit `b7ae074`. Un `grep -rn PARALELISMO.md` lo habría cazado. Familia C6 del registro
   de fallas: borrar o citar un fichero que otros referencian sin comprobar que existe.

## Qué NO se probó aquí (y por qué)

- **Paralelismo en CI.** Los runners de GitHub Actions tienen otra CPU y 16 GiB; estos
  números son de la máquina pequeña de desarrollo y no se extrapolan. En CI el `apk.yml` ya
  sube `-Xmx6g`.
- **Más de dos worktrees.** Con dos ya se agota la RAM; tres no se midieron porque el
  resultado es predecible (OOM killer).
- **`--build-cache` remoto.** No hay servidor de caché compartido; la caché es local.
