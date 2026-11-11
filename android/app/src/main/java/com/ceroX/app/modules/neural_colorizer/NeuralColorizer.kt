package com.ceroX.app.modules.neural_colorizer

import android.graphics.Bitmap
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtEnvironment
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import com.ceroX.app.modules.ai.SalidaTensor
import java.io.File
import java.nio.FloatBuffer

/**
 * NeuralColorizer — Colorización neural de manga con modelos ONNX quantizados.
 * Basado en PineappleTwilight/houri KisaraColorizer.
 */
class NeuralColorizer {

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private var session: OrtSession? = null
    private var isInitialized = false

    fun loadColorizerModel(modelPath: String): Boolean {
        try {
            val file = File(modelPath)
            if (!file.exists()) return false

            val options = SessionOptions()
            options.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)

            try {
                options.addNnapi()
            } catch (e: Exception) {
                // Fallback a CPU
            }

            session = env.createSession(file.absolutePath, options)
            isInitialized = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Colorea una pagina con el modelo cargado.
     *
     * Antes devolvia una copia del bitmap de entrada con `success = true`: la
     * pagina salia igual de gris y la app decia que la habia coloreado. Aqui se
     * ejecuta la inferencia de verdad, con el mismo contrato que se midio contra
     * `colorize.onnx`: entrada [1,5,H,W], salida [1,3,H,W].
     *
     * Los cinco canales de entrada no son cinco colores: luminancia, las dos
     * componentes cromaticas y dos guias. Las cuatro ultimas van a cero, que es
     * como se verifico el modelo.
     */
    fun colorizePage(bitmap: Bitmap, modelPath: String): ColorizationResult {
        val startTime = System.currentTimeMillis()
        val sesion = session

        if (!isInitialized || sesion == null) {
            return ColorizationResult(
                inputBitmap = bitmap,
                outputBitmap = null,
                success = false,
                inferenceTimeMs = 0,
                errorMessage = "Modelo no cargado"
            )
        }

        // La red pide multiplos de 32 y una pagina a resolucion completa tarda
        // minutos: se reduce, se colorea y se devuelve al tamano original.
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
            // Luminancia Rec. 601: el ojo pesa el verde mucho mas que el azul.
            entrada[i] = 0.299f * Color.red(p) / 255f + 0.587f * Color.green(p) / 255f + 0.114f * Color.blue(p) / 255f
        }

        return try {
            val nombre = sesion.inputNames.firstOrNull()
            if (nombre == null) {
                ColorizationResult(bitmap, null, false, System.currentTimeMillis() - startTime,
                    "El modelo no declara ninguna entrada")
            } else {
                OnnxTensor.createTensor(env, FloatBuffer.wrap(entrada), longArrayOf(1, 5, h.toLong(), w.toLong())).use { t ->
                    sesion.run(mapOf(nombre to t)).use { out ->
                        // La salida tiene cuatro niveles, no tres; SalidaTensor la
                        // lee comprobando la forma en vez de suponerla.
                        val destino = SalidaTensor.aPixeles(out.get(0).value, w, h)
                        val coloreada = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        coloreada.setPixels(destino, 0, w, 0, 0, w, h)
                        val final = Bitmap.createScaledBitmap(coloreada, bitmap.width, bitmap.height, true)
                        ColorizationResult(
                            inputBitmap = bitmap,
                            outputBitmap = final,
                            success = true,
                            inferenceTimeMs = System.currentTimeMillis() - startTime,
                            modelUsed = modelPath
                        )
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            ColorizationResult(bitmap, null, false, System.currentTimeMillis() - startTime,
                "Sin memoria para colorear una pagina de " + bitmap.width + "x" + bitmap.height)
        } catch (e: Exception) {
            ColorizationResult(bitmap, null, false, System.currentTimeMillis() - startTime,
                "La inferencia fallo: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    fun close() {
        session?.close()
        session = null
        isInitialized = false
    }
}

data class ColorizationResult(
    val inputBitmap: Bitmap,
    val outputBitmap: Bitmap?,
    val success: Boolean,
    val inferenceTimeMs: Long,
    val errorMessage: String? = null,
    val modelUsed: String? = null
)