# Pendientes que requieren dispositivo o credenciales

Corte: 2026-11-14. Estos elementos no se marcan completados por compilar.

## LG V60 / MangaDex / revisión visual

Estado: `adb devices` no muestra aparatos (comprobado de nuevo el 2026-11-13). `assembleDebugAndroidTest` compila, pero no ejecuta Android.

Cuando haya un dispositivo:

1. Copiar `work/auditoria-apks/mangadex.apk` al dispositivo, sin cambiar su hash fijado.
2. Instalar APK debug y androidTest generados desde el commit que se quiera validar.
3. Ejecutar `MangaDexAndroidTest` con argumentos:

```text
-e mangadexApk /ruta/en/dispositivo/mangadex.apk -e networkTests 1
```

4. Comprobar DEX→búsqueda→detalle/capítulos→páginas→imagen y conservar resultado del dispositivo.
5. Ejecutar el recorrido físico del visor, que ya está escrito y **compilado pero nunca ejecutado**:

```text
adb shell am instrument -w -e recorridoFisico 1 -e networkTests 1 \
  com.ceroX.app.test/androidx.test.runner.AndroidJUnitRunner
```

   `VisorFisicoAndroidTest` inyecta tactos reales con `sendPointerSync` y comprueba: render de Coil
   con drawable de tamaño real, pellizco que amplía y reduce sin bajar del ajuste, doble toque que
   alterna, arrastre que no saca la imagen de la vista, «Guardar» escribiendo de verdad en
   `AlmacenExterno` con ruta, tamaño y cabecera mágica de imagen, y contraste WCAG AA medido sobre
   el tema resuelto por el aparato más etiquetas y objetivo táctil de los botones. Sin
   `networkTests 1` sólo corre la parte que no necesita Safebooru.

6. Recorrer visualmente lo que ninguna prueba sustituye: Inicio, Biblioteca, Fuentes→Directorios, Web, IA, Ajustes, Safebooru, Diseño claro/oscuro y rotación. En Ajustes→Módulos del sistema, comprobar búsqueda, detalle, resultado de iniciar/detener y bloqueo de una dependencia con dependientes activos. En el visor, mirar con los ojos la imagen ampliada y escuchar el lector de pantalla. En el catálogo de apps, mirar el bloque de directorios curados, la etiqueta del tipo de enlace de cada ficha, el aviso de enlace muerto y de repositorio archivado, y el filtro «Tiendas e instaladores»: la auditoría cambió lo que se enseña y ninguna prueba JVM valida que se lea bien. Las pruebas JVM/Robolectric no validan Coil, los gestos reales, foco físico, contraste renderizado ni efectos reales de detener cada módulo en el aparato.

## Emulador, Android 17 y OmniShizuku

Corte: 2026-11-13. Pedidos explícitos del usuario que este entorno no puede satisfacer. Todo lo de abajo está medido, no supuesto; si algo cambia, se vuelve a medir antes de escribir otra cosa.

**No hay aparato ni puede haberlo.** `/dev/kvm` no existe y `/proc/cpuinfo` no tiene ningún flag `vmx` ni `svm`; 2 CPUs y 1.982 MB de RAM. Sin virtualización por hardware no arranca un AVD, ni una imagen GSI, ni Cuttlefish, así que `adb devices` seguirá vacío por mucho SDK que se instale. `mobolist.net/en` no ayuda: es un catálogo comercial de precios y especificaciones («Latest Mobile Phones, Tablets, Smartwatches Prices & Specs»), no un repositorio de perfiles de dispositivo ni de ROMs.

**Android 17 beta 3 no se puede emular.** `platforms/android-37.2-beta3` sí está instalado (`sdkmanager --install` con EXIT=0; `AndroidVersion.ApiLevel=37.1`, `CodeName=DEV`, `ExtensionLevel=23`) y sirve para compilar. Robolectric 4.16.1 no lo emula: `IllegalArgumentException: API level 37 is not available` en `UnknownSdk.java:45`. Los niveles que arrancan son 34, 35 y 36, y el proyecto prueba en 36.

