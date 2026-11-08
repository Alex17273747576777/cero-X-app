package com.ceroX.app.modules.reader

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pruebas de la caché de páginas.
 *
 * Se ejecutan con Robolectric porque `Bitmap.allocationByteCount` es una llamada
 * real al framework y con el `returnDefaultValues` de Gradle devolvería 0,
 * haciendo que todas las pruebas de tamaño pasaran sin probar nada.
 */
// Robolectric 4.13 no conoce targetSdk 36 y aborta con
// "targetSdkVersion=36 > maxSdkVersion=34". Se fija la imagen de Android 14,
// que es la última que trae. El código bajo prueba no depende de la versión:
// solo usa Bitmap, que es idéntico desde API 26.
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PageCacheTest {

    private fun bitmap(ancho: Int = 8, alto: Int = 8): Bitmap =
        Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)

    @Test
    fun `guarda y devuelve la misma pagina`() {
        val c = PageCache(topeBytes = 10L * 1024 * 1024)
        val bmp = bitmap()
        c.guardar("a", bmp)
        assertNotNull(c.obtener("a"))
        assertEquals("un acierto", 1L, c.aciertos())
        assertEquals("sin fallos", 0L, c.fallos())
    }

    @Test
    fun `una pagina que no esta devuelve null y cuenta fallo`() {
        val c = PageCache()
        assertNull(c.obtener("no-existe"))
        assertEquals(1L, c.fallos())
        assertEquals(0f, c.hitRate())
    }

    @Test
    fun `respeta el tope en bytes`() {
        val bmp = bitmap(32, 32)                    // 32*32*4 = 4096 B
        val bytes = bmp.allocationByteCount.toLong()
        // Espacio para dos justas.
        val c = PageCache(topeBytes = bytes * 2)

        c.guardar("1", bmp)
        c.guardar("2", bmp)
        c.guardar("3", bmp)

        assertTrue("no debe pasar del tope", c.bytesUsados() <= bytes * 2)
        assertNull("la más vieja se descartó", c.obtener("1"))
        assertNotNull("la última entró", c.obtener("3"))
    }

    @Test
    fun `una pagina mayor que todo el cache no lo vacia`() {
        val pequena = bitmap(8, 8)
        val enorme = bitmap(512, 512)               // 512*512*4 = 1 MB
        val c = PageCache(topeBytes = 512L * 1024)  // 512 KB: la enorme no cabe

        c.guardar("pequena", pequena)
        c.guardar("enorme", enorme)

        assertNull("la enorme no entra", c.obtener("enorme"))
        assertNotNull("y no se llevó por delante a la pequeña", c.obtener("pequena"))
    }

    @Test
    fun `hit rate refleja aciertos y fallos`() {
        val c = PageCache()
        c.guardar("x", bitmap())
        c.obtener("x")          // acierto
        c.obtener("y")          // fallo
        c.obtener("z")          // fallo
        assertEquals(1f / 3f, c.hitRate(), 0.001f)
    }

    @Test
    fun `recortar libera hasta el objetivo`() {
        val bmp = bitmap(32, 32)
        val bytes = bmp.allocationByteCount.toLong()
        val c = PageCache(topeBytes = bytes * 4)
        for (i in 1..4) c.guardar("$i", bmp)

        c.recortarHasta(bytes)

        assertTrue("debe quedar como mucho un byte-objetivo",
            c.bytesUsados() <= bytes)
    }

    @Test
    fun `la clave distingue capitulo y pagina`() {
        val c = PageCache()
        val k1 = c.clave("src", "obra", "cap1", 0)
        val k2 = c.clave("src", "obra", "cap2", 0)
        val k3 = c.clave("src", "obra", "cap1", 1)
        assertTrue("capítulo distinto, clave distinta", k1 != k2)
        assertTrue("página distinta, clave distinta", k1 != k3)
    }
}
