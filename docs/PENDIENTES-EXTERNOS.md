# Pendientes que requieren dispositivo o credenciales

Corte: 2026-11-13. Estos elementos no se marcan completados por compilar.

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
