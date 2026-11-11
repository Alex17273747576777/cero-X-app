package com.ceroX.app.modules.reverse_engineering

import java.io.File

/**
 * AntigravityAnalyzer — Análisis de binarios APK.
 * Herramienta de ingeniería inversa integrada en cero-X.
 */
class AntigravityAnalyzer {

    /**
     * Lee el contenedor ZIP y dice hasta donde llega.
     *
     * Sin apktool ni dex2jar en el dispositivo no se desensambla nada, y antes se
     * devolvía "Análisis estático completado con antigravity" habiendo mirado el
     * tamano del archivo. Lo que sí se puede medir de verdad se mide: entradas,
     * DEX, manifiesto, bloques de firma y tamano sin comprimir. El estado es
     * `parcial`, que es lo que es.
     */
    fun analyzeApk(apkPath: String): BinaryAnalysisResult {
        val file = File(apkPath)
        if (!file.exists()) {
            return BinaryAnalysisResult(apkPath, "error", "Archivo no encontrado", null)
        }

        return try {
            java.util.zip.ZipFile(file).use { zip ->
                val entradas = zip.entries().toList()
                val dex = entradas.count { it.name.endsWith(".dex") }
                val manifiesto = entradas.any { it.name == "AndroidManifest.xml" }
                val firma = entradas.count {
                    it.name.startsWith("META-INF/") &&
                        (it.name.endsWith(".RSA") || it.name.endsWith(".DSA") ||
                            it.name.endsWith(".EC") || it.name.endsWith(".SF"))
                }
                val sinComprimir = entradas.sumOf { it.size }
                BinaryAnalysisResult(
                    target = apkPath,
                    status = "parcial",
                    details = "Contenedor leido: ${entradas.size} entradas, $dex DEX, manifiesto=$manifiesto, " +
                        "$firma bloques de firma. Sin desensamblado: no hay apktool ni dex2jar en el dispositivo.",
                    metadata = mapOf(
                        "file_size" to file.length(),
                        "entradas" to entradas.size,
                        "dex" to dex,
                        "manifiesto" to manifiesto,
                        "bloques_firma" to firma,
                        "bytes_sin_comprimir" to sinComprimir,
                        "analyzed_at" to System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            BinaryAnalysisResult(
                apkPath, "error",
                "No es un contenedor legible: " + (e.message ?: e.javaClass.simpleName), null
            )
        }
    }

    /**
     * Lee la cabecera DEX, que es lo que se puede leer sin desensamblador.
     *
     * Los contadores del formato estan en desplazamientos fijos: cadenas en 0x38,
     * metodos en 0x58 y clases en 0x60. Antes se devolvía "Análisis DEX
     * completado" habiendo mirado solo el tamano del archivo.
     */
    fun analyzeDex(dexPath: String): BinaryAnalysisResult {
        val file = File(dexPath)
        if (!file.exists()) {
            return BinaryAnalysisResult(dexPath, "error", "Archivo DEX no encontrado", null)
        }

        val cabecera = ByteArray(112)
        var leidos = 0
        try {
            file.inputStream().use { e ->
                while (leidos < cabecera.size) {
                    val n = e.read(cabecera, leidos, cabecera.size - leidos)
                    if (n < 0) break
                    leidos += n
                }
            }
        } catch (e: Exception) {
            return BinaryAnalysisResult(dexPath, "error",
                "No se pudo leer la cabecera: " + (e.message ?: e.javaClass.simpleName), null)
        }

        // La magia son los bytes 'd','e','x' seguidos de un salto de linea.
        if (leidos < cabecera.size ||
            cabecera[0] != 'd'.code.toByte() || cabecera[1] != 'e'.code.toByte() ||
            cabecera[2] != 'x'.code.toByte() || cabecera[3].toInt() != 10
        ) {
            return BinaryAnalysisResult(dexPath, "error", "El archivo no tiene la magia DEX", null)
        }

        fun enteroEn(off: Int): Int =
            (cabecera[off].toInt() and 0xFF) or
                ((cabecera[off + 1].toInt() and 0xFF) shl 8) or
                ((cabecera[off + 2].toInt() and 0xFF) shl 16) or
                ((cabecera[off + 3].toInt() and 0xFF) shl 24)

        val cadenas = enteroEn(56)
        val metodos = enteroEn(88)
        val clases = enteroEn(96)
        return BinaryAnalysisResult(
            target = dexPath,
            status = "parcial",
            details = "Cabecera DEX leida: $cadenas cadenas, $metodos metodos, $clases clases. Sin desensamblado.",
            metadata = mapOf(
                "file_size" to file.length(),
                "strings" to cadenas,
                "methods" to metodos,
                "classes" to clases
            )
        )
    }
}

data class BinaryAnalysisResult(
    val target: String,
    val status: String,
    val details: String,
    val metadata: Map<String, Any>?
)