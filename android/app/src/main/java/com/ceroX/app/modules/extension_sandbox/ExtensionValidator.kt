package com.ceroX.app.modules.extension_sandbox

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest

/** Verificación criptográfica más confianza explícita. Una firma cualquiera NO da confianza. */
class ExtensionValidator(private val trustedSignerSha256: Set<String> = emptySet()) {
    fun initialize() = Unit

    fun validate(apkPath: String): ValidationResult {
        val file = File(apkPath)
        val errors = mutableListOf<String>()
        fun invalid(message: String): ValidationResult = ValidationResult(false, listOf(message), emptyList())
        if (!file.isFile || !file.canRead()) return invalid("Archivo APK no encontrado o ilegible")
        if (!file.name.endsWith(".apk", ignoreCase = true)) return invalid("El archivo no es un APK")
        if (file.length() !in 1L..(100L * 1024 * 1024)) return invalid("APK vacío o superior a 100 MiB")
        val trusted = trustedSignerSha256.map { it.replace(":", "").lowercase() }.toSet()
        if (trusted.isEmpty() || trusted.any { !it.matches(Regex("[0-9a-f]{64}")) }) {
            return invalid("No hay certificados de extensiones aprobados. No se ejecuta código de terceros sin confianza explícita.")
        }
        return try {
            val verified = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(26).build().verify()
            if (!verified.isVerified || verified.signerCertificates.isEmpty()) {
                errors.add("La firma criptográfica del APK no es válida")
            } else {
                val signers = verified.signerCertificates.map { cert ->
                    MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                }
                if (signers.any { it !in trusted }) errors.add("Certificado de extensión no aprobado")
            }
            // No inventar permisos: este verificador no interpreta el manifiesto binario.
            ValidationResult(errors.isEmpty(), errors, emptyList())
        } catch (e: Exception) {
            invalid("No se pudo verificar criptográficamente el APK")
        }
    }
}
