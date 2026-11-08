package com.ceroX.app.modules.reader

import android.graphics.Bitmap
import com.ceroX.app.modules.kisara.KisaraColorizer
import com.ceroX.app.modules.kisara.KisaraUpscaler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Pruebas del pipeline lector → IA.
 *
 * Los motores reales necesitan ONNX Runtime y el marco de Android, así que aquí
 * se inyectan dobles: lo que se prueba no es que ONNX funcione —eso ya se midió
 * con inferencia real— sino las tres reglas del pipeline:
 *
 *   1. sin IA encendida no se toca ni un píxel
 *   2. si un motor falla, se devuelve el original y NO se lanza
 *   3. el reescalado va antes que la colorización
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ReaderPipelineTest {

    private fun pagina(w: Int = 16, h: Int = 16): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    /** Doble del colorizador: devuelve una imagen nueva o falla, según se pida. */
    private class ColorizadorDoble(
        private val falla: Boolean = false,
        private val lanza: Boolean = false,
    ) : ColorizadorDePagina {
        var llamadas = 0
        override fun colorear(original: Bitmap, maxLado: Int): KisaraColorizer.Resultado {
            llamadas++
            if (lanza) throw IllegalStateException("ONNX explotó")
            return if (falla) {
                KisaraColorizer.Resultado(null, false, 1, "modelo ausente")
            } else {
                KisaraColorizer.Resultado(
                    Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888),
                    true, 1490, ""
                )
            }
        }
    }

    private class ReescaladorDoble(
        private val falla: Boolean = false,
    ) : EscaladorDePagina {
        var llamadas = 0
        override fun reescalar(original: Bitmap, maxLado: Int): KisaraUpscaler.Resultado {
            llamadas++
            return if (falla) {
                KisaraUpscaler.Resultado(null, false, 1, "anime4x.onnx", "sin memoria")
            } else {
                KisaraUpscaler.Resultado(
                    Bitmap.createBitmap(original.width * 2, original.height * 2, Bitmap.Config.ARGB_8888),
                    true, 2673, "anime4x.onnx", ""
                )
            }
        }
    }

    @Test
    fun `sin IA encendida devuelve la misma pagina sin copiar`() {
        val col = ColorizadorDoble()
        val ree = ReescaladorDoble()
        val pipe = ReaderPipeline(RuntimeEnvironment.getApplication(), col, ree)
        val original = pagina()

        val r = pipe.procesar(original, ReaderPipeline.Ajustes(colorizar = false, reescalar = false))

        assertSame("debe devolver el MISMO objeto, no una copia", original, r.pagina)
        assertEquals("no puede haber trabajo aplicado", 0, r.aplicado.size)
        assertEquals("el colorizador no debe ni llamarse", 0, col.llamadas)
        assertEquals("el reescalador no debe ni llamarse", 0, ree.llamadas)
    }

    @Test
    fun `si la colorizacion falla devuelve el original y lo explica`() {
        val col = ColorizadorDoble(falla = true)
        val pipe = ReaderPipeline(RuntimeEnvironment.getApplication(), col, ReescaladorDoble())
        val original = pagina()

        val r = pipe.procesar(original, ReaderPipeline.Ajustes(colorizar = true))

        assertSame("debe seguir pintándose la página original", original, r.pagina)
        assertTrue("debe decir que falló", r.fallido.any { it.contains("colorización") })
        assertTrue("y por qué", r.fallido.any { it.contains("modelo ausente") })
    }

    @Test
    fun `si el motor lanza una excepcion no se propaga`() {
        val col = ColorizadorDoble(lanza = true)
        val pipe = ReaderPipeline(RuntimeEnvironment.getApplication(), col, ReescaladorDoble())

        // La regla número uno: un modelo que explota en mitad de un capítulo no
        // puede cerrar la app.
        val r = pipe.procesar(pagina(), ReaderPipeline.Ajustes(colorizar = true))

        assertNotNull("siempre tiene que haber algo que pintar", r.pagina)
        assertTrue(r.fallido.any { it.contains("IllegalStateException") })
    }

    @Test
    fun `el reescalado va antes que la colorizacion`() {
        val orden = mutableListOf<String>()
        val col = object : ColorizadorDePagina {
            override fun colorear(original: Bitmap, maxLado: Int): KisaraColorizer.Resultado {
                orden.add("colorizar")
                return KisaraColorizer.Resultado(original, true, 1490, "")
            }
        }
        val ree = object : EscaladorDePagina {
            override fun reescalar(original: Bitmap, maxLado: Int): KisaraUpscaler.Resultado {
                orden.add("reescalar")
                return KisaraUpscaler.Resultado(original, true, 2673, "anime4x.onnx", "")
            }
        }
        val pipe = ReaderPipeline(RuntimeEnvironment.getApplication(), col, ree)
        pipe.procesar(pagina(), ReaderPipeline.Ajustes(colorizar = true, reescalar = true))

        assertEquals(
            "reescalar primero evita colorizar a resolución completa (minutos); " +
                "el orden fue $orden",
            listOf("reescalar", "colorizar"), orden
        )
    }

    @Test
    fun `los dos motores se aplican y se registra el tiempo real de cada uno`() {
        val pipe = ReaderPipeline(
            RuntimeEnvironment.getApplication(),
            ColorizadorDoble(),
            ReescaladorDoble()
        )
        val r = pipe.procesar(pagina(), ReaderPipeline.Ajustes(colorizar = true, reescalar = true))

        assertEquals("dos pasos aplicados", 2, r.aplicado.size)
        assertTrue("el reescalado reporta sus ms", r.aplicado.any { it.contains("2673 ms") })
        assertTrue("la colorización reporta sus ms", r.aplicado.any { it.contains("1490 ms") })
        assertTrue("el total no puede ser negativo", r.ms >= 0)
    }

    @Test
    fun `la estimacion es cero cuando no hay IA y crece con lo caro`() {
        val pipe = ReaderPipeline(RuntimeEnvironment.getApplication(),
            ColorizadorDoble(), ReescaladorDoble())

        assertEquals(0L..0L, pipe.estimacionMs(ReaderPipeline.Ajustes()))
        val soloColor = pipe.estimacionMs(ReaderPipeline.Ajustes(colorizar = true))!!
        val soloEscala = pipe.estimacionMs(ReaderPipeline.Ajustes(reescalar = true))!!
        val ambos = pipe.estimacionMs(ReaderPipeline.Ajustes(colorizar = true, reescalar = true))!!

        assertEquals(1490L, soloColor.first)
        assertTrue("el peor caso del reescalado es esrgan a 24 587 ms", soloEscala.last == 24587L)
        assertEquals("los dos suman", soloColor.first + soloEscala.first, ambos.first)
    }
}
