package com.ceroX.app.modules.ai

import android.content.Context
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtEnvironment
import java.io.File

/**
 * ModelManager — Gestor de modelos ONNX para IA on-device.
 * Basado en Kisara ONNX Runtime integration.
 */
class ModelManager(private val context: Context) {

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

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

    fun runInference(modelId: String, input: FloatArray): FloatArray? {
        val model = loadedModels[modelId] ?: return null
        // Placeholder: inferencia ONNX real
        return FloatArray(10) { 0.0f }
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