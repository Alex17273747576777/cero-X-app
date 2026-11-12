# Reglas de trabajo de cero-X

## Secuencia consciente

1. **Una fase activa.** Terminar y verificar la fase actual antes de abrir otra. No mezclar reparación de entorno, contratos de extensiones, rediseño y proveedores en un mismo cambio indiferenciado.
2. **No perder variables.** Antes de cambiar de fase, actualizar `docs/FASES.md`: commit/base, archivos tocados, pruebas y cifras, lo realmente ejecutado, bloqueos y siguiente acción concreta. El workspace y Git son la fuente de verdad, no la memoria de la conversación.
3. **Entorno primero.** Ejecutar `python3 tools/comprobar_entorno.py` al inicio y al terminar una fase grande. Mantener margen respecto a 128 MiB/10.000 archivos; las dependencias y cachés pesadas viven fuera del workspace. No instalar VM, imágenes Android ni modelos salvo que la fase los necesite y haya presupuesto explícito.
4. **Paso que dure.** Un cambio pequeño con recorrido y prueba vale más que diez pantallas nuevas sin ejecutar. Después de cada parte grande, convertir lo aprendido en una regla aquí o en `.agents/skills/`, con su comprobación automática cuando sea posible.
5. **Funcional, no decorativo.** Ningún botón puede quedar sin acción. Toda opción nueva necesita estado de carga, resultado, vacío, error y salida. No presentar «instalado», «compatible», «verificado», «IA» o «funcionando» si sólo se descargó un índice, existe una clase o compiló el código.
6. **Pruebas honestas.** Robolectric no ejecuta DEX de extensiones. `DexClassLoader` y el recorrido de una fuente se validan con `androidTest` en Android. dex2jar no forma parte de la app ni de su validación. Una prueba omitida o pendiente de dispositivo se declara así.
7. **Código externo bajo control.** No ejecutar APK/JAR/JS automáticamente. Firma y hash prueban procedencia/integridad dentro de una lista aprobada, no seguridad ni sandbox. Descargas HTTPS, límites de tamaño, hashes y acciones explícitas.
8. **Diseño por sesión y categoría.** El motor adaptativo debe explicar y permitir deshacer sus cambios; no puede hacerse pasar por IA generativa ni alterar navegación mientras el usuario escribe, lee o reproduce. NSFW permanece desactivado por defecto.
9. **Cierre mínimo de fase Android.** Suite `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, auditoría de entorno y revisión del diff. Las pruebas físicas, credenciales reales o recursos ausentes quedan como bloqueos visibles, nunca como éxitos.
10. **Commit trazable.** Commit sólo con la fase verde y diff revisado. No imprimir credenciales, no forzar push y no reescribir releases anteriores.

## Comandos base

```bash
python3 tools/comprobar_entorno.py
source entorno/local.sh
cd android
./gradlew \
  -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
  -I ../tools/low-memory.gradle \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Si falta el JDK/SDK: `scripts/preparar-entorno-android.sh` (no instala emulador ni modelos).
