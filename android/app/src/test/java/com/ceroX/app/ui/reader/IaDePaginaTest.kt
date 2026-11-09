package com.ceroX.app.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.ceroX.app.ui.ia.PantallaIaActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El puente entre el lector y los modelos, probado con Android de verdad.
 *
 * QUÉ SE COMPRUEBA, Y POR QUÉ CADA COSA
 * -------------------------------------
 *  1. **Apagado no copia.** Si la colorización está apagada, `procesar` devuelve
 *     exactamente el mismo objeto. Una copia por página serían megabytes por
 *     capítulo a cambio de nada, y `assertSame` es la única forma de comprobarlo:
 *     comparar contenido no distingue «igual» de «el mismo».
 *  2. **Apagado no carga los modelos.** `activa` es falso y no se toca ONNX. Esto
 *     importa porque el pipeline se construye con `by lazy`: si se instanciara al
 *     abrir el lector, arrancar un capítulo cargaría 373 MiB de modelos para no
 *     usarlos.
 *  3. **El resumen dice el coste.** Un interruptor encendido sin decir cuánto
 *     tarda es lo que convierte una aplicación en una estufa silenciosa.
 *  4. **La pantalla se abre.** `PantallaIaActivity` se infla sin lanzar. Suena
 *     poco ambicioso; es lo único que se puede comprobar de una pantalla sin un
 *     dispositivo delante, y es exactamente lo que faltaba: la pantalla no existía.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IaDePaginaTest {

    private val contexto get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun pagina(lado: Int = 8): Bitmap {
        val b = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE)
        return b
    }

    @Test
    fun `apagado devuelve el mismo bitmap, no una copia`() = runBlocking {
        val prefs = PreferenciasDeIa(contexto)
        prefs.colorizar = false
        prefs.reescalar = false

        val ia = IaDePagina(contexto, prefs)
        val original = pagina()

        assertFalse("sin nada encendido, activa tiene que ser falso", ia.activa)
        val salida = ia.procesar(original)
        assertSame(
            "apagado no puede copiar la página: se pinta el mismo objeto",
            original,
            salida,
        )
        assertNull("sin trabajo no hay parte que enseñar", ia.ultimo)
    }

    @Test
    fun `el resumen nombra lo encendido y su coste medido`() {
        val prefs = PreferenciasDeIa(contexto)

        prefs.colorizar = false
        prefs.reescalar = false
        assertTrue(prefs.resumen().startsWith("Apagado"))

        prefs.colorizar = true
        val soloColor = prefs.resumen()
        assertTrue(soloColor.contains("colorizar"))
        assertTrue("tiene que decir cuánto tarda", soloColor.contains("1 500"))

        prefs.reescalar = true
        val ambos = prefs.resumen()
        assertTrue(ambos.contains("reescalar"))
        assertTrue(
            "con los dos encendidos el coste se suma en el texto",
            ambos.contains("24 600") || ambos.contains("2 700"),
        )
    }

    @Test
    fun `el motivo del ultimo fallo sobrevive a apagar el interruptor`() {
        val prefs = PreferenciasDeIa(contexto)
        prefs.colorizar = true
        prefs.anotar(
            com.ceroX.app.modules.reader.ReaderPipeline.Resultado(
                pagina = pagina(),
                aplicado = emptyList(),
                fallido = listOf("colorizar: el modelo no está en el APK"),
                ms = 12,
            )
        )
        assertEquals("colorizar: el modelo no está en el APK", prefs.ultimoMotivo)

        // Apagar no borra el motivo: mañana hay que poder saber qué pasó ayer.
        prefs.colorizar = false
        assertEquals(
            "apagar el interruptor no puede ocultar el error de la última vez",
            "colorizar: el modelo no está en el APK",
            prefs.ultimoMotivo,
        )
        assertEquals(12L, prefs.ultimosMs)
    }

    @Test
    fun `la pantalla de modelos se abre sin lanzar`() {
        // Lo que se comprueba: que la Activity existe, se infla y su ciclo de vida
        // básico no revienta. Es la comprobación que faltaba en el README cuando
        // decía «funciona (falta pantalla)».
        val control = Robolectric.buildActivity(PantallaIaActivity::class.java).setup()
        assertFalse(
            "la pantalla tiene que quedar viva tras crearse",
            control.get().isFinishing,
        )
        control.pause().stop().destroy()
    }
}
