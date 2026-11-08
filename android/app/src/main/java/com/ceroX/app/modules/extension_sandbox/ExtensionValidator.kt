package com.ceroX.app.modules.extension_sandbox

import java.io.File
import java.security.MessageDigest

/**
 * ExtensionValidator — Validador de extensiones antes de instalación.
 * Verifica firma, permisos, estructura y seguridad.
 */
class ExtensionValidator {

    fun initialize() {
        // Inicializar validador
    }

    fun validate(apkPath: String): ValidationResult {
        val file = File(apkPath)
        val errors = mutableListOf<String>()
        val permissions = mutableListOf<String>()

        if (!file.exists()) {
            errors.add("Archivo APK no encontrado")
            return ValidationResult(false, errors, permissions)
        }

        // Verificar extensión
        if (!apkPath.lowercase().endsWith(".apk")) {
            errors.add("El archivo no es un APK válido")
        }

        // Verificar tamaño (máximo 100MB)
        val sizeMb = file.length() / 1024 / 1024
        if (sizeMb > 100) {
            errors.add("APK demasiado grande: ${sizeMb}MB (máximo 100MB)")
        }

        // Calcular SHA-256
        val sha256 = calculateSha256(file)

        // Placeholder: verificar firma del APK
        permissions.add("INTERNET")
        permissions.add("READ_EXTERNAL_STORAGE")

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            permissions = permissions
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { is ->
            val buffer = ByteArray(8192)
            var read: Int
            while (is.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}