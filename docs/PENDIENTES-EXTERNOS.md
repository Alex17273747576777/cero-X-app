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

**OmniShizuku no se incorpora.** `github.com/krishna3163/OmniShizuku`, Apache-2.0, 29 estrellas, Kotlin, `pushed_at 2026-11-13T08:52:59Z`. Sus dos releases publican un único asset cada una, `omnishizuku-latest-debug.apk`: 57.801.232 B, sha256 `7a0fce3109c7827e826f55a51f9b4bc3398311ceea8b9d14e3631f7330070f19`, paquete `com.krishna.omnishizuku` versionCode 326 / v1.0.0, targetSdk 37, ABIs arm64-v8a, armeabi-v7a, x86 y x86_64. Está firmado con `CN=Android Debug, O=Android, C=US` (esquemas v2 y v3 true, v1 false) y declara **47 permisos**, entre ellos `READ_CALL_LOG`, `READ_CONTACTS`, `RECORD_AUDIO`, `MANAGE_ONGOING_CALLS`, `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES`, `WRITE_SECURE_SETTINGS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `HIDE_OVERLAY_WINDOWS`, `USE_BIOMETRIC` y `ACCESS_ADSERVICES_AD_ID`. La regla 7 no deja incorporar un artefacto que sólo existe como build de depuración, y no habría aparato donde instalarlo. El APK se descargó a `/tmp` a propósito —fuera del workspace— y no se copió al repo.

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

Criterio de salida — OmniShizuku: que el proyecto publique un APK de release firmado con un certificado que no sea `CN=Android Debug`, y que exista un aparato donde instalarlo. Se vuelve a medir con `apksigner verify --print-certs` sobre el asset nuevo antes de decidir nada.

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
