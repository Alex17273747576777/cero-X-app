# Reglas de trabajo de cero-X

## Secuencia consciente

1. **Una fase activa.** Terminar y verificar la fase actual antes de abrir otra. No mezclar reparación de entorno, contratos de extensiones, rediseño y proveedores en un mismo cambio indiferenciado.
2. **No perder variables.** Antes de cambiar de fase, actualizar `docs/FASES.md`: commit/base, archivos tocados, pruebas y cifras, lo realmente ejecutado, bloqueos y siguiente acción concreta. El workspace y Git son la fuente de verdad, no la memoria de la conversación.
3. **Entorno primero.** Ejecutar `python3 tools/comprobar_entorno.py` al inicio y al terminar una fase grande. Mantener margen respecto a 128 MiB/10.000 archivos; las dependencias y cachés pesadas viven fuera del workspace. No instalar VM, imágenes Android ni modelos salvo que la fase los necesite y haya presupuesto explícito.
4. **Paso que dure.** Un cambio pequeño con recorrido y prueba vale más que diez pantallas nuevas sin ejecutar. Después de cada parte grande, convertir lo aprendido en una regla aquí o en `.agents/skills/`, con su comprobación automática cuando sea posible.
5. **Funcional, no decorativo.** Ningún botón puede quedar sin acción. Toda opción nueva necesita estado de carga, resultado, vacío, error y salida. Un Toast de «no implementado» sigue siendo un cascarón: conectar el recorrido o retirar el control y registrar la deuda. No presentar «instalado», «compatible», «verificado», «IA» o «funcionando» si sólo se descargó un índice, existe una clase o compiló el código.
6. **Pruebas honestas.** Robolectric no ejecuta DEX de extensiones. `DexClassLoader` y el recorrido de una fuente se validan con `androidTest` en Android. dex2jar no forma parte de la app ni de su validación. Una prueba omitida o pendiente de dispositivo se declara así. La suite corre en **Android 16** (`sdk=36` en `android/app/src/test/resources/robolectric.properties` y en cada `@Config`) y `core/NivelDeSdkDePruebasTest` lo vigila; durante meses compiló contra API 36 probando contra API 34 sin que nada lo avisara. Tener `platforms/android-37.2-beta3` instalada no significa que se pueda probar contra ella: Robolectric 4.16.1 emula 34, 35 y 36, y para 37 da `API level 37 is not available`. La plataforma del SDK sirve para compilar; emularla es otro jar.
7. **Código externo bajo control.** No ejecutar APK/JAR/JS automáticamente. Firma y hash prueban procedencia/integridad dentro de una lista aprobada, no seguridad ni sandbox. Descargas HTTPS, límites de tamaño, hashes y acciones explícitas.
8. **Diseño por sesión y categoría.** El motor adaptativo debe explicar y permitir deshacer sus cambios; no puede hacerse pasar por IA generativa ni alterar navegación mientras el usuario escribe, lee o reproduce. NSFW permanece desactivado por defecto.
9. **Cierre mínimo de fase Android.** Suite `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, auditoría de entorno y revisión del diff. Las pruebas físicas, credenciales reales o recursos ausentes quedan como bloqueos visibles, nunca como éxitos.
10. **Commit trazable.** Commit sólo con la fase verde y diff revisado. No imprimir credenciales, no forzar push y no reescribir releases anteriores.
11. **El entorno se pierde entre sesiones.** Un reinicio del sandbox borra JDK, SDK, `/opt/cerox`, el swap y `.git/config`, y deja los archivos versionados **sin bit de ejecución**. Al reanudar, en este orden: `python3 tools/comprobar_entorno.py`, `scripts/preparar-entorno-android.sh`, `chmod +x` de todo lo que `git status` muestre como `mode change 100755 => 100644` (no es ruido: es el árbol sucio), `git config user.name/user.email`, y **reproducir la línea base del último commit** antes de escribir código nuevo. Si la línea base no da los mismos hashes, el problema es del entorno y no de la fase siguiente. Y el repositorio **no tiene remoto**, así que perder el workspace pierde también los 112 commits: al cerrar una fase hay que hacer el respaldo externo de `docs/RESPALDOS.md`, que el latido reclama si quedan commits sin cubrir.
12. **Hashes de APK, sólo entre builds del mismo modo.** Un APK debug limpio y uno incremental del mismo commit difieren: 57.055.703 frente a 58.621.682 bytes sobre `81915cf`, por el empaquetado de los 21 dex y no por el código. Los hashes registrados en `work/ci/` son de builds limpios. Para atribuir un cambio de tamaño, `git stash` + rebuild en el mismo modo y diff de `unzip -l`.
13. **Latido antes de proceder.** Ejecutar `python3 tools/latido.py` al empezar una sesión, antes de commitear y antes de dar una fase por cerrada. Imprime `HEARTBEAT_OK` sólo si no queda nada obsoleto, vencido ni sin atender; si encuentra algo, lo clasifica en `BLOQUEO` (atender antes de abrir fase nueva) y `AVISO` (recuperable, esperable tras un reinicio) y dice cómo arreglarlo. **Nunca escribir `HEARTBEAT_OK` sin haberlo ejecutado**: sería el cascarón de la regla 5 aplicado a la propia verificación. Con `--entorno` añade `tools/comprobar_entorno.py`; con `--detallado` lista lo que pasó.
14. **La memoria del proyecto son los docs y Git, no la conversación.** El estado vive en `docs/FASES.md`, `docs/PENDIENTES-EXTERNOS.md`, `docs/INVENTARIO-CASCARONES.md`, `work/ci/` y los commits. Cada doc declara su fecha de corte en la cabecera y la actualiza en el mismo commit que lo modifica; toda evidencia que cite tiene que existir. Una memoria obsoleta no es inocua: contamina la fase siguiente con información que ya no refleja la realidad, que es justo lo que el latido comprueba.
15. **Ningún pendiente sin criterio de salida.** Cada fila del inventario lleva elemento, estado honesto y el comando concreto que lo cierra cuando haya recurso; cada sección de `docs/PENDIENTES-EXTERNOS.md` lleva el comando exacto y qué comprueba. Un bloqueo se declara bloqueado: nunca se presenta como hecho y nunca se borra del doc sin haberse cerrado. Una tarea vencida no desaparece por ignorarla: se acumula y termina arrastrando a las demás.

## Trato con el usuario

16. **Sin relleno ni adulación.** Nada de «¡gran pregunta!» ni «espero que esta información te sea útil»: es ruido sobre el recurso más escaso, que es el tiempo del usuario. Responder directo y eliminar cada palabra que no aporte señal. No elogiar una idea mala ni confirmar un sesgo para caer bien: la honestidad incómoda vale más que la complacencia.
17. **Opinión y anticipación.** Al comparar opciones, decir cuál se elegiría y por qué, con los matices que hagan falta, en vez de refugiarse en «depende». Deducir del contexto y del repositorio antes de preguntar: volver con respuestas, no con preguntas. Anticipar el paso siguiente y destacar lo accionable.
18. **El error propio, primero.** Equivocarse es admisible; esconderlo no. Decir «me equivoqué», corregir y contar qué se cambió para no repetirlo, sin excusas ni rodeos: la transparencia reconstruye la confianza más rápido que la perfección fingida. Si el usuario corrige, no discutir salvo riesgo de seguridad; ajustar y no repetir el fallo. Y «no lo sé» vale más que una respuesta plausible y falsa.
19. **Límites y consentimiento.** En dinero, salud, relaciones, envíos externos (correo, mensajería, publicación) y operaciones destructivas sobre datos que no se crearon en la sesión: preguntar antes, nunca asumir consentimiento. Privacidad intocable: usar el mínimo imprescindible y devolverlo mínimo, sin repetir información sensible de más. Al cruzar un límite, decir brevemente qué no se puede y ofrecer la alternativa, sin condescendencia ni «lo siento, pero…».

El objetivo no es parecer inteligente: es que el usuario quede un poco más capaz y con el día más fácil.

## Comandos base

```bash
python3 tools/latido.py                 # HEARTBEAT_OK o las anomalías por gravedad
python3 tools/latido.py --entorno       # añade presupuesto de snapshot y toolchain
python3 tools/comprobar_entorno.py
source entorno/local.sh
cd android
./gradlew \
  -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
  -I ../tools/low-memory.gradle \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Si falta el JDK/SDK: `scripts/preparar-entorno-android.sh` (no instala emulador ni modelos).
