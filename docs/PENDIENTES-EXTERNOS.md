# Pendientes que requieren dispositivo o credenciales

Corte: 2026-11-13. Estos elementos no se marcan completados por compilar.

## LG V60 / MangaDex / revisión visual

Estado: `adb devices` no muestra aparatos. `assembleDebugAndroidTest` compila, pero no ejecuta Android.

Cuando haya un dispositivo:

1. Copiar `work/auditoria-apks/mangadex.apk` al dispositivo, sin cambiar su hash fijado.
2. Instalar APK debug y androidTest generados desde el commit que se quiera validar.
3. Ejecutar `MangaDexAndroidTest` con argumentos:

```text
-e mangadexApk /ruta/en/dispositivo/mangadex.apk -e networkTests 1
```

4. Comprobar DEX→búsqueda→detalle/capítulos→páginas→imagen y conservar resultado del dispositivo.
5. Recorrer visualmente Inicio, Biblioteca, Fuentes→Directorios, Web, IA, Ajustes, Safebooru, Diseño claro/oscuro y rotación. En Ajustes→Módulos del sistema, comprobar búsqueda, detalle, resultado de iniciar/detener y bloqueo de una dependencia con dependientes activos. En Ajustes→Imágenes Safebooru, tocar una miniatura y comprobar el visor: pellizco real, doble toque sobre el punto tocado, arrastre sin huecos con la imagen ampliada, botones −/+/Ajustar/1:1, escala visible, rotación recuperando la escala y «Guardar» escribiendo en la carpeta externa de la app con su ruta y tamaño (o el motivo si no hay externo). Las pruebas JVM/Robolectric no validan Coil, los gestos reales, foco físico, contraste renderizado, lector de pantalla ni efectos reales de detener cada módulo en el aparato.

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
