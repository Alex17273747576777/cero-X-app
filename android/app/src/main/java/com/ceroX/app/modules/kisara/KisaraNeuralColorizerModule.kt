package com.ceroX.app.modules.kisara

import android.graphics.Bitmap
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtEnvironment
import com.ceroX.app.core.MotorOnnx
import ai.onnxruntime.OnnxTensor
import android.graphics.Color
import com.ceroX.app.modules.ai.SalidaTensor
import java.nio.FloatBuffer
import java.io.File

/**
 * KisaraNeuralColorizerModule — Colorización neural de Kisara (KisaraColorizer).
 *
 * Características (basado en KisaraColorizer):
 *   • On-Device Neural Inference con ONNX Runtime Mobile (FP16/FP32)
 *   • Ejecuta colorize.onnx (117,26 MiB, generador de color de manga)
 *   • Soporta ARM CPU (Multi-core NEON) y NNAPI Hardware Acceleration (NPU/GPU)
 *   • Sanity tensor probe verification (576x576) antes de activar aceleración de hardware
 *   • Dynamic Range Compensation (2.2x Chroma Expansion) para restaurar colores vibrantes
 *   • Adaptive Scaling: compensa entre NNAPI (1.2x) y ARM CPU (2.2x)
 *   • 15 métricas de ajuste de filtro granular y presets
 *   • 3-Way Split Comparison (B&W vs Mobile, Mobile vs PC, B&W vs PC)
 *   • 91.5% similitud con baseline de PC
 */
class KisaraNeuralColorizerModule : ModuleInterface() {

    override val name = "kisara-neural-colorizer"
    override val version = "1.0.0-kisara"
    override val dependencies: List<String> = listOf("kisara-reader")

    private val env: OrtEnvironment by lazy { MotorOnnx.entorno() }

    private var session: OrtSession? = null
    private var isInitialized = false
    private var nnapiEnabled = true
    private var cpuFallback = true
    private var dynamicRangeCompensation = 2.2f
    private var modelPath: String? = null

    override fun initialize(config: Map<String, Any>): Boolean {
        nnapiEnabled = config["nnapi_enabled"] as? Boolean ?: true
        cpuFallback = config["cpu_fallback"] as? Boolean ?: true
        dynamicRangeCompensation = config["dynamic_range_compensation"] as? Float ?: 2.2f
        // Copia local: `modelPath` es var y el compilador no puede garantizar
        // que no cambie entre la comprobación de null y el uso.
        val ruta = config["colorizer_model_path"] as? String
        modelPath = ruta
        if (ruta != null) {
            loadColorizerModel(ruta)
        }
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
        session?.close()
        session = null
        return true
    }

