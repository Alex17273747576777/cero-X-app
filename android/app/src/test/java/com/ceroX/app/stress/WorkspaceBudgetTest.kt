package com.ceroX.app.stress

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El espacio de trabajo no puede crecer sin control.
 *
 * QUÉ PASÓ
 * --------
 * Al añadir las primeras pruebas que necesitan `Bitmap` de verdad hubo que traer
 * Robolectric. Robolectric descarga `android-all-instrumented-<versión>.jar`:
 * **143,67 MiB** por cada versión de Android que se pruebe. La descarga fue, por
 * defecto, a `~/.m2`, y en este entorno `~` es el único directorio que se guarda
 * entre sesiones.
 *
 * Resultado: una sesión creó 143,7 MB en el espacio de trabajo y se pasó del
 * límite (128 MB / 10.000 archivos). No se perdió trabajo —todo estaba en git—
 * pero 4 archivos no llegaron a guardarse.
 *
 * POR QUÉ UNA PRUEBA Y NO SOLO UNA NOTA EN EL README
 * --------------------------------------------------
 * Porque el fallo no fue de conocimiento, fue de configuración: la nota se lee
 * una vez y la descarga se repite en la siguiente máquina. Esta prueba falla si
 * el directorio de trabajo crece por encima de lo razonable, así que el fallo
 * aparece en `gradle test` y no tres sesiones después.
 *
 * Se ejecuta solo si existe el directorio, para que no falle en una máquina que
 * no sea esta (un CI normal tiene otro layout).
 */
class WorkspaceBudgetTest {

    @Test
    fun `el directorio de trabajo no supera los 32 MiB`() {
        val trabajo = File(System.getProperty("user.home"))
        if (!trabajo.exists()) return

        // Solo aplica en la máquina de la casa, que es donde el espacio de trabajo se
        // guarda entre sesiones. En un CI el $HOME es el del runner: tiene su propio
        // caché de Gradle y de Robolectric, pasa de 32 MiB sin que nadie esté haciendo
        // nada mal, y el rojo no señala ningún fallo del proyecto. Un rojo permanente
        // en CI enseña a ignorar el CI.
        val esLaMaquinaDeLaCasa = File("/opt/robolectric").isDirectory ||
            File("/opt/gradle-home").isDirectory
        org.junit.Assume.assumeTrue(
            "presupuesto de espacio de la máquina de la casa: fuera de ella no aplica",
            esLaMaquinaDeLaCasa,
        )

        var total = 0L
        var archivos = 0
        val grandes = mutableListOf<Pair<Long, String>>()

        trabajo.walkTopDown()
            .onEnter { dir -> !dir.name.startsWith(".") || dir.name in setOf(".m2", ".gradle", ".android") }
            .filter { it.isFile }
            .forEach { f ->
                val t = f.length()
                total += t
                archivos++
                if (t > 8L * 1024 * 1024) grandes.add(t to f.absolutePath)
            }

        val totalMb = total / (1024.0 * 1024.0)
        println("  ${"%.2f".format(totalMb)} MiB en $archivos archivos bajo $trabajo")

        if (grandes.isNotEmpty()) {
            throw AssertionError(
                "hay ${grandes.size} archivo(s) de más de 8 MiB en el espacio de trabajo:\n" +
                    grandes.joinToString("\n") { (t, p) -> "  ${"%.2f".format(t / 1048576.0)} MiB  $p" } +
                    "\n\nCasi seguro es la caché de Robolectric (android-all-instrumented, 143,67 MiB)." +
                    "\nLa solución está en android/gradle.properties.leeme y en app/build.gradle:" +
                    "\n  systemProperty \"robolectric.dependency.dir\", \"/opt/robolectric\"" +
                    "\ny el jar se descarga una vez ahi a mano."
            )
        }

        assertTrue(
            "el espacio de trabajo ocupa ${"%.2f".format(totalMb)} MiB en $archivos archivos: " +
                "por encima de esto algo está guardando caché de build donde no debe",
            totalMb < 32.0 && archivos < 2000
        )
    }
}