**OmniShizuku no se incorpora.** `github.com/krishna3163/OmniShizuku`, Apache-2.0, 29 estrellas, Kotlin, `pushed_at 2026-11-13T08:52:59Z`. Sus dos releases publican un único asset cada una, `omnishizuku-latest-debug.apk`: 57.801.232 B, sha256 `7a0fce3109c7827e826f55a51f9b4bc3398311ceea8b9d14e3631f7330070f19`, paquete `com.krishna.omnishizuku` versionCode 326 / v1.0.0, targetSdk 37, ABIs arm64-v8a, armeabi-v7a, x86 y x86_64. Está firmado con `CN=Android Debug, O=Android, C=US` (esquemas v2 y v3 true, v1 false) y declara **47 permisos**, entre ellos `READ_CALL_LOG`, `READ_CONTACTS`, `RECORD_AUDIO`, `MANAGE_ONGOING_CALLS`, `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES`, `WRITE_SECURE_SETTINGS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `HIDE_OVERLAY_WINDOWS`, `USE_BIOMETRIC` y `ACCESS_ADSERVICES_AD_ID`. **Corrección del motivo, medido el 2026-11-13 sobre la fuente clonada (31 MB, 613 `.kt`).** Lo anterior está bien como dato del binario, pero la conclusión era falsa: se escribió que «la regla 7 no deja incorporar un artefacto que sólo existe como build de depuración», y la regla 7 no dice eso —dice que no se ejecute nada automáticamente y que firma y hash prueban procedencia, no seguridad. Que sólo publique debug tiene además explicación inocente: `build_apk.sh` sólo construye `:manager:assembleDebug` y `signing.gradle` cae al keystore de depuración cuando falta `signing.properties`. Es un hueco de CI, no un ocultamiento.

También es un **fork de Shizuku**, no una alternativa: clases en `moe.shizuku.manager.*`, `libshizuku.so` y `librish.so` empaquetadas, y permisos `moe.shizuku.manager.permission.API`/`.MANAGER` declarados como propios. Shizuku original (`RikkaApps/Shizuku`, 33.570 estrellas, 3.644 forks, no archivado) lleva ~17 meses sin commits —el último es 2025-06-18— y su última release v13.6.0 es de 2025-05-25, con 3.716.322 descargas de un APK sí firmado en release. «Sin actualizar» es correcto; «obsoleto» es otra cosa, y el fork hereda la misma base.

Lo que la auditoría contra la fuente sí encontró, y que un antivirus no diría:

- **Telemetría de Google en una herramienta privilegiada.** `manager/build.gradle:140-142` trae `firebase-bom:34.19.0`, `firebase-crashlytics` y `firebase-analytics`, con `manager/google-services.json` (project_id `omnishiziku`). De ahí salen `ACCESS_ADSERVICES_AD_ID` y `com.google.android.gms.permission.AD_ID`: cero referencias en el código propio, vienen del manifiesto fusionado de la librería.
- **Salidas a proveedores de IA**: `openrouter.ai` (8 referencias), `generativelanguage.googleapis.com` (4), `aistudio.google.com` (3), en `AiClient.kt`, `AiExplainUtil.kt`, `AiProviderRepository.kt`, `AiProviderPresets.kt` y `AgyCliEngine.kt`. Un cliente de IA dentro de una app con `WRITE_SECURE_SETTINGS` usado en 96 ficheros y `ProcessBuilder`/`exec` en 11.
- **Grabadora de llamadas real**, no un permiso huérfano: `callrecorder/ShellRecordingEngine.kt`, `ShellService.kt`, `RecordingModel.kt` y wrappers de `ServiceManager`/`ActivityManager`.
- **17 componentes `exported="true"` sin `android:permission`** que los proteja, de 48 exportados y 111 componentes. Los que merecen revisión: `InstallZipModuleActivity`, `ShellRequestHandlerActivity` y `DhizukuProvider`. `ComponentActivity` y `PreviewActivity` de Compose son restos del tooling que sólo aparecen en build de depuración.
- **Permisos declarados sin uso encontrado**: `READ_CONTACTS` (0 `ContactsContract`), `READ_CALL_LOG` (0 `CallLog`/`call_log`), `SmsManager`, `LocationManager`, `AdvertisingIdClient`. Sobre-declaración de fork agregador: descuido, no exfiltración.

En contra de la sospecha: el proyecto publica `security-reports/` con una auditoría propia que clasifica las 427 alertas de GitHub Code Scanning (419 de mobsfscan, 8 de OpenSSF Scorecard) en 21 vulnerabilidades confirmadas y corregidas, 322 endurecimientos, 8 restricciones de plataforma, 71 falsos positivos y 5 de gobernanza; y las correcciones están en `main` (`SafeIntentUtils` 25 ocurrencias, `taskAffinity=""` 16, `allowBackup="false"` 10). El APK publicado declara `HIDE_OVERLAY_WINDOWS` y `targetSdk 37`, dos de esas correcciones: binario y fuente concuerdan.

**No se ejecutó ningún antivirus y no se va a fingir lo contrario**: `clamscan`, `apktool`, `jadx`, `androguard` y `dexdump` no están instalados y la API de VirusTotal exige una clave que este entorno no tiene. Lo hecho en su lugar —auditoría contra la fuente y lectura del MobSF que ellos publican— responde mejor a la pregunta real, que no es «¿es malware conocido?» sino «¿qué hace una app con shell y `WRITE_SECURE_SETTINGS` con los datos del aparato?». Medidas completas en `work/ci/omnishizuku-auditoria-01.txt`.

El APK publicado sigue sin incorporarse al repo, y sigue sin poder instalarse: no hay aparato. Compilarlo aquí sin aparato sería teatro, y la regla 5 prohíbe presentar como «instalado» o «funcionando» lo que sólo compiló.

**zram no es posible.** `sudo modprobe` da `command not found`, no existe `/sys/block/zram*` y `zramctl` no está instalado. Hay sudo sin contraseña (uid 1000) pero no se pueden cargar módulos del kernel. La palanca real es el archivo de swap, hoy en 12 GiB sobre 1.982 MB de RAM.

Criterio de salida — volver a intentar emular un aparato:

```text
ls -l /dev/kvm                       # hoy: no existe; hace falta el nodo
grep -c -E 'vmx|svm' /proc/cpuinfo   # hoy: 0; hace falta >= 1
adb devices                          # hoy: vacío; hace falta un device
```

Criterio de salida — probar contra Android 17:

```bash
# cuando Robolectric publique soporte de API 37: subir la dependencia, repetir la
# sonda de niveles y sólo entonces mover robolectric.properties y las @Config
grep -n 'sdk=' android/app/src/test/resources/robolectric.properties
./gradlew -I ../tools/low-memory.gradle :app:testDebugUnitTest \
  --tests com.ceroX.app.core.NivelDeSdkDePruebasTest
