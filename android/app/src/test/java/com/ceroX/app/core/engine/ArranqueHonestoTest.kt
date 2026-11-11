package com.ceroX.app.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Las pruebas del arranque honesto (1.0.1).
 *
 * El ANR de esta app no era un misterio: 30 módulos inicializándose en el hilo
 * principal, siete de ellos cargando la librería nativa de ONNX al construirse, y
 * reintentos que duermen ese mismo hilo. Estas pruebas no ejecutan Android:
 * comprueban que la POLÍTICA quede donde debe y que los textos del panel salgan
 * de los estados, no de la mano de nadie.
 */
class ArranqueHonestoTest {

    private fun fuente(rel: String): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "android/app/src/main/java/com/ceroX/app/$rel") }
            .firstOrNull { it.exists() }
            ?: listOf(
                "android/app/src/main/java/com/ceroX/app/$rel",
                "src/main/java/com/ceroX/app/$rel",
            ).map { File(it) }.firstOrNull { it.exists() }

    @Test
    fun elArranqueNoInicializaModulosEnElHiloPrincipal() {
        val t = fuente("core/CeroXApplication.kt")!!.readText()
        val onCreate = t.substring(t.indexOf("override fun onCreate"), t.indexOf("override fun onTerminate"))
        // El hilo se abre con `Thread({` y se cierra con su nombre: las dos
        // llamadas tienen que quedar ENTRE las dos marcas. La primera versión de
        // esta prueba miraba sólo el nombre y falló por eso: el nombre va después
        // del cuerpo, no antes.
        val abre = onCreate.indexOf("Thread({")
        val init = onCreate.indexOf("initializeAll()")
        val start = onCreate.indexOf("startAll()")
        val cierra = onCreate.indexOf("cerox-arranque-modulos")
        assertTrue("el arranque vive dentro de un Thread", abre >= 0)
        assertTrue("initializeAll() va dentro del hilo de arranque", abre in 0 until init)
        assertTrue("startAll() también", abre in 0 until start)
        assertTrue("y el nombre del hilo cierra el bloque, después de las dos", cierra > init && cierra > start)
    }

    @Test
    fun losSieteEntornosOnnxSonPerezosos() {
        val siete = listOf(
            "modules/ai/ModelAssets.kt",
            "modules/ai/ModelManager.kt",
            "modules/ai_training/AiTrainingModule.kt",
            "modules/kisara/KisaraColorizer.kt",
            "modules/kisara/KisaraNeuralColorizerModule.kt",
            "modules/kisara/KisaraUpscaler.kt",
            "modules/neural_colorizer/NeuralColorizer.kt",
        )
        for (rel in siete) {
            val lineas = fuente(rel)!!.readText().lines().filter { it.contains("OrtEnvironment.getEnvironment()") }
            assertTrue("$rel carga el entorno ONNX", lineas.isNotEmpty())
            assertTrue("$rel lo carga con by lazy, no al construirse", lineas.all { it.contains("by lazy") })
        }
    }

    @Test
    fun laPoliticaDePesadosCoincideConLosModulosRegistrados() {
        val app = fuente("core/CeroXApplication.kt")!!.readText()
        assertEquals("son cinco los módulos pesados", 5, ReglasArranque.PESADOS.size)
        for (n in ReglasArranque.PESADOS) {
            assertTrue("el módulo pesado " + n + " sigue registrado en la app", app.contains("\"" + n + "\""))
        }
        assertTrue(ReglasArranque.esPesado("kisara-neural-colorizer"))
        assertFalse("un módulo barato no es pesado", ReglasArranque.esPesado("houbi-reader"))
    }

    @Test
    fun losEstadosSeDicenEnPalabrasYSinAdornos() {
        assertEquals("pendiente: se inicia al pedirlo", ReglasArranque.descripcionEstado(ModuleStatus.UNLOADED, true))
        assertEquals("sin iniciar", ReglasArranque.descripcionEstado(ModuleStatus.UNLOADED, false))
        assertEquals("listo", ReglasArranque.descripcionEstado(ModuleStatus.INITIALIZED, false))
        assertEquals("funcionando", ReglasArranque.descripcionEstado(ModuleStatus.STARTED, true))
        assertEquals("falló", ReglasArranque.descripcionEstado(ModuleStatus.ERROR, false))
        assertEquals("parado", ReglasArranque.descripcionEstado(ModuleStatus.STOPPED, true))
    }

    @Test
    fun laBarraInferiorNavegaDeVerdad() {
        val t = fuente("ui/main/MainActivity.kt")!!.readText()
        assertTrue("el estado elegido decide el contenido", t.contains("if (seleccionado == \"panel\")"))
        assertTrue("y los demás destinos montan su Fragment", t.contains("onMostrarFragmento(it.crear())"))
        assertFalse("la mentira del API fijo se fue", t.contains("Android 16 · API 36"))
    }

    @Test
    fun elPanelNoAfirmaNadaEscritoAMano() {
        val t = fuente("ui/main/MainActivity.kt")!!.readText()
        assertTrue("el panel lee del gestor", t.contains("app.lifecycleManager.resumen()"))
        assertTrue("y lo dice", t.contains("nada de esto está escrito a mano"))
        assertTrue("los perezosos se pueden iniciar desde el panel", t.contains("iniciarEnSegundoPlano"))
    }
}
