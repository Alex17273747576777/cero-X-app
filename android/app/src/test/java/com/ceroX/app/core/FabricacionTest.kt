package com.ceroX.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La puerta que impide que vuelvan los métodos que dicen haber hecho algo.
 *
 * POR QUÉ EXISTE
 * --------------
 * `PlaceholderDetectorTest` busca la combinación "comentario de relleno + éxito en
 * las tres líneas siguientes", y con esa ventana se escapaban casi todos: en
 * `NeuralColorizer.colorizePage` la marca estaba siete líneas antes del
 * `success = true`, y en `BooruModule.searchBooru` el éxito ni siquiera era
 * nominal —era el quinto argumento posicional de un constructor—.
 *
 * El inventario real del barrido fueron 66 apariciones y unas 45 fabricaciones
 * vivas en 30 ficheros, cuando `docs/LIMPIEZA.md` tenía apuntadas ocho. O sea que
 * el detector existente encontraba una parte pequeña, y la lista escrita a mano
 * encontraba menos todavía.
 *
 * QUÉ HACE ESTA PRUEBA
 * --------------------
 * Dos cosas. La primera es prohibitiva y simple: ninguna fuente de producción
 * puede llevar las marcas de relleno. No se mira si hay éxito cerca —se prohíbe la
 * palabra—, porque un método que necesita decir que no está hecho es un método que
 * no debería devolver éxito, y esa conversación ya se tuvo.
 *
 * La segunda es positiva: comprueba que los sitios concretos donde estaban las
 * marcas siguen haciendo lo que ahora dicen. Prohibir una palabra no sirve de nada
 * si lo siguiente que pasa es que se borra la palabra y se deja la mentira.
 *
 * UN DETALLE QUE COSTÓ TRES COMMITS
 * ---------------------------------
 * El texto que explica por qué algo está prohibido no puede citar lo prohibido. Si
 * este fichero viviera en `src/main`, se acusaría a sí mismo. Vive en `src/test`,
 * que es justo lo que la prueba no escanea, y por eso puede nombrar las marcas.
 * Los comentarios de `src/main` que describían arreglos anteriores sí las citaban y
 * se reescribieron en el mismo barrido.
 *
 * Cuidado también con las mayúsculas: buscar `TODO:` sin distinguir mayúsculas
 * acusa a cualquier `metodo:`, que contiene esa secuencia.
 */
class FabricacionTest {

    private val raiz = File("src/main/java/com/ceroX/app")

    /** Marcas prohibidas. La búsqueda distingue mayúsculas a propósito. */
    private val marcas = listOf(
        "Placeholder", "placeholder", "Simular", "simular",
        "simulado", "Simulado", "not implemented", "Not implemented",
        "sin implementar", "hardcodeado", "Hardcodeado",
        "TODO:", "FIXME", "ficticio", "dummy",
    )

