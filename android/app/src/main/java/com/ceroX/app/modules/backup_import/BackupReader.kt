package com.ceroX.app.modules.backup_import

import java.io.File
import java.io.InputStream

/**
 * Lector de cabeceras de backup: la pieza que faltaba entre «tengo un archivo» y
 * `BackupFormatDetector.detect(bytes)`.
 *
 * POR QUÉ HACÍA FALTA
 * -------------------
 * `BackupFormatDetector` sabía clasificar 16 bytes, pero en el módulo no había
 * nada que se los diera: nadie abría el archivo. Eso significa que el detector
 * estaba escrito y **no había forma de llamarlo desde la app**. Un importador
 * sin lector de cabecera es una función a medias, y es el tipo de hueco que queda
 * cuando se escribe el trozo divertido y no el aburrido.
 *
 * Ahora lo aburrido está hecho, y con dos reglas que importan:
 *
 *   1. **Se leen 16 bytes, nunca el archivo entero.** Un backup de 400 MB no debe
 *      entrar en memoria solo para mirar sus primeros bytes. Ni 400 MB ni 400 KB.
 *   2. **El flujo se cierra siempre.** `use {}` a prueba de excepción: mil
 *      importaciones seguidas no pueden dejar descriptores abiertos.
 *
 * Devuelve `BackupFormatDetector.FORMAT_UNKNOWN` ante cualquier fallo, con el
 * motivo en `ultimoMotivo`. Un archivo ilegible no es un cierre inesperado: es
 * un archivo ilegible, y el usuario tiene derecho a saber cuál fue el problema.
 */
object BackupReader {

    /** Cuántos bytes se leen. 16 bastan para las cinco firmas que se conocen. */
    const val BYTES_CABECERA = 16

    /** Último motivo de fallo, para poder decírselo al usuario. */
    @Volatile
    var ultimoMotivo: String = ""
        private set

    /**
     * Clasifica un archivo leyendo solo su cabecera.
     *
     * @return uno de los `BackupFormatDetector.FORMAT_*`
     */
    fun clasificar(archivo: File): String {
        ultimoMotivo = ""

        if (!archivo.exists()) {
            ultimoMotivo = "el archivo no existe"
            return BackupFormatDetector.FORMAT_UNKNOWN
        }
        if (!archivo.isFile) {
            ultimoMotivo = "no es un archivo (¿es una carpeta?)"
            return BackupFormatDetector.FORMAT_UNKNOWN
        }
        if (!archivo.canRead()) {
            ultimoMotivo = "sin permiso de lectura"
            return BackupFormatDetector.FORMAT_UNKNOWN
        }
        if (archivo.length() == 0L) {
            ultimoMotivo = "el archivo está vacío (0 bytes)"
            return BackupFormatDetector.FORMAT_UNKNOWN
        }
        if (archivo.length() > BackupLimits.MAX_FILE_BYTES) {
            ultimoMotivo = "el archivo pasa del límite de " +
                "${BackupLimits.MAX_FILE_BYTES / (1024 * 1024)} MiB"
            return BackupFormatDetector.FORMAT_UNKNOWN
        }

        val cabecera = try {
            archivo.inputStream().use { leerCabecera(it) }
        } catch (e: OutOfMemoryError) {
            ultimoMotivo = "sin memoria al leer la cabecera"
            return BackupFormatDetector.FORMAT_UNKNOWN
        } catch (e: Exception) {
            ultimoMotivo = "no se pudo leer: ${e.javaClass.simpleName}"
            return BackupFormatDetector.FORMAT_UNKNOWN
        }

        val formato = BackupFormatDetector.detect(cabecera)
        if (formato == BackupFormatDetector.FORMAT_UNKNOWN) {
            ultimoMotivo = "no se reconoce el formato; " +
                BackupFormatDetector.explanation(cabecera)
        }
        return formato
    }

    /**
     * Lee como mucho [BYTES_CABECERA] bytes.
     *
     * No usa `readNBytes` porque en Android solo existe desde API 33 y esto
     * compila para API 26. Un bucle de `read` con acumulador es igual de correcto
     * y funciona en todas.
     */
    fun leerCabecera(entrada: InputStream, maximo: Int = BYTES_CABECERA): ByteArray {
        val buffer = ByteArray(maximo)
        var leidos = 0
        while (leidos < maximo) {
            val n = entrada.read(buffer, leidos, maximo - leidos)
            if (n <= 0) break          // -1 = fin de archivo; 0 no debería pasar
            leidos += n
        }
        return if (leidos == maximo) buffer else buffer.copyOf(leidos)
    }
}