```

Criterio de salida — OmniShizuku: lo que falta no es una firma de release, es **un aparato**. El orden correcto cuando lo haya es compilarlo desde la fuente con clave propia en vez de instalar el binario publicado, porque la fuente es Apache-2.0 y eso convierte la confianza en control:

```bash
git clone --depth 1 https://github.com/krishna3163/OmniShizuku && cd OmniShizuku
# quitar telemetría antes de compilar: firebase-analytics y firebase-crashlytics
# en manager/build.gradle:140-142, y el plugin apply de google-services
grep -n 'firebase\|google-services' manager/build.gradle build.gradle
keytool -genkeypair -v -keystore cerox.keystore -alias cerox -keyalg RSA \
  -keysize 4096 -validity 10000        # la clave, fuera del repo y sin commitear
printf 'KEYSTORE_FILE=...\nKEYSTORE_PASSWORD=...\nKEYSTORE_ALIAS=cerox\nKEYSTORE_ALIAS_PASSWORD=...\n' \
  > signing.properties                  # signing.gradle lo lee; si falta, cae a debug
./gradlew :manager:assembleRelease
apksigner verify --print-certs manager/build/outputs/apk/release/*.apk
adb devices && adb install -r manager/build/outputs/apk/release/*.apk
```

Comprobar después, sobre el APK construido y no sobre el descargado: que `aapt2 dump xmltree` ya no liste `FirebaseInitProvider` ni `AppMeasurementService`, que no queden permisos `AD_ID`, y que los 17 componentes `exported="true"` sin permiso se hayan reducido. Si el usuario quiere el binario publicado tal cual en vez del compilado, hace falta su decisión explícita con estos hallazgos delante, no una regla que lo dé por bueno.

## Kotatsu

Ejecución opt-in del índice el 2026-11-12: 3.662 entradas y 1.113 marcadas rotas. Evidencia `work/ci/pendientes-red-01.log` y `pendientes-red-01-resumen.txt`.

Esto valida únicamente lectura de metadatos. Un JAR Kotatsu no es APK Mihon; ejecución requiere contrato `MangaLoaderContext`, modelos, red/configuración, licencias y pruebas Android. No se carga código de los índices ni se ofrece un botón de ejecución.

Repetir sólo el índice:

```bash
CEROX_NETWORK_TESTS=1 ./gradlew -I ../tools/low-memory.gradle \
  :app:testDebugUnitTest \
  --tests 'com.ceroX.app.modules.extension_sandbox.IntegracionRedRealTest.instantaneas_publicas_reales_coinciden_con_el_inventario_auditado'
```

## Ejecución interna de un APK Nox (requiere aparato)

Existe y es código real, no un botón vacío: `InstaladorInternoNox.ejecutar()` exige que
la extensión sea del repositorio Nox Manga, exige autorización explícita si está marcada
NSFW, inicializa el host, descarga con `InstaladorProjectNox.preparar()` —que comprueba
el SHA-256 publicado contra el archivo y valida la firma con `ExtensionValidator` y el
certificado auditado, y borra el APK si algo falla—, carga el DEX con
`ExtensionSourceLoader` fijando `trustedSignerSha256`, verifica que paquete y versión
coincidan con el índice, registra las fuentes en `RepositorioGlobal`, deja constancia en
`RegistroNox` y **devuelve el resultado real**, incluido el mensaje de que el código
conserva los permisos de la app y la firma no es un sandbox.

Lo que falta no es código, es aparato. Nunca se ejecutó: `/dev/kvm` no existe,
`/proc/cpuinfo` no tiene flags `vmx` ni `svm`, `adb devices` sale vacío y Robolectric no
ejecuta DEX, así que la ruta del cargador de extensiones está compilada y probada sólo
hasta donde llega la JVM. La pantalla lo dice literalmente («Ejecutar no se ha verificado
en un aparato físico: sin dispositivo no hay prueba de que las fuentes carguen de
verdad») y `NavegacionTest` afirma ese texto, así que no se puede borrar en silencio. Es
la misma razón por la que `main` decía que el contrato «aún está en migración»: la
intención era correcta y se conservó, actualizada a lo que hoy es cierto.

Criterio de salida, con aparato:

```bash
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class \
  'com.ceroX.app.modules.extension_sandbox.InstaladorInternoNoxInstrumentedTest' \
  com.ceroX.app.test/androidx.test.runner.AndroidJUnitRunner
# y en el aparato: Fuentes y extensiones → Manga Nox → «Verificar y ejecutar en cero-X»
```

Comprueba que el DEX carga de verdad, que las fuentes quedan registradas y que un APK con
firma distinta o hash distinto se rechaza y se borra. Hasta entonces el recorrido es un
pendiente, no un éxito.

## Dependabot: serialize-javascript en el lockfile de Kotlin/JS

Tres alertas abiertas desde el 2026-11-10, todas sobre el mismo paquete y el mismo
manifiesto, `comun/kotlin-js-store/yarn.lock`:

| # | severidad | GHSA | rango vulnerable | primer parche |
|---|---|---|---|---|
| 1 | alta | `GHSA-5c6j-r48x-rmvq` (RCE vía `RegExp.flags` y `Date.prototype.toISOString`) | `<= 7.0.2` | 7.0.3 |
| 2 | moderada | `GHSA-qj8w-gfj5-8c6v` (DoS por agotamiento de CPU) | `>= 5.0.0, < 7.0.5` | 7.0.5 |
| 3 | baja | `GHSA-m5mm-p8h7-m29v` (XSS por `</script>` sin escapar) | `< 7.1.3` | 7.1.3 |

El lockfile fija `serialize-javascript@^6.0.2` → 6.0.2, y quien lo arrastra es
`mocha@10.7.0`, el runner de pruebas del target `js(IR) { nodejs() }` de `comun/`.

**Por qué no se cerró aquí.** Cerrar las tres exige `>= 7.1.3`, un salto de versión mayor
que el `^6.0.2` de mocha no permite. Forzarlo con una resolución
(`YarnRootExtension.resolution(...)`) rompería el target JS, y en este entorno **no se
puede verificar**: `comun/build` no existe, el target JS no se compila nunca aquí, `yarn`
no está instalado y ningún workflow de CI lo toca. Aplicar el cambio sin poder ejecutarlo
sería declarar arreglado lo que no se comprobó, que es lo que la regla 5 prohíbe.

**Riesgo medido, no supuesto: no alcanza al producto.** La app no consume el target JS,
incluye las fuentes de `comun/` directamente en su source set
(`java.srcDirs += file('../../comun/src/commonMain/kotlin')`), el APK de depuración lleva
**0 ficheros `.js`** y `comun/build` ni existe. La exposición es del entorno de
construcción del target JS, no de quien instala la app.

Esa conclusión es cierta hoy y puede dejar de serlo en silencio, así que está atada a
`core/CadenaDeSuministroJsTest` (3 pruebas): que el lockfile siga siendo del target JS,
que la app consuma `comun/` por fuentes y no por artefactos JS, y que ninguna fuente
cargue la salida JS. La versión 6.0.2 se afirma explícitamente para que un cambio obligue
a releer estas alertas en vez de colar de tapadillo.

Criterio de salida, tres caminos y los tres necesitan red y `yarn`:

```bash
# a) Subir mocha/Kotlin hasta que el toolchain acepte serialize-javascript 7.x
cd comun && ../android/gradlew jsNodeTest --info   # requiere yarn y red
# b) Forzar la resolución y verificar que el target JS sigue compilando y probando
#    (añadir en comun/build.gradle.kts: the<YarnRootExtension>().resolution("serialize-javascript", "7.1.3"))
# c) Si el target JS se abandona, quitarlo de comun/build.gradle.kts y borrar
#    comun/kotlin-js-store/: las tres alertas desaparecen con su causa.
```

El camino (c) es decisión del dueño: el target JS está documentado como deliberado en
`docs/MULTIPLATAFORMA.md` y en el README, así que no se quita por iniciativa propia.

## Proveedor IA autenticado

El cliente OpenAI-compatible existe y la clave UI se cifra con Android Keystore, pero este entorno no contiene credenciales. `ProveedorIaRedRealTest` quedó compilado y omitido, no superado.

Con una credencial autorizada, sin escribirla en archivos ni logs:

```bash
export CEROX_AI_BASE_URL='https://proveedor.example'
export CEROX_AI_MODEL='modelo-exacto'
export CEROX_AI_API_KEY='...'
./gradlew -I ../tools/low-memory.gradle :app:testDebugUnitTest \
  --tests com.ceroX.app.modules.aihub.ProveedorIaRedRealTest
```

La prueba exige HTTP 2xx, modelo y respuesta no vacía. Registra sólo HTTP/modelo/milisegundos/cantidad de caracteres; no imprime clave ni respuesta. Ejecutarla en JVM no prueba Keystore ni la UI Android, que deben revisarse en dispositivo.

## Objetivo todo-en-uno: decisiones del dueño y cabos medidos

Anotados el 2026-11-13 al cerrar la fase F1 de `docs/OBJETIVO-TODO-EN-UNO.md`. Cada uno con
su salida, porque un pendiente sin comando es un recordatorio que se olvida.

### 1. Licencia de Kiroku y ReDantotsu — DECIDIDO el 2026-11-13: se acepta la UPL

`Nahiro18/Kiroku` (18★) y su padre `AsrOfficialDev/ReDantotsu` (156★) declaran
`NOASSERTION` en la API porque su `LICENSE.md` (1.434 bytes, leído entero) no es una licencia
SPDX: es la **«Unabandon Public License (UPL)»**, que incorpora todos los términos de GPLv3 y
añade una condición —el fuente de modificaciones y obras derivadas debe estar públicamente
disponible, sin restricciones de acceso ni pago, identificando claramente la obra— y termina
automáticamente si se incumple.

cero-X-app es GPL-3.0 y su fuente es público en GitHub, así que la condición adicional se
cumple de hecho. **El dueño decidió el 2026-11-13 aceptar la UPL** (opción a de abajo): Kiroku
ya está en la lista `KOTLIN` de `scripts/bajar-referencias.sh`, clonado en `/var/tmp/referencias`
(22 MB, 1.090 ficheros, `e2d54c4`) y auditado como la fuente número once. El aviso de la UPL se
conservará en cada fichero que proceda de ahí si se llega a copiar código, como hace
`docs/LICENCIA.md` con el linaje Apache/MIT. ReDantotsu (el padre) sigue sin clonarse: se lee
por la API como referencia de producto.

Salida que se tomó (a); las otras dos quedan anotadas por si se revierte:

```bash
# a) Aceptar la UPL como compatible: añadir Kiroku a la lista KOTLIN del script y bajarlo
#    (la condición de fuente público ya se cumple; habría que conservar el aviso de la UPL
#    en cada fichero que proceda de ahí, como hace docs/LICENCIA.md con las otras).
scripts/bajar-referencias.sh --solo Nahiro18/Kiroku

# b) Rechazarla: dejar el script como está y anotar el motivo en docs/OBJETIVO-TODO-EN-UNO.md
#    para que nadie vuelva a abrir la pregunta. Kiroku sigue siendo referencia de producto
#    legible por la API de GitHub, que es lo que ya se hizo con su tema «Kiroku Liquid Glass».

# c) Pedir aclaración al autor: el texto completo de la licencia está guardado en
#    work/ci/frankie/kiroku-license.json y el de su padre en redantotsu-license.json.
```

### 2. Dos herramientas nuevas sin espejar en `entorno-de-herramientas-de-trabajo` — CERRADO el 2026-11-13

Hecho. `scripts/bajar-referencias.sh` y `tools/auditar-referencias.py` están espejadas en edt
(commit `e788b92`), copiadas del blob `6f4a848` y verificadas con `git hash-object` (idénticas)
y con `comprobar-copias.sh --estricto-origen`: `COPIAS_OK`, cero-X con 8 ficheros fieles a
`6f4a848` y al día con `main`. El `origen-commit` del manifiesto subió de `4ff793a` a `6f4a848`;
es seguro porque los 6 ficheros ya espejados son byte a byte idénticos en ambos commits, medidos
con `git rev-parse <sha>:<ruta>`, así que subir la referencia no vuelve infiel a ninguno.

De paso se cazó una deriva que el verificador no cubría: la tabla de `HERRAMIENTAS.md` seguía
dando 4,973 B y sha `72a98183` para `comprobar_entorno.py` cuando el fichero real y el manifiesto
están en 5,795 B y `9a9c2355` (repunte de la carrera TOCTOU a `4ff793a`). `comprobar-copias.sh`
comprueba manifiesto y origen pero no la tabla del doc, así que daba `COPIAS_OK` con el doc
obsoleto. Registrado como falla **C12** del común (variante de C7 aplicada a la documentación: el
dato vive en tres sitios —fichero, manifiesto, doc— y el verificador sólo cubría dos) y añadida a
la familia de «falso éxito» en «Lo que tienen en común».

```bash
cd /home/user/work/edt && bash proyectos/comprobar-copias.sh --estricto-origen   # → COPIAS_OK, cero-X 8 ficheros
```

### 3. Los tres ejes que faltan de la fase F2 — CERRADO el 2026-11-13

Hecho. `tools/auditar-referencias.py` audita ya los cuatro ejes sobre los 12 objetivos (11
referencias + cero-X; Kiroku se unió al aceptar el dueño la UPL, cabo ①) sin aparato ni
compilación: manifiestos, APIs usadas frente a
declaradas, endpoints por host y telemetría de dependencias. Evidencia en
`work/ci/frankie/f2-{manifiestos,apis,endpoints,telemetria}.tsv` y una ficha por app en
`work/ci/frankie/fichas/`. El cierre y lo que dicen los cuatro ejes está en
`docs/OBJETIVO-TODO-EN-UNO.md`, fase F2.

Cuatro falsos positivos cazados al validar la salida antes de publicarla, que es la razón de
que este cabo se cierre con la herramienta corregida y no con la primera corrida:

```bash
# 'adjust' casaba con android:windowSoftInputMode="adjustResize" en las ONCE apps; el SDK
#   real (com.adjust) no está en ninguna. 'umeng' casaba con «Diumenge» (domingo en catalán)
#   de un strings.xml de Kototoro. Ahora se buscan coordenadas de módulo, no subcadenas.
grep -rn 'com\.adjust\|adjust-sdk\|AdjustSdk' /var/tmp/referencias   # → 0 coincidencias
# 'image/*' en una cabecera HTTP Accept contaba como uso de READ_MEDIA_IMAGES; el eje 2 ya
#   separa código de recursos y del propio manifiesto.
# El eje 4 audita la raíz de build (android/ en cero-X), no src/main: antes daba «0 ficheros
#   de build» y «ninguna telemetría» sin haber mirado el build.gradle.kts real.
python3 tools/auditar-referencias.py --ejes telemetria   # cero-X: 5 ficheros, ninguno
```

### 4. PlayTorrio: sustituido por `komi-store` como referencia de código — CERRADO el 2026-11-14

El sitio vive (`https://playtorrio.xyz/`, HTTP 200, 57.105 bytes guardados en
`work/ci/frankie/playtorrio.html`), se declara open-source y menciona `torrent` 30 veces y
`stremio` 12. El único enlace a GitHub de su portada, `ayman708-UX/PlayTorrioV3`, **devuelve
404** y ese usuario tiene 0 repos públicos. Los 15 resultados de buscar «playtorrio» en GitHub
son copias de terceros (`hgus7089/PlayTorrioV3-TW` y `MediaHub-Org/PlayTorrioMov`, ambas Dart
y GPL-3.0, 4★ cada una), que no sustituyen al origen.

El dueño autorizó entonces **sustituir PlayTorrio por otra referencia de código** de dos listas
que aportó (JustIndex, Miyomi, Wotaku, EverythingMoe, AniPirate · Appteka Rebuild, FMHY Android,
Komi Store). Se censó cada candidata por la API antes de afirmar nada y **las descripciones no
casaron con los repos**: `miyomiorg/Miyomi` y `wotakumoe/wotaku` son TypeScript de sitio web (no
código Android; Wotaku además con licencia NOASSERTION), `SehonYT/AniPirate` es un índice de
datos sin código (127 KB, sin lenguaje ni licencia), y JustIndex, EverythingMoe y Appteka no
citan repo público. Sólo dos tienen código Android real: `omeg4-dev/fmhy-android` (Kotlin,
GPL-3.0, wiki FMHY sin conexión, creado tres semanas antes) y `komi-store/komi-store`.

**Decisión: `komi-store/komi-store` ocupa el hueco de código de PlayTorrio.** No es el
«marketplace madre de Appteka» que la prosa sugería: es *«a free, open-source app store for
developers' releases on GitHub, Codeberg & Forgejo»* (antes «GitHub Store», de ahí sus ★20.656),
Kotlin Multiplatform + Compose con `build-logic`. Apache-2.0 (dirección ya permitida por
`docs/LICENCIA.md`, conservar `NOTICE`), 100 commits en 30 días, CI verde (Verify, PR checks,
CodeQL) y releases maduras (v1.9.4, 13 recursos). Es la referencia directa de la superficie
catálogo+instalación+actualizaciones de cero-X —resuelve releases por la API de
GitHub/Codeberg/Forgejo e instala APK— y, al revés que cero-X (8 permisos huérfanos en F2),
**no declara ningún permiso huérfano** y **no trae telemetría** (0 SDK en 49 ficheros de build):
modelo de la higiene que F2 pidió. Clonado el 2026-11-14: 108 MB, 1.818 ficheros, `19eb744`;
fila en `referencias.tsv` y ficha en `fichas/komi-store.md`. `fmhy-android` queda como secundaria
(GPL-3.0, relevante para el indexado de directorios, recién nacida) y no se clona.

PlayTorrio sigue siendo **referencia de producto por su sitio** y nada más; por lo que anuncia
(torrent + IPTV) es la fuente de mayor riesgo jurídico de las 13, así que no copiar de ahí es la
posición por defecto y no una pérdida. Las 13 fuentes de la lista del dueño siguen siendo 13:
komi-store no reemplaza a PlayTorrio en esa lista, reemplaza su *hueco de código auditable*. El
censo queda en **12 referencias + cero-X = 13 objetivos auditados**.

```bash
curl -sS -o /home/user/work/ci/frankie/playtorrio.html https://playtorrio.xyz/   # ya hecho
# verificación por API de la sustituta (no por la descripción del dueño):
curl -sS -H "Authorization: Bearer $GP" https://api.github.com/repos/komi-store/komi-store
curl -sS -H "Authorization: Bearer $GP" https://api.github.com/repos/komi-store/komi-store/contents/
```

### 5. Las cuatro fuentes Dart — LEÍDAS por API (cabo absorbido por F3, paso 1, 2026-11-14)

`mangayomi` (4.480★), `AnymeX` (1.283★), `watchtower` y `Foxlations` no se portan ni se
compilan aquí (no hay toolchain de Flutter y cero-X es Kotlin): **se leen**. Censadas por la
API el 2026-11-14 por la estructura de su `lib/`, no por la descripción; el resultado y las
tres brechas concretas que aportan están en `docs/ASISTENTE-Y-COMPETENCIA.md` (sección «Las
cuatro fuentes Dart»). No hace falta el clon para leerlas: la API de contenidos basta.

```bash
# leer un fichero concreto sin gastar disco:
curl -sS -H "Authorization: Bearer $GP" \
  "https://api.github.com/repos/kodjodevf/mangayomi/contents/lib/modules/backup"
# bajarlas enteras sólo si hace falta grep local:
scripts/bajar-referencias.sh --todas
```