    private fun fuentes(): List<File> {
        assertTrue("no existe " + raiz.path + "; la prueba se ejecuta desde android/app", raiz.isDirectory)
        return raiz.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun fuente(rel: String): String {
        val f = File(raiz, rel)
        assertTrue("falta $rel", f.isFile)
        return f.readText()
    }

    @Test
    fun `ninguna fuente de produccion lleva marcas de relleno`() {
        val culpables = mutableListOf<String>()
        for (archivo in fuentes()) {
            archivo.readLines().forEachIndexed { i, linea ->
                for (marca in marcas) {
                    if (linea.contains(marca)) {
                        culpables.add(
                            archivo.path.substringAfter("com/ceroX/app/") + ":" + (i + 1) +
                                " [" + marca + "] " + linea.trim().take(80)
                        )
                    }
                }
            }
        }
        assertTrue(
            "quedan marcas de relleno en el código de producción:\n  " +
                culpables.joinToString("\n  ") +
                "\n\nCada una de esas líneas está al lado de un método que no hace lo que dice. " +
                "O se implementa, o se devuelve un fallo con el motivo.",
            culpables.isEmpty(),
        )
    }

    @Test
    fun `el catalogo de extensiones no inventa entradas`() {
        val store = fuente("modules/extension_sandbox/ExtensionStore.kt")
        assertTrue(
            "ExtensionStore vuelve a registrar extensiones con un dominio propio inventado",
            !store.contains("extensions.cero-x.app"),
        )
        assertTrue(
            "ExtensionStore declara una huella sin valor real; un instalador de " +
                "extensiones no puede fingir la huella con la que va a verificar",
            !store.contains("sha256 = \""),
        )
        val sandbox = fuente("modules/extension_sandbox/ExtensionSandboxManager.kt")
        assertTrue(
            "el recuento de extensiones activas vuelve a ser una constante",
            sandbox.contains("listFiles"),
        )
    }

    @Test
    fun `los dos colorizadores ejecutan la inferencia de verdad`() {
        for (rel in listOf(
            "modules/neural_colorizer/NeuralColorizer.kt",
            "modules/kisara/KisaraNeuralColorizerModule.kt",
        )) {
            val t = fuente(rel)
            assertTrue("$rel no crea el tensor de entrada", t.contains("OnnxTensor.createTensor"))
            assertTrue(
                "$rel no lee la salida con el lector que comprueba la forma; " +
                    "leerla con un casteo fue lo que reventó con ClassCastException",
                t.contains("SalidaTensor.aPixeles"),
            )
            assertTrue(
                "$rel devuelve una copia del bitmap de entrada como si estuviera coloreado",
                !t.contains("bitmap.copy(Bitmap.Config.ARGB_8888, true)"),
            )
        }
        // Los cinco canales de entrada son el contrato medido contra colorize.onnx.
        val kisara = fuente("modules/kisara/KisaraNeuralColorizerModule.kt")
        assertTrue(
            "el módulo de Kisara no usa los nombres de entrada y salida que exige verifyTensorProbe",
            kisara.contains("\"input\" to t") && kisara.contains("setOf(\"rgb\")"),
        )
    }

    @Test
    fun `el gestor de modelos infiere en vez de devolver ceros`() {
        val t = fuente("modules/ai/ModelManager.kt")
        assertTrue("ModelManager vuelve a devolver un FloatArray constante", t.contains("aplanar"))
        assertTrue("ModelManager no consulta la forma que declara el modelo", t.contains("TensorInfo"))
        assertTrue(
            "ModelManager no ejecuta la sesión",
            t.contains("sesion.run("),
        )
    }

    @Test
    fun `la pantalla de estres ejecuta las pruebas en vez de escribirlas`() {
        val t = fuente("ui/stress/StressTestScreen.kt")
        assertTrue("la pantalla no llama al módulo", t.contains("runStressTest"))
        assertTrue(
            "la pantalla ejecuta las pruebas en el hilo principal; una de ellas abre 50 tareas",
            t.contains("Dispatchers.Default"),
        )
        assertTrue(
            "la pantalla no enseña el detalle de cada prueba, que es donde se ve lo que midió",
            t.contains("/ detalle"),
        )
    }

    @Test
    fun `el monitor de hardware puede decir que no sabe`() {
        val t = fuente("modules/hardware_monitor/HardwareMonitor.kt")
        assertTrue(
            "getCpuUsage devuelve un Float no nuleable: sin /proc/stat no hay forma " +
                "de distinguir un 0 % real de un no se puede leer",
            t.contains("fun getCpuUsage(): Float?"),
        )
        assertTrue("no se lee /proc/stat", t.contains("/proc/stat"))
        assertTrue("no se leen las zonas térmicas", t.contains("/sys/class/thermal"))
        assertTrue("no hay estado térmico del sistema", t.contains("currentThermalStatus"))
    }

    @Test
    fun `las herramientas del catalogo dicen si se pueden ejecutar`() {
        val t = fuente("modules/tools/ToolsModule.kt")
        assertTrue(
            "ToolInfo no distingue entre estar en el catálogo y poder ejecutarse",
            t.contains("val ejecutable: Boolean"),
        )
        assertTrue("el healthCheck no cuenta las ejecutables", t.contains("tools_ejecutables"))
        assertTrue(
            "el healthCheck vuelve a afirmar que las seis herramientas están disponibles",
            t.split("\n").count { it.trim().endsWith("\" to true,") || it.trim().endsWith("\" to true") } <= 1,
        )
    }

    @Test
    fun `los analizadores dicen hasta donde llegan`() {
        val analizador = fuente("modules/reverse_engineering/AntigravityAnalyzer.kt")
        assertTrue("el analizador de APK no lee el contenedor", analizador.contains("ZipFile"))
        assertTrue(
            "el analizador afirma un análisis completo en vez de uno parcial",
            analizador.contains("\"parcial\""),
        )
        val probador = fuente("modules/reverse_engineering/StressTester.kt")
        assertTrue(
            "la prueba de compatibilidad vuelve a afirmar niveles de API que el " +
                "dispositivo no puede probar",
            probador.contains("No se probo ningun nivel de API"),
        )
        val profundo = fuente("modules/reverse_engineering/DeepReverseEngineeringModule.kt")
        assertTrue(
            "runStressTest no despacha al probador",
            profundo.contains("probador.run"),
        )
        assertEquals(
            "la nota de evaluación de un modelo no puede ser una constante",
            0, profundo.split("\n").count { it.contains("score = 0.95") },
        )
    }

    @Test
    fun `ningun resultado afirma una conexion o una ejecucion que no ocurre`() {
        // Cuatro sitios devolvían éxito por no hacer nada: conectar a un servidor,
        // ejecutar una herramienta, correr un comando en un Linux que no existe y
        // saltarse un desafío de Cloudflare. Los cuatro tienen que poder fallar, y
        // los dos primeros hacen de verdad lo que pueden hacer.
        val servidor = fuente("modules/server/ServerModule.kt")
        assertTrue("no se abre el socket", servidor.contains("java.net.Socket()"))
        assertTrue(
            "conectar sin esperar un fallo posible: el catch tiene que existir",
            servidor.contains("catch (e: Exception)"),
        )
        val kai = fuente("modules/kai/KaiModule.kt")
        assertTrue(
            "runLinuxCommand vuelve a decir que ejecutó algo en un sandbox que no existe",
            !kai.contains("Ejecutado en sandbox Linux"),
        )
        for (rel in listOf(
            "modules/sources/RepoForgeSourceModule.kt",
            "modules/repoforge/RepoForgeGenerator.kt",
        )) {
            val t = fuente(rel)
            assertTrue("$rel vuelve a anunciar un bypass que no existe", !t.contains("Bypass simulado"))
            assertTrue("$rel no dice que el bypass falla", t.contains("No hay bypass de Cloudflare"))
        }
    }
}
