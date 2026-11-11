package com.ceroX.app.modules.extension_factory

import java.io.File

/**
 * ExtensionBuilder — Constructor de extensiones (UI-less, para uso programático).
 * Permite construir extensiones paso a paso.
 */
class ExtensionBuilder {

    fun initialize() {
        // Inicializar constructor
    }

    /**
     * Comprueba la estructura del proyecto y ahi se queda.
     *
     * Devolvía `success = true` con la ruta de un `app-debug.apk` inexistente. La
     * comprobacion de que haya manifiesto y build.gradle sí es real, y se
     * conserva; lo que no se hace es construir nada.
     */
    fun buildExtension(projectDir: String): BuildResult {
        val dir = File(projectDir)
        if (!dir.exists()) {
            return BuildResult(false, "Directorio de proyecto no encontrado", null)
        }

        val manifest = File(dir, "src/main/AndroidManifest.xml")
        val buildGradle = File(dir, "build.gradle")

        if (!manifest.exists() || !buildGradle.exists()) {
            return BuildResult(false, "Estructura de proyecto incompleta", null)
        }

        return BuildResult(false,
            "El proyecto tiene la estructura correcta, pero no se construyo: " +
                "hace falta Gradle y cero-X no lo lleva en el dispositivo", null)
    }
}