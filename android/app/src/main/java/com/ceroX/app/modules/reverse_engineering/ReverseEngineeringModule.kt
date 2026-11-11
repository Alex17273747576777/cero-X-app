package com.ceroX.app.modules.reverse_engineering

import android.content.Context
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import java.io.File

class ReverseEngineeringModule : ModuleInterface() {

    override val name = "reverse-engineering"
    override val version = "0.1.0"
    override val dependencies: List<String> = emptyList()

    private val antigravityEnabled = true
    private val deepseekHarnessEnabled = true
    private val autoClone = true
    private val analysisResults = mutableListOf<AnalysisResult>()

    override fun initialize(config: Map<String, Any>): Boolean {
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
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf(
                "antigravity" to antigravityEnabled,
                "deepseek_harness" to deepseekHarnessEnabled,
                "analyses" to analysisResults.size
            )
        )
    }

    /**
     * Delega en AntigravityAnalyzer, que lee el contenedor de verdad.
     *
     * El detalle "Static analysis performed with antigravity" se devolvía con
     * `completed` sin haber ejecutado ningun analisis.
     */
    fun analyzeBinary(binaryPath: String): AnalysisResult {
        if (!_started) {
            return AnalysisResult(binaryPath, "error", "Engine not started")
        }
        val r = AntigravityAnalyzer().analyzeApk(binaryPath)
        val result = AnalysisResult(binaryPath, r.status, r.details)
        analysisResults.add(result)
        return result
    }

    /**
     * No se clona nada: no hay git en el dispositivo.
     *
     * Devolvía `status = "cloned"` con la herramienta "git" sin ejecutar nada. La
     * clonacion de los repositorios del ecosistema se hace en el ordenador, con
     * `scripts/clone_all_repos.sh`.
     */
    fun cloneEcosystem(sourceUrl: String, targetDir: String): CloneResult {
        if (!_started || !autoClone) {
            return CloneResult(sourceUrl, targetDir, "error", "Auto-clone disabled")
        }
        return CloneResult(
            sourceUrl, targetDir, "error",
            "No se puede clonar en el dispositivo: no hay git y cero-X no lleva un entorno Linux. " +
                "La clonacion se hace en el ordenador con scripts/clone_all_repos.sh"
        )
    }

    /**
     * No hay arnes de evaluacion: no se ejecuta ningun benchmark.
     *
     * Devolvía `status = "evaluated"` con puntuacion 0.0, que se leía como "se
     * evaluó y saco un cero", no como "no se evaluo".
     */
    fun evaluateWithDeepSeek(modelPath: String, benchmark: String): EvaluationResult {
        if (!_started || !deepseekHarnessEnabled) {
            return EvaluationResult(modelPath, benchmark, "error", 0.0)
        }
        return EvaluationResult(modelPath, benchmark, "sin_evaluar", 0.0)
    }
}

data class AnalysisResult(
    val binary: String,
    val status: String,
    val details: String
)

data class CloneResult(
    val source: String,
    val target: String,
    val status: String,
    val method: String
)

data class EvaluationResult(
    val model: String,
    val benchmark: String,
    val status: String,
    val score: Double
)