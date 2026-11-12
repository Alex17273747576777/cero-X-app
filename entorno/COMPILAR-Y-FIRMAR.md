# Compilar y firmar

Las tres trampas que cuestan una tarde cada una, con lo que las demuestra dentro del
propio repositorio. Nada de esto es teoría: los tres números y los tres órdenes están
en ficheros que se pueden abrir, y se citan con su ruta.

Versión en el momento de escribir esto: `1.0.2-beta-iptv-eq` (versionCode 7).

---

## 1 · El heap de R8 no se baja, se sube donde sobra memoria

`android/gradle.properties` lleva `-Xmx2048m`, y ese valor no es arbitrario: está
medido en una máquina de 1982 MiB de RAM con 2 GiB de swap, y el propio fichero
recuerda las tres pruebas.

| heap | qué pasó |
|---|---|
| `-Xmx1024m` | R8 murió de `OutOfMemoryError`, con mensaje |
| `-Xmx1400m` | el demonio desapareció sin mensaje: el asesino por falta de memoria del núcleo |
| `-Xmx2048m` | terminó. Es el valor con el que se construyó el APK de 475 MB |

La lección que anota el fichero es la que importa: **bajar el límite convierte un fallo
lento en uno silencioso, que es peor**. Con 2 GiB R8 se murió al minificar el release
también en CI (run `43010008338`), así que el flujo del APK sube el heap **por línea de
comandos** y deja el `gradle.properties` intacto para la máquina pequeña:

    -Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8

con

    -Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8

O sea: el techo de la máquina pequeña se queda donde está y el del corredor de CI, que
tiene 16 GiB, se usa. Nadie paga por el otro.

## 2 · La máquina pequeña: un solo trabajador, sin demonio, sin paralelo

En 2 GiB con swap, Gradle no se puede comportar como en un servidor. Los valores que
funcionan están en `android/gradle.properties`:

    org.gradle.workers.max=1
    org.gradle.parallel=false
    kotlin.compiler.execution.strategy=in-process

y en la línea de comandos, `--no-daemon --console=plain --max-workers=1`. Los dos flujos
de CI usan `--console=plain`; el del APK añade `--stacktrace`, que es lo que permite leer
dónde se murió R8 en vez de adivinarlo.

El swap no es un detalle: sin él, el asesino por falta de memoria del núcleo se lleva el
demonio de Gradle sin dejar mensaje, y lo que se ve es una compilación que «se para». Con
swap se vuelve lenta y termina. Lenta y terminada gana a rápida y muerta.

Aparte, `systemProp.maven.repo.local=/opt/m2` saca la caché de Robolectric del directorio
personal: cada versión de Android probada baja un jar de 143,67 MiB, y en este entorno lo
que está bajo el directorio personal es lo único que se conserva entre mensajes.

## 3 · `signingConfigs` va antes que `buildTypes`, y no es estética

Gradle evalúa el guion de arriba abajo, así que un bloque que se referencia tiene que
estar declarado antes. En `android/app/build.gradle` el orden es:

| línea | qué hay |
|---|---|
| 38 | `signingConfigs {` |
| 53 | `buildTypes {` |
| 56 | `signingConfig signingConfigs.release` |

Si el primer bloque se mueve debajo del segundo, la referencia de la línea 56 se resuelve
contra algo que todavía no existe y el fallo llega al configurar, antes de compilar nada:
no hay ningún error de Kotlin ni de recursos que buscar, y por eso cuesta una tarde.

En el mismo fichero, después del bloque de tipos, se fijan los dos esquemas de firma
(`v1SigningEnabled` y `v2SigningEnabled`). La 1.0.1-beta salió sólo con v2 y se instaló y
verificó igual; se mantienen los dos declarados.

---

## De dónde sale la clave

La clave **no está en el repositorio**. Vive en los secretos del flujo:
`CEROX_KEYSTORE_B64`, `CEROX_KEYSTORE_PASS`, `CEROX_KEY_ALIAS` y `CEROX_KEY_PASS`. El
flujo del APK escribe el almacén en base64 a un fichero temporal del corredor y lo borra
al terminar el trabajo.

Lo que sí es público es la huella, en `entorno/HUELLA-CERTIFICADO.txt`:

    6626f635443cd189851b793a9cda43eb1f81ebacdf2519e48cc0ab417e273a19

El flujo verifica el APK con el `apksigner` oficial, compara esa huella con la que da el
APK y **falla si no coincide**, en vez de publicar otra cosa. Hay una prueba publicada
(`core/engine/FirmaNuevaTest.kt`) que exige que el flujo contenga esa comparación, y otra
que exige que ningún flujo lleve la clave en claro.

## Qué se construye

    bash ./gradlew :app:assembleRelease -PsinModelos -PsoloAbi=<abi> <heap> --console=plain --stacktrace

Una sola arquitectura por build (por defecto `arm64-v8a`, la del V60 ThinQ 5G) y sin
modelos. Los ONNX pesan 378 MiB y no van dentro: ésos son los 475.523.392 bytes del APK
original. El flujo cuenta los `.onnx` del ZIP y falla si hay alguno.

Piezas de media3 declaradas hoy, las dos en la misma versión fija: `media3-exoplayer:1.11.1` y `media3-exoplayer-hls:1.11.1`.

## Lo que este documento NO dice

Que la app funcione en un teléfono. Aquí no hay emulador y no se ha probado nada en un
dispositivo: ni el ecualizador, ni la reproducción, ni las pantallas. Las 472 pruebas del
proyecto son de JVM, y una prueba de JVM no abre un DSP ni un decodificador de vídeo. El
banco previsto es un V60 ThinQ 5G y sigue sin usarse.

Tampoco dice cómo recuperar el certificado de la 1.0.0-beta: se perdió. Por eso la
1.0.1-beta se firmó con un almacén nuevo, que es lo que cuenta `docs/FIRMA-NUEVA.md`, y
por eso una instalación 1.0.0-beta hay que desinstalarla antes. Entre la 1.0.1 y las
siguientes no: es el mismo almacén y se actualiza en el sitio.
