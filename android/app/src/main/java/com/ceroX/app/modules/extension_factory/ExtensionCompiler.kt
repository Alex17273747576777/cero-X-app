package com.ceroX.app.modules.extension_factory

import java.io.File

/**
 * ExtensionCompiler — Compilador de extensiones a APK.
 */
class ExtensionCompiler {

    private var isEnabled = true

    /** Estado legible desde fuera sin abrir la puerta a escribirlo. */
    data class CompilerState(val enabled: Boolean)

    fun state(): CompilerState = CompilerState(isEnabled)

    fun initialize() {
        // Inicializar compilador
    }

    fun isEnabled(): Boolean = isEnabled

    /**
     * No se compila: no hay toolchain en el dispositivo.
     *
     * Devolvía `success = true` con la ruta de un `.jar` que nadie habia escrito.
     * Compilar una extension hace falta un JDK y Gradle, y cero-X no los lleva.
     */
    fun compileExtension(projectPath: String): CompileResult {
        val dir = File(projectPath)
        if (!dir.exists()) {
            return CompileResult(false, "Proyecto no encontrado", null)
        }
        return CompileResult(false,
            "No se compilo nada: cero-X no lleva JDK ni Gradle en el dispositivo. " +
                "Las extensiones se compilan fuera y se instalan como APK.", null)
    }

    /** Igual que `compileExtension`: no se genera ningun APK. */
    fun exportToApk(projectPath: String, outputPath: String): ExportResult {
        val dir = File(projectPath)
        if (!dir.exists()) {
            return ExportResult(false, "Proyecto no encontrado", null)
        }
        return ExportResult(false,
            "No se exporto ningun APK a $outputPath: no hay toolchain de compilacion en el dispositivo", null)
    }
}