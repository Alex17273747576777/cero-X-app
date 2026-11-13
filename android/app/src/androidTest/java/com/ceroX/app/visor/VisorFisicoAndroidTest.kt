package com.ceroX.app.visor

import android.app.Instrumentation
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ceroX.app.R
import com.ceroX.app.core.AlmacenExterno
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.modules.booru.BooruModule
import com.ceroX.app.modules.booru.BooruPost
import com.ceroX.app.modules.booru.VisorBooruFragment
import com.ceroX.app.modules.booru.VisorImagenView
import com.ceroX.app.modules.booru.VisorTransformacion
import com.ceroX.app.ui.ajustes.FragmentHostActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El recorrido físico del visor, automatizado.
 *
 * POR QUÉ ESTÁ AQUÍ Y NO EN Robolectric
 * --------------------------------------
 * Robolectric no ejecuta gestos reales, no renderiza Coil y no tiene el volumen
 * externo de un aparato. Lo que faltaba por comprobar del visor son justamente
 * esas tres cosas, y ninguna se puede simular sin mentir. Aquí se hacen de
 * verdad: `sendPointerSync` inyecta tactos en la ventana, Coil carga por red y
 * el archivo se escribe donde diga `AlmacenExterno` en el dispositivo.
 *
 * UN DETALLE QUE ROMPE SI SE TOCA
 * -------------------------------
 * Nada de lo que espera o lee se ejecuta dentro de `ActivityScenario.onActivity`:
 * ese bloque corre en el hilo principal, y `waitForIdleSync` o `runOnMainSync`
 * desde ahí se bloquean para siempre. `onActivity` sólo captura referencias; el
 * resto va en el hilo de instrumentación y lee por `enPrincipal`.
 *
 * ESTADO HONESTO
 * --------------
 * **Compilada, nunca ejecutada.** Este entorno no tiene aparato (`adb devices`
 * vacío). Que compile no demuestra que pase. Se ejecuta con:
 *
 * ```text
 * adb shell am instrument -w -e recorridoFisico 1 -e networkTests 1 \
 *   com.ceroX.app.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * y el resultado del aparato es el que vale. Sin `recorridoFisico 1` todo queda
 * omitido; sin `networkTests 1` se omite lo que necesita Safebooru y sólo corre
 * lo que no depende de la red.
 */
@RunWith(AndroidJUnit4::class)
class VisorFisicoAndroidTest {

    private val instrumentacion: Instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun argumentos() = InstrumentationRegistry.getArguments()

    @Test fun la_imagen_se_renderiza_y_el_pellizco_la_amplia_de_verdad() {
        assumirFisico()
        assumirRed()
        val post = postReal()

        conVisor(post) { _, visor ->
            assertTrue(
                "Coil no llegó a cargar la imagen en el aparato",
                esperar(30_000) { visor.drawable != null },
            )
            val dibujo: Drawable = enPrincipal { visor.drawable!! }
            assertTrue("el drawable no tiene tamaño real", dibujo.intrinsicWidth > 0 && dibujo.intrinsicHeight > 0)

            val antes = enPrincipal { visor.estado }
            assertTrue("el visor no quedó ajustado con la imagen real", antes.ajustada)

            pellizco(visor, 2.2f)
            val ampliada = enPrincipal { visor.estado }
            assertTrue(
                "el pellizco real no amplió: ${antes.escala} → ${ampliada.escala}",
                ampliada.escala > antes.escala + 0.05f,
            )
            assertTrue("la escala ampliada no se refleja en el estado", ampliada.ampliada)

            pellizco(visor, 0.45f)
            val reducida = enPrincipal { visor.estado }
            assertTrue("el pellizco inverso no redujo", reducida.escala < ampliada.escala)
            assertTrue(
                "se redujo por debajo del ajuste: ${reducida.escala} < ${reducida.escalaMinima}",
                reducida.escala >= reducida.escalaMinima - 0.001f,
            )
            println(
                "RECORRIDO_FISICO render=ok pellizco=${antes.escala}->${ampliada.escala}->${reducida.escala}" +
                    " drawable=${dibujo.intrinsicWidth}x${dibujo.intrinsicHeight}",
            )
        }
    }

    @Test fun el_doble_toque_alterna_y_el_arrastre_no_deja_huecos() {
        assumirFisico()
        assumirRed()
        val post = postReal()

        conVisor(post) { _, visor ->
            assertTrue("sin imagen no hay gestos que comprobar", esperar(30_000) { visor.drawable != null })
            val ajustada = enPrincipal { visor.estado }

            dobleToque(visor)
            val ampliada = enPrincipal { visor.estado }
            assertTrue("el doble toque real no amplió", ampliada.escala > ajustada.escala + 0.05f)

            dobleToque(visor)
            val vuelta = enPrincipal { visor.estado }
            assertTrue("el segundo doble toque no volvió al ajuste", vuelta.ajustada)

            dobleToque(visor)
            val zoom = enPrincipal { visor.estado }
            arrastre(visor, -4_000f, -4_000f)
            val arrastrada = enPrincipal { visor.estado }
            val ancho = arrastrada.anchoImagen * arrastrada.escala
            val alto = arrastrada.altoImagen * arrastrada.escala
            assertTrue(
                "la imagen se salió por la izquierda: dx=${arrastrada.dx} mínimo=${zoom.anchoVista - ancho}",
                arrastrada.dx >= zoom.anchoVista - ancho - 1f,
            )
            assertTrue(
                "la imagen se salió por arriba: dy=${arrastrada.dy} mínimo=${zoom.altoVista - alto}",
                arrastrada.dy >= zoom.altoVista - alto - 1f,
            )
            assertTrue("y tampoco puede pasarse de 0", arrastrada.dx <= 0f && arrastrada.dy <= 0f)
            println(
                "RECORRIDO_FISICO dobleToque=${ajustada.escala}->${ampliada.escala}->${vuelta.escala}" +
                    " arrastre=${arrastrada.dx},${arrastrada.dy}",
            )
        }
    }

    @Test fun guardar_escribe_en_el_almacenamiento_externo_real_del_aparato() {
        assumirFisico()
        assumirRed()
        val app = ApplicationProvider.getApplicationContext<CeroXApplication>()
        val externo = AlmacenExterno.directorio(app)
        assertNotNull("El aparato no dio almacenamiento externo escribible", externo)
        val post = postReal()
        val destino = File(
            externo!!,
            "booru/safebooru-${post.id}.${VisorTransformacion.extensionDe(post.url)}",
        )

        conVisor(post) { actividad, visor ->
            assertTrue("sin imagen no se descarga nada", esperar(30_000) { visor.drawable != null })
            val modulo = Pantallas.modulo(app, "booru-viewer", BooruModule::class.java)
            assertNotNull("El módulo booru-viewer no está en el aparato", modulo)
            assertTrue(
                "El módulo no llegó a inicializarse; sin eso no hay descargador",
                esperar(20_000) { modulo!!.descargasDisponibles() },
            )

            destino.delete()
            enPrincipal { actividad.findViewById<Button>(R.id.visor_guardar).performClick() }
            assertTrue("la descarga real no terminó en el aparato", esperar(45_000) { destino.isFile })

            val estado = enPrincipal { actividad.findViewById<TextView>(R.id.visor_estado).text.toString() }
            assertTrue("la pantalla no informó de la descarga: $estado", estado.contains("Guardada"))
            assertTrue("y no dijo la ruta", estado.contains(destino.absolutePath))
            assertTrue("el archivo quedó vacío", destino.length() > 1_000)
            assertTrue(
                "lo guardado no está dentro del almacenamiento externo",
                destino.absolutePath.startsWith(externo.absolutePath),
            )
            assertTrue(
                "el contenido no es una imagen",
                esImagen(destino.inputStream().use { it.readNBytes(12) }),
            )
            println("RECORRIDO_FISICO descarga=${destino.absolutePath} bytes=${destino.length()}")
        }
    }

    @Test fun contraste_y_accesibilidad_se_miden_sobre_el_tema_real_del_aparato() {
        assumirFisico()

        conVisor(null) { actividad, visor ->
            assertEquals("El visor se abrió sin datos y aun así se titula", "Visor de imagen", actividad.title.toString())
            assertEquals(
                "sin argumentos tiene que decirlo, no inventar una imagen",
                "El visor se abrió sin los datos de la publicación",
                enPrincipal { actividad.findViewById<TextView>(R.id.visor_estado).text.toString() },
            )

            val valor = TypedValue()
            assertTrue(
                "el tema del aparato no resuelve colorOnSurfaceVariant",
                enPrincipal { actividad.theme.resolveAttribute(R.attr.colorOnSurfaceVariant, valor, true) },
            )
            val texto = valor.data
            assertTrue(
                "el tema del aparato no resuelve colorBackground",
                enPrincipal { actividad.theme.resolveAttribute(android.R.attr.colorBackground, valor, true) },
            )
            val fondo = valor.data
            val relacion = contraste(texto, fondo)
            assertTrue(
                "El texto de estado no llega al contraste AA en el aparato: " +
                    "${formato(relacion)} (texto=#${Integer.toHexString(texto)} fondo=#${Integer.toHexString(fondo)})",
                relacion >= 4.5,
            )

            val botones = mutableListOf<Button>()
            recoger(enPrincipal { actividad.findViewById<ViewGroup>(android.R.id.content) }, botones)
            assertTrue("el visor no expuso ningún botón", botones.isNotEmpty())
            val sinEtiqueta = botones.filter { it.text.isNullOrBlank() && it.contentDescription.isNullOrBlank() }
            assertEquals("botones sin texto ni descripción para el lector de pantalla", 0, sinEtiqueta.size)
            val altoMinimo = botones.minOf { if (it.height > 0) it.height else it.minimumHeight }
            assertTrue("hay un botón por debajo del objetivo táctil de 48 px: $altoMinimo", altoMinimo >= 48)
            assertTrue("la imagen no tiene descripción", !enPrincipal { visor.contentDescription }.isNullOrBlank())
            println(
                "RECORRIDO_FISICO contraste=${formato(relacion)} botones=${botones.size} altoMinimo=$altoMinimo",
            )
        }
    }

    // ── preparación ────────────────────────────────────────────────────────

    private fun assumirFisico() {
        assumeTrue("Pasare -e recorridoFisico 1", argumentos().getString("recorridoFisico") == "1")
    }

    private fun assumirRed() {
        assumeTrue("Pasare -e networkTests 1", argumentos().getString("networkTests") == "1")
    }

    /**
     * Abre el visor y entrega la actividad y la vista. Todo lo que espere o lea
     * va fuera de `onActivity`, que corre en el hilo principal.
     */
    private fun conVisor(post: BooruPost?, cuerpo: (FragmentHostActivity, VisorImagenView) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<CeroXApplication>()
        val intento = if (post == null) {
            FragmentHostActivity.intento(app, FragmentHostActivity.VISOR)
        } else {
            FragmentHostActivity.intentoVisor(app, post)
        }
        val escenario = ActivityScenario.launch<FragmentHostActivity>(intento)
        try {
            var capturada: FragmentHostActivity? = null
            escenario.onActivity { capturada = it }
            val actividad = capturada
            assertNotNull("La actividad del visor no arrancó", actividad)
            actividad!!

            val fragment = enPrincipal {
                actividad.supportFragmentManager
                    .findFragmentById(R.id.fragment_host_container) as? VisorBooruFragment
            }
            assertNotNull("El host no creó el fragmento del visor", fragment)

            val visor = enPrincipal { actividad.findViewById<VisorImagenView>(R.id.visor_imagen) }
            assertNotNull("No existe la vista del visor", visor)
            assertTrue("el visor no llegó a medirse en el aparato", esperar(10_000) { visor!!.width > 0 })
            cuerpo(actividad, visor!!)
        } finally {
            escenario.close()
        }
    }

    /** Una publicación safe real, consultada fuera del hilo principal. */
    private fun postReal(): BooruPost {
        val app = ApplicationProvider.getApplicationContext<CeroXApplication>()
        val modulo = Pantallas.modulo(app, "booru-viewer", BooruModule::class.java)
        assertNotNull("El módulo booru-viewer no está registrado en el aparato", modulo)
        var encontrado: BooruPost? = null
        var fallo: String? = null
        val hilo = Thread {
            runCatching { runBlocking { modulo!!.searchBooru(tags = "landscape", limit = 2) } }
                .onSuccess { resultado ->
                    if (!resultado.success) fallo = resultado.motivo
                    encontrado = resultado.results.firstOrNull()
                }

                .onFailure { fallo = it.message ?: it.javaClass.simpleName }
        }
        hilo.start()
        hilo.join(40_000)
        assertNotNull("Safebooru no devolvió ninguna publicación safe en el aparato: $fallo", encontrado)
        return encontrado!!
    }

    // ── utilidades de hilo, gestos y color ─────────────────────────────────

    private fun recoger(vista: View?, salida: MutableList<Button>) {
        when (vista) {
            is Button -> salida += vista
            is ViewGroup -> for (i in 0 until vista.childCount) recoger(vista.getChildAt(i), salida)
            else -> Unit
        }
    }

    private fun <T> enPrincipal(cuerpo: () -> T): T {
        var resultado: Any? = SIN_VALOR
        instrumentacion.runOnMainSync { resultado = cuerpo() }
        assertTrue("la lectura en el hilo principal no devolvió nada", resultado !== SIN_VALOR)
        @Suppress("UNCHECKED_CAST")
        return resultado as T
    }

    private fun esperar(millis: Long, condicion: () -> Boolean): Boolean {
        val fin = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < fin) {
            instrumentacion.waitForIdleSync()
            if (enPrincipal(condicion)) return true
            SystemClock.sleep(50)
        }
        return false
    }

    private fun enviar(evento: MotionEvent) {
        instrumentacion.sendPointerSync(evento)
        evento.recycle()
    }

    private fun obtener(
        inicio: Long,
        ahora: Long,
        accion: Int,
        propiedades: Array<MotionEvent.PointerProperties>,
        coordenadas: Array<MotionEvent.PointerCoords>,
    ): MotionEvent = MotionEvent.obtain(
        inicio, ahora, accion, propiedades.size, propiedades, coordenadas,
        0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
    )

    private fun propiedades(dedos: Int) = Array(dedos) { i ->
        MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER }
    }

    private fun coordenadas(cx: Float, cy: Float, radio: Float, dedos: Int) = Array(dedos) { i ->
        MotionEvent.PointerCoords().apply {
            x = cx + if (i == 0) -radio else radio
            y = cy
            pressure = 1f
            size = 0.01f
        }
    }

    private fun coordenada(x: Float, y: Float) = arrayOf(
        MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 0.01f },
    )

    private fun centro(vista: View): Pair<Float, Float> {
        val ubicacion = IntArray(2)
        vista.getLocationOnScreen(ubicacion)
        return (ubicacion[0] + vista.width / 2f) to (ubicacion[1] + vista.height / 2f)
    }

    /** Pellizco real de dos dedos. `factor` mayor que 1 abre; menor, cierra. */
    private fun pellizco(vista: View, factor: Float, pasos: Int = 12) {
        val (cx, cy) = centro(vista)
        val dos = propiedades(2)
        val uno = arrayOf(dos[0])
        val inicio = SystemClock.uptimeMillis()
        val radioInicio = 30f
        val radioFinal = radioInicio * factor
        val segundo = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT

        enviar(obtener(inicio, inicio, MotionEvent.ACTION_DOWN, uno, coordenadas(cx, cy, radioInicio, 1)))
        enviar(obtener(inicio, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN or segundo, dos, coordenadas(cx, cy, radioInicio, 2)))
        for (paso in 1..pasos) {
            val radio = radioInicio + (radioFinal - radioInicio) * paso / pasos
            SystemClock.sleep(10)
            enviar(obtener(inicio, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, dos, coordenadas(cx, cy, radio, 2)))
        }
        enviar(obtener(inicio, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_UP or segundo, dos, coordenadas(cx, cy, radioFinal, 2)))
        enviar(obtener(inicio, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, uno, coordenadas(cx, cy, radioFinal, 1)))
        reposar()
    }

    private fun dobleToque(vista: View) {
        val (cx, cy) = centro(vista)
        val uno = propiedades(1)
        repeat(2) {
            val inicio = SystemClock.uptimeMillis()
            enviar(obtener(inicio, inicio, MotionEvent.ACTION_DOWN, uno, coordenada(cx, cy)))
            SystemClock.sleep(25)
            enviar(obtener(inicio, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, uno, coordenada(cx, cy)))
            SystemClock.sleep(70)
        }
        reposar()
    }

    private fun arrastre(vista: View, deltaX: Float, deltaY: Float, pasos: Int = 10) {
        val (cx, cy) = centro(vista)
        val uno = propiedades(1)
        val inicio = SystemClock.uptimeMillis()
        enviar(obtener(inicio, inicio, MotionEvent.ACTION_DOWN, uno, coordenada(cx, cy)))
        for (paso in 1..pasos) {
            SystemClock.sleep(10)
            enviar(
                obtener(
                    inicio, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, uno,
                    coordenada(cx + deltaX * paso / pasos, cy + deltaY * paso / pasos),
                ),
            )
        }
        enviar(obtener(inicio, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, uno, coordenada(cx, cy)))
        reposar()
    }

    private fun reposar() {
        instrumentacion.waitForIdleSync()
        SystemClock.sleep(150)
    }

    private fun esImagen(cabecera: ByteArray): Boolean {
        val png = intArrayOf(0x89, 0x50, 0x4E, 0x47)
        val jpeg = intArrayOf(0xFF, 0xD8, 0xFF)
        fun empieza(texto: String): Boolean {
            val bytes = texto.toByteArray()
            return cabecera.size >= bytes.size && bytes.indices.all { cabecera[it] == bytes[it] }
        }
        fun empiezaEnteros(prefijo: IntArray) = cabecera.size >= prefijo.size &&
            prefijo.indices.all { (cabecera[it].toInt() and 0xFF) == prefijo[it] }
        return empiezaEnteros(png) || empiezaEnteros(jpeg) || empieza("GIF8") ||
            (empieza("RIFF") && cabecera.size >= 12 &&
                cabecera.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()))
    }

    private fun luminancia(color: Int): Double {
        fun canal(desplazamiento: Int): Double {
            val s = ((color shr desplazamiento) and 0xFF) / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * canal(16) + 0.7152 * canal(8) + 0.0722 * canal(0)
    }

    /** Relación de contraste WCAG 2.1 entre dos colores ARGB. */
    private fun contraste(uno: Int, otro: Int): Double {
        val a = luminancia(uno)
        val b = luminancia(otro)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun formato(valor: Double): String = ((valor * 100).toInt() / 100).toString()

    private companion object {
        val SIN_VALOR = Any()
    }
}