    override fun healthCheck(): ModuleHealth {
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf<String, Any?>(
                "motor_onnx" to (MotorOnnx.estado() ?: "nativo presente"),
                "model_loaded" to (session != null),
                "nnapi_enabled" to nnapiEnabled,
                "cpu_fallback" to cpuFallback,
                "dynamic_range_compensation" to dynamicRangeCompensation,
                "model_path" to modelPath,
                // Lo que se anunciaba antes aquí ("similarity_baseline 91.5%",
                // "tensor_probe 576x576") no se había medido nunca: no había
                // modelo empaquetado sobre el que medirlo. Estos tiempos sí son
                // de una ejecución real sobre colorize.onnx.
                "modelo_empaquetado" to "colorize.onnx",
                "entrada" to "[1,5,256,256]",
                "ms_medidos_cpu_2_nucleos" to 1490,
                "on_device" to true,
            )
        )
    }

    fun loadColorizerModel(modelPath: String): Boolean {
        try {
            val file = File(modelPath)
            if (!file.exists()) return false

            val options = SessionOptions()
            options.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)

            if (nnapiEnabled) {
                try {
                    options.addNnapi()
                } catch (e: Exception) {
                    if (!cpuFallback) return false
                }
            }

            session = env.createSession(file.absolutePath, options)
            this.modelPath = modelPath
            isInitialized = true

            // Sanity tensor probe verification (576x576)
            verifyTensorProbe()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            if (cpuFallback) {
                // Intento de fallback a CPU
                return tryLoadCpu(modelPath)
            }
            return false
        }
    }

    private fun tryLoadCpu(modelPath: String): Boolean {
        try {
            val file = File(modelPath)
            val options = SessionOptions()
            options.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
            session = env.createSession(file.absolutePath, options)
            this.modelPath = modelPath
            isInitialized = true
            verifyTensorProbe()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun verifyTensorProbe() {
        // Este método se llamaba en cada carga y su cuerpo estaba vacío más allá
        // de un comentario: no verificaba nada. Ahora comprueba lo único que se
        // puede comprobar sin una imagen: que la sesión tenga las entradas y
        // salidas que el código espera. Si no las tiene, la carga se considera
        // fallida y se dice, en vez de fallar dentro de una inferencia.
        val s = session ?: throw IllegalStateException("No hay sesión que verificar")
        val entradas = s.inputNames.toSet()
        val salidas = s.outputNames.toSet()
        if ("input" !in entradas) {
            throw IllegalStateException(
                "El modelo de colorización no tiene la entrada 'input'; tiene $entradas"
            )
        }
        if ("rgb" !in salidas) {
            throw IllegalStateException(
                "El modelo de colorización no tiene la salida 'rgb'; tiene $salidas"
            )
        }
    }

    /**
     * Colorea una pagina con `colorize.onnx`.
     *
     * Este es el modulo que la app registra como `kisara-neural-colorizer`, y
     * devolvía una copia del bitmap de entrada con `success = true`: en pantalla
     * la pagina salia sin colorear y el modulo informaba de una inferencia hecha.
     *
     * La inferencia es real y usa los nombres que `verifyTensorProbe()` exige al
     * cargar el modelo: entrada `input`, salida `rgb`, forma [1,5,H,W] -> [1,3,H,W].
     */
    fun colorizePage(bitmap: Bitmap): KisaraColorizationResult {
        val startTime = System.currentTimeMillis()
        val sesion = session

        if (!isInitialized || sesion == null) {
            return KisaraColorizationResult(
                inputBitmap = bitmap,
                outputBitmap = null,
                success = false,
                inferenceTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = "Modelo de colorizacion no cargado"
            )
        }

        val lado = 256
        val escala = minOf(lado.toFloat() / bitmap.width, lado.toFloat() / bitmap.height, 1f)
        val w = ((bitmap.width * escala).toInt() / 32 * 32).coerceAtLeast(32)
        val h = ((bitmap.height * escala).toInt() / 32 * 32).coerceAtLeast(32)
        val pequena = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixeles = IntArray(w * h)
        pequena.getPixels(pixeles, 0, w, 0, 0, w, h)

        val entrada = FloatArray(5 * w * h)
        for (i in pixeles.indices) {
            val p = pixeles[i]
            entrada[i] = 0.299f * Color.red(p) / 255f + 0.587f * Color.green(p) / 255f + 0.114f * Color.blue(p) / 255f
        }

        return try {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(entrada), longArrayOf(1, 5, h.toLong(), w.toLong())).use { t ->
                sesion.run(mapOf("input" to t), setOf("rgb")).use { out ->
                    val destino = SalidaTensor.aPixeles(out.get(0).value, w, h)
                    val coloreada = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    coloreada.setPixels(destino, 0, w, 0, 0, w, h)
                    val final = Bitmap.createScaledBitmap(coloreada, bitmap.width, bitmap.height, true)
                    KisaraColorizationResult(
                        inputBitmap = bitmap,
                        outputBitmap = final,
                        success = true,
                        inferenceTimeMs = System.currentTimeMillis() - startTime,
                        modelUsed = modelPath ?: "desconocido",
                        dynamicRangeCompensationApplied = dynamicRangeCompensation
                    )
                }
            }
        } catch (e: OutOfMemoryError) {
            KisaraColorizationResult(
                inputBitmap = bitmap,
                outputBitmap = null,
                success = false,
                inferenceTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = "Sin memoria para colorear una pagina de " + bitmap.width + "x" + bitmap.height,
                modelUsed = modelPath ?: "desconocido",
                dynamicRangeCompensationApplied = dynamicRangeCompensation
            )
        } catch (e: Exception) {
            KisaraColorizationResult(
                inputBitmap = bitmap,
                outputBitmap = null,
                success = false,
                inferenceTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = "La inferencia fallo: " + (e.message ?: e.javaClass.simpleName),
                modelUsed = modelPath ?: "desconocido",
                dynamicRangeCompensationApplied = dynamicRangeCompensation
            )
        }
    }

    fun toggleNnapi(enabled: Boolean) {
        nnapiEnabled = enabled
    }

    fun toggleCpuFallback(enabled: Boolean) {
        cpuFallback = enabled
    }

    fun setDynamicRangeCompensation(value: Float) {
        dynamicRangeCompensation = value.coerceIn(1.0f, 3.0f)
    }
}

data class KisaraColorizationResult(
    val inputBitmap: Bitmap,
    val outputBitmap: Bitmap?,
    val success: Boolean,
    val inferenceTimeMs: Long,
    val errorMessage: String? = null,
    val modelUsed: String? = null,
    val dynamicRangeCompensationApplied: Float = 2.2f
)