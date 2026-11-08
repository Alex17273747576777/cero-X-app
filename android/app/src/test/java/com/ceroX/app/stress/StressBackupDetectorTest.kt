package com.ceroX.app.stress

import com.ceroX.app.modules.backup_import.BackupFormatDetector
import com.ceroX.app.modules.backup_import.BackupReader
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * Estrés del detector de backups: 4 000 entradas que no son backups.
 *
 * El detector se ejecuta sobre archivos que vienen de fuera —el usuario elige un
 * archivo de su tarjeta— y es la primera pieza que toca datos no confiables.
 * Un `IndexOutOfBoundsException` aquí es un cierre inesperado; un bucle infinito,
 * una app colgada. Ninguna de las dos puede pasar.
 *
 * Qué se le mete:
 *   - archivos vacíos y de 1 byte
 *   - ruido aleatorio de 0 a 4 KB (4 000 casos con semilla fija, para que falle
 *     siempre en el mismo sitio y el fallo sea reproducible)
 *   - cabeceras truncadas de los formatos reales (gzip que empieza y no sigue)
 *   - firmas válidas seguidas de basura
 *   - archivos enormes de un solo byte repetido (¿agota memoria?)
 *
 * La semilla es constante a propósito: una prueba de estrés que falla una vez de
 * cada diez ejecuciones no se puede depurar.
 */
class StressBackupDetectorTest {

    private val azar = Random(20261108L)
    private val carpeta = File(System.getProperty("java.io.tmpdir"), "cerox-stress").apply { mkdirs() }

    private fun archivo(bytes: ByteArray): File {
        val f = File(carpeta, "caso_${System.nanoTime()}_${azar.nextInt()}.bin")
        f.writeBytes(bytes)
        return f
    }

    @Test
    fun `4000 archivos de ruido no lo tiran`() {
        val formatos = mutableMapOf<String, Int>()
        repeat(4000) { i ->
            val tam = when (i % 4) {
                0 -> 0
                1 -> 1
                2 -> azar.nextInt(64)
                else -> azar.nextInt(4096)
            }
            val datos = ByteArray(tam).also { azar.nextBytes(it) }
            val f = archivo(datos)
            try {
                // Se pasa por el lector real (cabecera de 16 bytes), que es el
                // camino que usa la app. Antes se llamaba a un `detectar(File)`
                // que no existía: la prueba no compilaba y el hueco era real.
                val detectado = BackupReader.clasificar(f)
                val clave = detectado
                formatos[clave] = (formatos[clave] ?: 0) + 1
            } catch (e: Throwable) {
                throw AssertionError(
                    "explotó con ${tam} bytes de ruido (caso $i): ${e::class.simpleName}: ${e.message}",
                    e
                )
            } finally {
                f.delete()
            }
        }
        assertNotNull(formatos)
        println("  formatos detectados en 4000 ruidos: $formatos")
    }

    @Test
    fun `firmas validas truncadas no lo tiran`() {
        val firmas = listOf(
            byteArrayOf(0x1F, 0x8B.toByte()),                          // gzip
            byteArrayOf(0x50, 0x4B, 0x03, 0x04),                        // zip
            "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII),
            byteArrayOf(0x7B),                                          // {
            byteArrayOf(0x5B),                                          // [
        )
        firmas.forEach { firma ->
            for (extra in 0..32) {
                val datos = firma + ByteArray(extra) { azar.nextInt(256).toByte() }
                val f = archivo(datos)
                try {
                    BackupReader.clasificar(f)
                } catch (e: Throwable) {
                    throw AssertionError(
                        "explotó con firma ${firma.joinToString(" ") { "%02X".format(it) }} + $extra bytes: " +
                            "${e::class.simpleName}: ${e.message}", e
                    )
                } finally {
                    f.delete()
                }
            }
        }
    }

    @Test
    fun `archivos de 16 MiB de un solo byte no agotan memoria`() {
        listOf(0x00, 0xFF, 0x41, 0x1F).forEach { b ->
            val f = archivo(ByteArray(16 * 1024 * 1024) { b.toByte() })
            try {
                val t0 = System.currentTimeMillis()
                val d = BackupReader.clasificar(f)
                val ms = System.currentTimeMillis() - t0
                assertTrue(
                    "16 MiB de byte ${"%02X".format(b)} tardaron $ms ms: el detector no debe leer el archivo entero",
                    ms < 2000
                )
                println("  16 MiB de ${"%02X".format(b)}: $d en $ms ms")
            } finally {
                f.delete()
            }
        }
    }

    @Test
    fun `un archivo que no existe no lanza excepcion`() {
        val fantasma = File(carpeta, "no_existe_${System.nanoTime()}.bak")
        val d = BackupReader.clasificar(fantasma)
        assertTrue(
            "un archivo inexistente debe dar FORMAT_UNKNOWN, no excepción; dio $d",
            d == BackupFormatDetector.FORMAT_UNKNOWN
        )
        assertTrue("y debe explicar por qué", BackupReader.ultimoMotivo.isNotBlank())
    }

    @Test
    fun `mil archivos seguidos no filtran descriptores`() {
        // Si el detector abriera un FileInputStream y no lo cerrara, a las pocas
        // centenas fallaría con "Too many open files". Es el fallo clásico de un
        // detector de formatos.
        repeat(1000) { i ->
            val f = archivo(
                when (i % 3) {
                    0 -> byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00)
                    1 -> byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14)
                    else -> byteArrayOf(0x7B, 0x22, 0x61, 0x22)
                }
            )
            try {
                BackupReader.clasificar(f)
            } finally {
                f.delete()
            }
        }
        assertTrue(
            "1000 aperturas seguidas sin error de descriptores (si el lector no " +
                "cerrara el flujo, aquí saltaría 'Too many open files')",
            true
        )
    }
}
