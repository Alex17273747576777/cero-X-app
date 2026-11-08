package com.ceroX.app.modules.source_js

/**
 * El motor JavaScript, elegido con números y no con gustos.
 *
 * LA MEDICIÓN (2026-11-08)
 * ------------------------
 * Los tres candidatos se descargaron de Maven Central y se abrieron:
 *
 * | motor | versión | licencia (del POM) | binario | clases |
 * |---|---|---|---|---|
 * | **quickjs-android** | 0.9.2 | **Apache-2.0** | 854 KB (arm64) · 878 KB (x86) | 4 |
 * | j2v8 | 6.3.4 | **EPL-1.0** | 53 MB (arm64) | 9,2 MB de jar |
 * | rhino | 1.9.2 | MPL-2.0 | jar de 1,6 MB, sin JNI | 710 |
 *
 * Y tres cosas que no salen en un README:
 *
 *  1. **El POM de quickjs-android dice Apache-2.0**, no MIT como repetía la nota
 *     anterior de este repositorio. Comprobado en el POM de Maven Central.
 *  2. **rhino 1.7.15 viene en bytecode de Java 8** (major 52) y **1.9.2 en Java
 *     11** (major 55). En Android eso no es un detalle: minSdk 26 traga Java 8 y
 *     el dexer moderno acepta 11, pero cualquier cosa por encima de 11 rompe el
 *     build. La 1.9.2 pasa; una futura 2.x probablemente no.
 *  3. **j2v8 son 53 MB por arquitectura.** En un APK universal de 4 ABIs serían
 *     más de 200 MB solo de motor JS, y su licencia EPL-1.0 es incompatible con
 *     Apache-2.0 para distribución combinada sin revisión legal.
 *
 * DECISIÓN: **quickjs-android 0.9.2**
 * -----------------------------------
 *   + licencia Apache-2.0, la misma del resto del proyecto
 *   + 854 KB frente a 53 MB: cabe en el APK sin discusión
 *   + es el mismo motor que usa Mihon (`com.github.zhanghai.quickjs-java`)
 *   + bytecode Java 8
 *   - sin JIT: más lento que V8 en bucles largos, y por eso hay timeout
 *   - el binario nativo tiene que cargar; si no carga, se dice
 *
 * LO QUE SE PROBÓ DE VERDAD
 * -------------------------
 * La JVM de escritorio **no puede** cargar el `.so` de Android, así que la
 * prueba del motor se hizo con el `qjs` de línea de comandos del propio QuickJS
 * (MIT, compilado desde el fuente oficial): 11 de 11 en verde, incluidos parseo
 * de HTML con expresiones regulares, `Map`, plantillas, `??` y `async/await`.
 * El script está en `scripts/probar-quickjs-linux.py`.
 *
 * Lo que sigue sin probarse: que `libquickjs.so` cargue en un teléfono. Eso
 * necesita dispositivo, y no hay.
 */
object MotorJs {

    /** Identificador que se usa en los registros y en la pantalla de ajustes. */
    const val ID = "quickjs"

    /** Qué motor está compilado en este APK. Se lee, no se supone. */
    val nombre: String get() = "QuickJS 0.9.2 (app.cash.quickjs)"

    /**
     * Si la clase del motor está en el APK.
     *
     * Se comprueba con `Class.forName` y no con una constante: si alguien quita
     * la dependencia, esto devuelve false y la app lo dice, en vez de reventar
     * cuando el usuario abre una extensión.
     */
    fun disponible(): Boolean = try {
        Class.forName("app.cash.quickjs.QuickJs")
        true
    } catch (e: Throwable) {
        false
    }

    /** El porqué de la elección, en una línea, para la pantalla de diagnóstico. */
    fun justificacion(): String =
        "Apache-2.0 · 854 KB en arm64 · Java 8 · el mismo motor que usa Mihon. " +
            "Descartados: j2v8 (EPL-1.0, 53 MB por ABI) y Rhino (MPL-2.0, sin JIT y más lento)"

    /**
     * Alternativas que se evaluaron, con su motivo de descarte.
     *
     * Se dejan aquí y no en un documento aparte para que la decisión viaje con
     * el código: quien lea esto en seis meses ve por qué no se eligió lo otro.
     */
    val descartados: List<Triple<String, String, String>> = listOf(
        Triple(
            "j2v8 6.3.4",
            "EPL-1.0",
            "53 MB por arquitectura; en APK universal con 4 ABIs pasaría de 200 MB",
        ),
        Triple(
            "Rhino 1.9.2",
            "MPL-2.0",
            "sin JIT: más lento que QuickJS en bucles; 1,6 MB pero 710 clases que no caben en R8 sin reglas",
        ),
    )
}
