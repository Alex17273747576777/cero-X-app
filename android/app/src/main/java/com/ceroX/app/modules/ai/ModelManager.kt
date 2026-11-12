package com.ceroX.app.modules.ai

import android.content.Context
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtEnvironment
import com.ceroX.app.core.MotorOnnx
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.FloatBuffer

/**
 * ModelManager — Gestor de modelos ONNX para IA on-device.
 * Basado en Kisara ONNX Runtime integration.
 */
class ModelManager(private val context: Context) {

    private val env: OrtEnvironment by lazy { MotorOnnx.entorno() }

    private var session: OrtSession? = null
    private val loadedModels = mutableMapOf<String, LoadedModel>()

    fun loadModel(modelPath: String, modelId: String, useNnapi: Boolean = true): Boolean {
        try {
            val file = File(modelPath)
            if (!file.exists()) return false

            val options = SessionOptions()
            options.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)

            if (useNnapi) {
                try {
                    options.addNnapi()
                } catch (e: Exception) {
                    // NNAPI no disponible, usar CPU
                }
            }

            session = env.createSession(file.absolutePath, options)
            loadedModels[modelId] = LoadedModel(modelId, modelPath, System.currentTimeMillis(), useNnapi)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Ejecuta el modelo y devuelve su primera salida, plana.
     *
     * Devolvía diez ceros fijos: `FloatArray(10) { 0.0f }`, sin tocar la sesion.
     * Ahora se lee la forma que declara el propio modelo, se comprueba que la
     * entrada tiene justo esos valores y se aplana la salida tal como venga.
     *
     * `null` significa "no se pudo", y las razones son concretas: modelo no
     * cargado, sesion sin entradas, forma con dimensiones dinamicas, entrada de
     * otro tamano o excepcion del runtime.
     */
    fun runInference(modelId: String, input: FloatArray): FloatArray? {
        val sesion = session ?: return null
        if (!loadedModels.containsKey(modelId)) return null

        val nombre = sesion.inputNames.firstOrNull() ?: return null
        val forma = (sesion.inputInfo[nombre]?.info as? TensorInfo)?.shape ?: return null

        var total = 1L
        for (d in forma) {
            // Una dimension negativa o cero es dinamica: sin forma no se crea el tensor.
            if (d <= 0L) return null
            total *= d
        }
        if (total != input.size.toLong()) return null

        return try {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), forma).use { t ->
                sesion.run(mapOf(nombre to t)).use { out -> aplanar(out.get(0).value) }
            }
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Pone en un `FloatArray` plano una salida que ONNX Runtime puede dar como
     * buffer o como array anidado. Las dos formas aparecen segun la version y el
     * tipo de tensor, y asumir una sola ya hizo fallar a los lectores de imagen.
     */
    private fun aplanar(valor: Any?): FloatArray? {
        val salida = mutableListOf<Float>()
        fun recorrer(v: Any?) {
            when (v) {
                is FloatBuffer -> {
                    val copia = v.duplicate()
                    while (copia.hasRemaining()) salida.add(copia.get())
                }
                is FloatArray -> for (x in v) salida.add(x)
                is Array<*> -> for (x in v) recorrer(x)
                else -> Unit
            }
        }
        recorrer(valor)
        return if (salida.isEmpty()) null else salida.toFloatArray()
    }

    fun listLoadedModels(): List<LoadedModel> = loadedModels.values.toList()

    fun unloadModel(modelId: String) {
        session?.close()
        session = null
        loadedModels.remove(modelId)
    }

    fun close() {
        session?.close()
        session = null
        loadedModels.clear()
    }
}

data class LoadedModel(
    val id: String,
    val path: String,
    val loadedAt: Long,
    val useNnapi: Boolean
)