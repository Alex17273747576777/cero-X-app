package com.ceroX.app.modules.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pruebas del índice de backups.
 *
 * Lo que se comprueba, en orden de importancia:
 *   1. que un archivo inválido NO se declara restaurable (el fallo original era
 *      justo lo contrario: `success = true` con solo comprobar que existía)
 *   2. que un zip de más entradas de las permitidas se detecta ANTES de abrirlo
 *   3. que una bomba de descompresión se detecta por el tamaño declarado
 *   4. que un gzip truncado se nota
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
class BackupIndexTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "cerox-backup-index").apply { mkdirs() }

    private fun archivo(nombre: String, bytes: ByteArray): File =
        File(dir, nombre).also { it.writeBytes(bytes) }

    private fun zip(nombre: String, entradas: Map<String, ByteArray>): File {
        val f = File(dir, nombre)
        ZipOutputStream(f.outputStream()).use { z ->
            entradas.forEach { (n, d) ->
                z.putNextEntry(ZipEntry(n))
                z.write(d)
                z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun `un archivo inexistente no es restaurable`() {
        val i = BackupIndex.indexar(File(dir, "no-existe-${System.nanoTime()}.tachibk"))
        assertFalse("nunca restaurable", i.restaurable)
        assertTrue("debe explicar por qué", i.problemas.first().contains("no existe"))
    }

    @Test
    fun `un archivo vacio no es restaurable`() {
        val i = BackupIndex.indexar(archivo("vacio.bak", ByteArray(0)))
        assertFalse(i.restaurable)
        assertTrue(i.problemas.any { it.contains("vacío") })
    }

    @Test
    fun `ruido aleatorio no es restaurable`() {
        val ruido = ByteArray(2048).also { java.util.Random(42).nextBytes(it) }
        val i = BackupIndex.indexar(archivo("ruido.bak", ruido))
        assertFalse(i.restaurable)
        assertTrue(i.problemas.any { it.contains("formato no reconocido") })
    }

    @Test
    fun `un zip normal se indexa con sus entradas`() {
        val f = zip("normal.zip", mapOf(
            "library.json" to """{"obras":5}""".toByteArray(),
            "categories.json" to """["a","b"]""".toByteArray(),
        ))
        val i = BackupIndex.indexar(f)

        assertEquals("zip", i.formato)
        assertEquals("dos entradas", 2, i.entradas)
        assertTrue("debe listar los nombres: ${i.nombres}",
            i.nombres.containsAll(listOf("library.json", "categories.json")))
        assertTrue("un zip normal no tiene problemas: ${i.problemas}", i.restaurable)
    }

    @Test
    fun `un zip vacio no es restaurable`() {
        val f = zip("vacio.zip", emptyMap())
        val i = BackupIndex.indexar(f)
        assertFalse("un zip sin entradas no restaura nada", i.restaurable)
        assertTrue(i.problemas.any { it.contains("vacío") })
    }

    @Test
    fun `una bomba de descompresion se detecta antes de descomprimir`() {
        // 200 MB de ceros comprimidos ocupan muy poco. El índice tiene que verlo
        // por el tamaño DECLARADO, sin descomprimir nada.
        val f = File(dir, "bomba.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("bomba.bin"))
            val trozo = ByteArray(1024 * 1024)      // 1 MiB de ceros
            repeat(220) { z.write(trozo) }          // 220 MiB declarados
            z.closeEntry()
        }
        val i = BackupIndex.indexar(f)

        println("  bomba.zip: ${f.length() / 1024} KB en disco, declara " +
            "${i.bytesDescomprimidos / (1024 * 1024)} MiB")
        assertFalse("debe rechazarse", i.restaurable)
        assertTrue(
            "debe decir que es una bomba de descompresión: ${i.problemas}",
            i.problemas.any { it.contains("bomba") }
        )
        assertTrue(
            "el archivo en disco debe ser pequeño (es la gracia de una bomba)",
            f.length() < 5L * 1024 * 1024
        )
    }

    @Test
    fun `un gzip valido pasa y uno truncado no`() {
        // gzip válido
        val bueno = File(dir, "bueno.tachibk")
        GZIPOutputStream(bueno.outputStream()).use { it.write("contenido protobuf".toByteArray()) }
        val iBueno = BackupIndex.indexar(bueno)
        assertEquals("gzip", iBueno.formato)
        assertTrue("un gzip de una entrada debe pasar: ${iBueno.problemas}", iBueno.restaurable)

        // gzip truncado: se corta a los 20 bytes, incluida la cabecera
        val crudo = bueno.readBytes()
        val truncado = archivo("truncado.tachibk", crudo.copyOf(20))
        val iTruncado = BackupIndex.indexar(truncado)
        assertFalse("un gzip cortado no puede ser restaurable", iTruncado.restaurable)
        println("  gzip truncado: ${iTruncado.problemas}")
    }

    @Test
    fun `un sqlite se reconoce pero no se declara restaurable`() {
        // Cabecera real de SQLite + relleno
        val cabecera = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val f = archivo("base.db", cabecera + ByteArray(4096))
        val i = BackupIndex.indexar(f)

        assertEquals("sqlite", i.formato)
        assertFalse(
            "restaurar un SQLite encima sin verificar el esquema no puede darse por bueno",
            i.restaurable
        )
        assertTrue(i.problemas.any { it.contains("esquema") })
    }

    @Test
    fun `restoreBackup nunca dice exito sin restaurar`() {
        // La regresión que importa: el método original devolvía success = true con
        // solo comprobar que el archivo existía.
        val gestor = BackupManager(RuntimeEnvironment.getApplication())
        val f = zip("valido.zip", mapOf("a.json" to "{}".toByteArray()))

        val r = gestor.restoreBackup(f.absolutePath)

        assertFalse(
            "sin decodificador NO se puede devolver éxito; mensaje: ${r.message}",
            r.success
        )
        assertEquals("no puede decir que restauró elementos", 0, r.restoredItems)
        println("  mensaje real: ${r.message}")
    }
}
