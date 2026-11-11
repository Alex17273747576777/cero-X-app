package com.ceroX.app.modules.backup

import com.ceroX.app.modules.backup_import.BackupFormatDetector
import com.ceroX.app.modules.backup_import.BackupLimits
import com.ceroX.app.modules.backup_import.BackupReader
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * Índice de un backup: qué contiene, antes de escribir nada.
 *
 * EL AGUJERO QUE TAPA
 * -------------------
 * `BackupManager.restoreBackup()` hacía esto:
 *
 *     if (file.exists()) RestoreResult(success = true, restoredItems = 0)
 *
 * Es decir: comprobaba que el archivo existiera y **declaraba la restauración
 * exitosa sin restaurar nada**. `restoredItems = 0` estaba escrito a mano con el
 * comentario "Relleno" al lado. El usuario veía "Backup restaurado" y su
 * biblioteca seguía igual — o peor, si el flujo de escritura se hubiera añadido
 * encima de esa base sin el índice.
 *
 * Restaurar a ciegas sobre una biblioteca de años es cómo se pierde la
 * biblioteca. El patrón que usan houri (`BackupDecoder` + `BackupIntegrityHandler`)
 * y Kototoro (`BackupIndexFactory`) es siempre el mismo y es el correcto:
 *
 *   1. leer el archivo y decir QUÉ CONTIENE y en qué versión de esquema
 *   2. decodificar a objetos, fallando claro si el esquema no coincide
 *   3. solo entonces escribir
 *
 * Esta clase es el paso 1. Sin él, los pasos 2 y 3 no tienen sobre qué decidir.
 *
 * QUÉ NO HACE
 * -----------
 * No decodifica el contenido de la biblioteca: eso es `BackupDecoder`, que
 * necesita los esquemas internos. Esta clase responde a las preguntas que se
 * pueden responder **sin confiar en el contenido**: qué formato es, cuántas
 * entradas trae, cuánto ocupa descomprimido y si eso cabe en el límite.
 */
object BackupIndex {

    /** Lo que se sabe de un backup sin haberlo aplicado. */
    data class Indice(
        val archivo: String,
        val bytes: Long,
        val formato: String,
        /** Entradas del zip/archivo. -1 si el formato no las tiene. */
        val entradas: Int,
        /** Bytes descomprimidos totales, o -1 si no se puede saber sin descomprimir. */
        val bytesDescomprimidos: Long,
        /** Nombres de lo que contiene, con tope. */
        val nombres: List<String>,
        /** Por qué no se puede restaurar. Vacío si se puede. */
        val problemas: List<String>,
    ) {
        val restaurable: Boolean get() = problemas.isEmpty()
    }

    /** Cuántos nombres se guardan en el índice. Con 200 basta para reconocerlo. */
    private const val MAX_NOMBRES = 200

    /**
     * Tamaño descomprimido a partir del cual un backup se considera sospechoso.
     *
     * POR QUÉ NO SE USA `MAX_INFLATED_BYTES` (512 MiB)
     * ------------------------------------------------
     * Ese es el límite del FORMATO: lo máximo que el importador aceptaría
     * descomprimir. Usarlo aquí era un error de razonamiento que encontró la
     * prueba con una bomba real: 220 MiB de ceros comprimidos en 219 KB pasaban
     * el filtro porque "solo" eran 220 MiB de 512.
     *
     * Una biblioteca de 10.000 obras con sus capítulos no llega a 128 MiB
     * descomprimidos. Por encima de eso no es un backup: es una anomalía, y
     * descomprimirla para averiguarlo es justo lo que hay que evitar.
     */
    private const val UMBRAL_SOSPECHOSO = 128L * 1024 * 1024

