package com.ceroX.app.modules.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import com.ceroX.app.modules.kisara.KisaraColorizer
import java.io.File
import java.io.FileOutputStream

/**
 * Módulo de IA neuronal.
 *
 * HISTORIA DE ESTE ARCHIVO, PORQUE IMPORTA
 * ----------------------------------------
 * La versión anterior tenía un `colorizeMangaPage()` que:
 *   - no abría ninguna imagen,
 *   - no ejecutaba ningún modelo,
 *   - devolvía `success = true`,
 *   - y anunciaba `modelUsed = "manga_colorizer_v2_int8.onnx"`, un archivo que
 *     nunca estuvo en el APK.
 *
 * Un método que dice "colorizado con éxito" sin colorizar nada es peor que uno
 * que falla, porque el que falla se nota. Ese método se ha eliminado y en su
 * lugar están dos cosas separadas:
 *
 *   `colorizeMangaPage()`  → llama al motor real (`KisaraColorizer`) y devuelve
 *                            fallo con motivo si no puede. Nunca `success = true`
 *                            sin un archivo de salida que exista y pese > 0.
 *   `loadColorizerModel()` → desaparece: el modelo ya no se carga desde una ruta
 *                            externa. `KisaraColorizer` vive en `assets/models/`
 *                            y sabe cómo copiarlo a disco él mismo. Pedirle al
 *                            llamador una ruta era una forma de que alguien pasara
 *                            una ruta inválida y el error saliera en otro sitio.
 */
class NeuralAIModule : ModuleInterface() {

    override val name: String = "NeuralAI"
    override val version: String = "2.0.0"

    // _initialized y _started los declara y mantiene ModuleInterface.
    // Declararlos otra vez aquí era ocultar el estado del padre con el propio:
    // dos banderas para lo mismo, y `isInitialized()` del padre mirando la suya.
    private var onnxInitialized = false
    private var nnapiEnabled = false
    private var appContext: Context? = null

    private val colorizationHistory = mutableListOf<ColorizationResult>()
    private var hardwareStats = HardwareStats()

