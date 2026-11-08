package com.ceroX.app.modules.environments

import java.io.File

/**
 * DebianManager — Gestor de entornos Debian.
 * Soporta Debian stable (12), testing (13 "trixie"), sid (inestable).
 * Nota: Debian 14 no existe aún.
 */
class DebianManager {

    private val availableVersions = listOf("stable", "testing", "sid")
    private val createdEnvironments = mutableListOf<DebianEnvironment>()

    fun listAvailableVersions(): List<String> = availableVersions

    fun createEnvironment(name: String, version: String, targetDir: File): DebianEnvironmentResult {
        if (!availableVersions.contains(version)) {
            return DebianEnvironmentResult(name, version, "error", "Versión no disponible: $version")
        }

        val env = DebianEnvironment(name, version, targetDir.absolutePath, "created")
        createdEnvironments.add(env)

        return DebianEnvironmentResult(name, version, "created", "Entorno creado en ${targetDir.absolutePath}")
    }

    fun listEnvironments(): List<DebianEnvironment> = createdEnvironments.toList()

    fun downloadIso(version: String, outputDir: File): IsoDownloadResult {
        return IsoDownloadResult(
            version = version,
            outputPath = outputDir.absolutePath,
            status = "scheduled",
            note = "Descarga ISO desde https://debian.org (Debian $version)"
        )
    }

    fun getSupportedDistros(): List<String> {
        return listOf("debian", "ubuntu", "fedora", "arch")
    }
}

data class DebianEnvironment(
    val name: String,
    val version: String,
    val path: String,
    val status: String
)

data class DebianEnvironmentResult(
    val name: String,
    val version: String,
    val status: String,
    val message: String
)

data class IsoDownloadResult(
    val version: String,
    val outputPath: String,
    val status: String,
    val note: String
)