    /**
     * Indexa un archivo de backup.
     *
     * No lanza: devuelve un índice con `problemas` lleno. Un archivo corrupto es
     * un dato, no una excepción que haya que atrapar en tres sitios.
     */
    fun indexar(archivo: File): Indice {
        val problemas = mutableListOf<String>()

        if (!archivo.exists()) {
            return vacio(archivo, "el archivo no existe")
        }
        if (!archivo.isFile) {
            return vacio(archivo, "no es un archivo (¿es una carpeta?)")
        }
        if (!archivo.canRead()) {
            return vacio(archivo, "sin permiso de lectura")
        }
        if (archivo.length() == 0L) {
            return vacio(archivo, "el archivo está vacío (0 bytes)")
        }
        if (archivo.length() > BackupLimits.MAX_FILE_BYTES) {
            return vacio(
                archivo,
                "ocupa ${archivo.length() / (1024 * 1024)} MiB, por encima del límite de " +
                    "${BackupLimits.MAX_FILE_BYTES / (1024 * 1024)} MiB"
            )
        }

        val formato = BackupReader.clasificar(archivo)
        if (formato == BackupFormatDetector.FORMAT_UNKNOWN) {
            return vacio(archivo, "formato no reconocido: ${BackupReader.ultimoMotivo}")
        }

        var entradas = -1
        var descomprimido = -1L
        var nombres: List<String> = emptyList()

        when (formato) {
            BackupFormatDetector.FORMAT_ZIP -> {
                try {
                    ZipFile(archivo).use { zip ->
                        val lista = zip.entries().toList()
                        entradas = lista.size
                        // `size` de la entrada es el tamaño DESCOMPRIMIDO declarado.
                        // Se puede mentir (zip bomb), así que además se comprueba
                        // que el total no pase del límite antes de descomprimir.
                        descomprimido = lista.sumOf { e -> if (e.size < 0) 0L else e.size }
                        nombres = lista.take(MAX_NOMBRES).map { it.name }

                        if (entradas > BackupLimits.MAX_ZIP_ENTRIES) {
                            problemas.add(
                                "el zip trae $entradas entradas, límite ${BackupLimits.MAX_ZIP_ENTRIES}"
                            )
                        }
                        // Se avisa igualmente si pasa de MAX_INFLATED_BYTES, pero el
                        // corte real de "esto es una bomba" es UMBRAL_SOSPECHOSO.
                        val limite = minOf(BackupLimits.MAX_INFLATED_BYTES, UMBRAL_SOSPECHOSO)
                        if (descomprimido > limite) {
                            problemas.add(
                                "descomprimido declara ${descomprimido / (1024 * 1024)} MiB a " +
                                    "partir de ${archivo.length() / 1024} KB en disco, límite " +
                                    "${limite / (1024 * 1024)} MiB — parece una bomba de " +
                                    "descompresión"
                            )
                        }
                        if (entradas == 0) problemas.add("el zip está vacío")
                    }
                } catch (e: Exception) {
                    problemas.add("no se pudo leer el zip: ${e.javaClass.simpleName}")
                }
            }

            BackupFormatDetector.FORMAT_GZIP -> {
                // Un .tachibk es gzip de un protobuf: una sola entrada lógica.
                entradas = 1
                // No se descomprime para indexar: sería gastar tiempo y memoria en
                // algo que el decodificador hará después. Solo se comprueba que el
                // flujo gzip abra y dé algo, que es lo que detecta una cabecera
                // truncada.
                try {
                    GZIPInputStream(archivo.inputStream().buffered()).use { gz ->
                        // LEER HASTA EL FINAL, no solo el primer bloque.
                        // La primera versión leía 1 KB y daba el gzip por bueno: un
                        // archivo truncado a 20 bytes devolvía bytes de la parte que
                        // sí está comprimida y el `EOFException` de "el flujo acabó
                        // antes del marcador de fin" solo salta al llegar al final.
                        // Es exactamente el caso que hay que detectar: un backup a
                        // medio descargar.
                        val buf = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val n = gz.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > UMBRAL_SOSPECHOSO) {
                                problemas.add(
                                    "el gzip pasa de ${UMBRAL_SOSPECHOSO / (1024 * 1024)} MiB " +
                                        "descomprimido — se corta la lectura por seguridad"
                                )
                                return@use
                            }
                        }
                        if (total == 0L) problemas.add("el gzip abre pero no da ningún byte")
                    }
                } catch (e: java.io.EOFException) {
                    problemas.add(
                        "el gzip está truncado: el flujo acaba antes del marcador de fin " +
                            "(backup a medio descargar o a medio copiar)"
                    )
                } catch (e: java.util.zip.ZipException) {
                    problemas.add("el gzip está corrupto o truncado: ${e.message}")
                } catch (e: Exception) {
                    problemas.add("no se pudo leer el gzip: ${e.javaClass.simpleName}")
                }
            }

            BackupFormatDetector.FORMAT_SQLITE -> {
                // Restaurar un SQLite encima de la base de datos de la app no es
                // "restaurar": es reemplazar. Y sin verificar el esquema, es
                // reemplazar por algo que puede ser de otra versión.
                problemas.add(
                    "es una base de datos SQLite suelta: se puede importar, pero no " +
                        "restaurar encima sin verificar el esquema, y eso no está hecho"
                )
            }

            BackupFormatDetector.FORMAT_JSON -> {
                entradas = 1
                problemas.add(
                    "JSON reconocido; falta el decodificador de este esquema concreto"
                )
            }

            else -> {
                problemas.add("formato '$formato' reconocido pero sin índice implementado")
            }
        }

        return Indice(
            archivo = archivo.absolutePath,
            bytes = archivo.length(),
            formato = formato,
            entradas = entradas,
            bytesDescomprimidos = descomprimido,
            nombres = nombres,
            problemas = problemas,
        )
    }

    private fun vacio(archivo: File, motivo: String) = Indice(
        archivo = archivo.absolutePath,
        bytes = if (archivo.exists()) archivo.length() else 0,
        formato = BackupFormatDetector.FORMAT_UNKNOWN,
        entradas = -1,
        bytesDescomprimidos = -1,
        nombres = emptyList(),
        problemas = listOf(motivo),
    )
}