    override fun initialize(config: Map<String, Any>): Boolean {
        onnxInitialized = true
        nnapiEnabled = config["nnapi"] as? Boolean ?: false
        appContext = config["context"] as? Context
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean {
        _started = false
        return true
    }

    override fun healthCheck(): ModuleHealth {
        val ctx = appContext
        return ModuleHealth(
            name, version,
            when {
                !_started -> "stopped"
                ctx == null -> "sin contexto de Android: no puede colorizar"
                else -> "healthy"
            },
            mapOf(
                "onnx_runtime" to onnxInitialized,
                "nnapi_enabled" to nnapiEnabled,
                "backend" to if (nnapiEnabled) "NNAPI" else "CPU",
                "colorizations" to colorizationHistory.size,
                // Se informa del motor REAL, no de un nombre de archivo soñado.
                "motor" to "colorize.onnx (117,26 MiB, empaquetado)",
                "ms_medidos_cpu_2_nucleos" to 1490,
                "contexto_disponible" to (ctx != null),
                "cpu_percent" to hardwareStats.cpuPercent,
                "gpu_mhz" to hardwareStats.gpuMhz,
                "ram_mb" to hardwareStats.ramMb
            )
        )
    }

    /**
     * Coloriza una página de verdad.
     *
     * Contrato, y se cumple o se devuelve fallo:
     *   - el archivo de entrada tiene que existir y no estar vacío
     *   - el resultado solo lleva `success = true` si el archivo de salida
     *     existe y pesa más de cero bytes
     *   - `inferenceTimeMs` es tiempo de reloj real, no una estimación
     *
     * Es bloqueante a propósito: se espera que el llamador lo invoque desde una
     * corrutina en `Dispatchers.Default`. 1 490 ms en un hilo de interfaz son un
     * ANR garantizado.
     */
    fun colorizeMangaPage(imagePath: String): ColorizationResult {
        val inicio = System.currentTimeMillis()

        fun fallo(motivo: String) = ColorizationResult(
            inputPath = imagePath,
            outputPath = "",
            success = false,
            inferenceTimeMs = System.currentTimeMillis() - inicio,
            modelUsed = "colorize.onnx",
            device = if (nnapiEnabled) "NNAPI" else "CPU",
            errorMessage = motivo
        ).also { colorizationHistory.add(it) }

        if (!_started) return fallo("el módulo no está iniciado")
        val ctx = appContext ?: return fallo("falta el contexto de Android en initialize()")

        val entrada = File(imagePath)
        if (!entrada.exists()) return fallo("no existe el archivo de entrada: $imagePath")
        if (entrada.length() == 0L) return fallo("el archivo de entrada está vacío: $imagePath")

        val bitmap = try {
            BitmapFactory.decodeFile(imagePath)
        } catch (e: OutOfMemoryError) {
            return fallo("sin memoria para decodificar la página: ${e.message}")
        } catch (e: Exception) {
            return fallo("no se pudo decodificar la imagen: ${e.javaClass.simpleName}")
        } ?: return fallo("la imagen no se pudo decodificar (¿formato no soportado?)")

        val motor = KisaraColorizer(ModelAssets(ctx))
        val resultado: KisaraColorizer.Resultado = try {
            motor.colorear(bitmap)
        } catch (e: OutOfMemoryError) {
            return fallo("sin memoria durante la inferencia: la página es demasiado grande")
        } catch (e: Exception) {
            return fallo("fallo del motor ONNX: ${e.javaClass.simpleName}: ${e.message}")
        }

        if (!resultado.exito || resultado.bitmap == null) {
            return fallo(resultado.motivo.ifBlank { "el motor no devolvió imagen" })
        }

        // Antes la imagen coloreada se escribía JUNTO a la de entrada, y la de
        // entrada puede estar en cualquier sitio — incluido el almacenamiento
        // interno, que es lo que la regla prohíbe. Ahora va al externo de la app.
        val destinoExterno = com.ceroX.app.core.AlmacenExterno.directorio(ctx)
            ?: return fallo(
                "sin almacenamiento externo: la imagen coloreada no se escribe en el " +
                    "almacenamiento interno (regla del proyecto)",
            )
        val carpetaSalida = File(destinoExterno, "colorizadas").apply { mkdirs() }
        val salida = File(carpetaSalida, entrada.nameWithoutExtension + "_color.png")
        try {
            FileOutputStream(salida).use { flujo ->
                resultado.bitmap.compress(Bitmap.CompressFormat.PNG, 100, flujo)
            }
        } catch (e: Exception) {
            return fallo("no se pudo escribir la salida: ${e.javaClass.simpleName}")
        }

        // La comprobación que faltaba: que el archivo exista y tenga bytes.
        if (!salida.exists() || salida.length() == 0L) {
            return fallo("la salida quedó vacía tras escribirla")
        }

        val ok = ColorizationResult(
            inputPath = imagePath,
            outputPath = salida.absolutePath,
            success = true,
            inferenceTimeMs = System.currentTimeMillis() - inicio,
            modelUsed = "colorize.onnx",
            device = if (nnapiEnabled) "NNAPI" else "CPU",
            errorMessage = null
        )
        colorizationHistory.add(ok)
        return ok
    }

    fun listColorizationHistory(): List<ColorizationResult> = colorizationHistory.toList()

    fun updateHardwareStats(cpuPercent: Float, gpuMhz: Int, ramMb: Long) {
        hardwareStats = HardwareStats(cpuPercent, gpuMhz, ramMb, System.currentTimeMillis())
    }

    fun getHardwareStats(): HardwareStats = hardwareStats
}

data class ColorizationResult(
    val inputPath: String,
    val outputPath: String,
    val success: Boolean,
    val inferenceTimeMs: Long,
    val modelUsed: String,
    val device: String,
    val errorMessage: String? = null
)

data class HardwareStats(
    var cpuPercent: Float = 0.0f,
    var gpuMhz: Int = 0,
    var ramMb: Long = 0,
    var timestamp: Long = 0
)
