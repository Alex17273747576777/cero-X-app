package com.ceroX.app.modules.booru

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import com.ceroX.app.R
import com.ceroX.app.core.AlmacenExterno
import com.ceroX.app.ui.ajustes.FragmentHostActivity
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * El visor abierto de verdad, con su actividad y su diseño.
 *
 * LO QUE SÍ COMPRUEBA
 * -------------------
 * Que el Intent lleva la publicación y la pantalla la pinta, que los botones de
 * zoom hacen la misma operación que los gestos, que sin argumentos no se inventa
 * una imagen y que el fallo de carga dice el motivo sin quitar el zoom.
 *
 * LO QUE NO COMPRUEBA
 * -------------------
 * No lanza `MotionEvent` sintéticos: el pellizco y el doble toque viven en
 * [VisorTransformacionTest]. Y no carga una imagen real: [cargadorCoil] se
 * ejercita en producción, mientras que aquí se llama a los mismos callbacks que
 * Coil invoca. Si Coil llegara a arrancar una petición, el interceptor la corta
 * antes de tocar la red.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisorBooruAndroidTest {

    private val post = BooruPost(
        id = 7, url = "https://safebooru.org/images/7/paisaje.png",
        previewUrl = "https://safebooru.org/thumbnails/7/thumbnail.jpg",
        tags = listOf("landscape", "sky"), score = 3, rating = "safe",
        dimensions = "1200×800", source = null, createdAt = 1L,
    )

    @Before fun sinRed() {
        AlmacenExterno.olvidar()
        val contexto = contexto()
        Coil.setImageLoader(
            ImageLoader.Builder(contexto)
                .okHttpClient(
                    OkHttpClient.Builder()
                        .addInterceptor { throw IOException("sin red en las pruebas") }
                        .build(),
                )
                .build(),
        )
    }

    @After fun limpiar() {
        Coil.reset()
        AlmacenExterno.olvidar()
    }

    private fun contexto(): Context = ApplicationProvider.getApplicationContext()

    private fun controlador(post: BooruPost?) = Robolectric.buildActivity(
        FragmentHostActivity::class.java,
        if (post == null) FragmentHostActivity.intento(contexto(), FragmentHostActivity.VISOR)
        else FragmentHostActivity.intentoVisor(contexto(), post),
    ).setup().visible()

    private fun fragmento(actividad: FragmentHostActivity) = actividad.supportFragmentManager
        .findFragmentById(R.id.fragment_host_container) as? VisorBooruFragment

    private fun visor(actividad: FragmentHostActivity) =
        actividad.findViewById<VisorImagenView>(R.id.visor_imagen)

    private fun texto(actividad: FragmentHostActivity, id: Int) =
        actividad.findViewById<TextView>(id).text.toString()

    private fun boton(actividad: FragmentHostActivity, id: Int) =
        actividad.findViewById<Button>(id)

    /** Da tamaño real a la vista: sin medida no hay ajuste posible. */
    private fun medir(actividad: FragmentHostActivity, ancho: Int = 800, alto: Int = 600) {
        val v = visor(actividad)
        v.measure(
            View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(alto, View.MeasureSpec.EXACTLY),
        )
        v.layout(0, 0, ancho, alto)
        shadowOf(actividad.mainLooper).idle()
    }

    @Test fun el_intent_lleva_la_publicacion_y_los_botones_amplian() {
        val controller = controlador(post)
        try {
            val actividad = controller.get()
            assertEquals("Visor de imagen", actividad.title.toString())
            medir(actividad)

            val v = visor(actividad)
            assertTrue("el visor no quedó ajustado con las dimensiones declaradas", v.estado.lista)
            assertTrue(v.estado.ajustada)
            assertEquals(1200, v.estado.anchoImagen)
            assertEquals(800, v.estado.altoImagen)

            val metadata = texto(actividad, R.id.visor_metadata)
            assertTrue(metadata, metadata.contains("#7"))
            assertTrue(metadata, metadata.contains("1200×800"))
            assertTrue(metadata, metadata.contains("rating safe"))
            assertEquals("landscape sky", texto(actividad, R.id.visor_tags))
            assertTrue(texto(actividad, R.id.visor_zoom).contains("ajustada a la pantalla"))

            val acercar = boton(actividad, R.id.visor_acercar)
            val alejar = boton(actividad, R.id.visor_alejar)
            val ajustar = boton(actividad, R.id.visor_ajustar)
            assertFalse("en el ajuste no tiene sentido reducir", alejar.isEnabled)
            assertFalse("y tampoco volver a ajustar", ajustar.isEnabled)
            assertTrue(acercar.isEnabled)

            acercar.performClick()
            assertTrue(v.estado.ampliada)
            assertTrue(v.estado.escala > v.estado.escalaAjuste)
            assertTrue(texto(actividad, R.id.visor_zoom).contains("ampliada"))
            assertTrue("al ampliar hay que poder recorrerla", alejar.isEnabled)
            assertTrue(ajustar.isEnabled)

            boton(actividad, R.id.visor_uno_a_uno).performClick()
            assertEquals(1f, v.estado.escala, 0.001f)

            ajustar.performClick()
            assertTrue(v.estado.ajustada)
            assertFalse(alejar.isEnabled)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun sin_argumentos_no_se_inventa_una_imagen() {
        val controller = controlador(null)
        try {
            val actividad = controller.get()
            assertEquals(
                "El visor se abrió sin los datos de la publicación",
                texto(actividad, R.id.visor_estado),
            )
            assertFalse(visor(actividad).estado.util)
            assertEquals("Sin imagen", texto(actividad, R.id.visor_zoom))
            for (id in listOf(
                R.id.visor_ajustar, R.id.visor_uno_a_uno, R.id.visor_acercar,
                R.id.visor_alejar, R.id.visor_guardar, R.id.visor_favorito,
            )) {
                assertFalse("el botón $id tendría que estar desactivado", boton(actividad, id).isEnabled)
            }
            assertFalse("sin ficha que abrir, el enlace tampoco", boton(actividad, R.id.visor_ficha).isEnabled)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun la_imagen_cargada_ajusta_con_las_dimensiones_reales() {
        val controller = controlador(post)
        try {
            val actividad = controller.get()
            medir(actividad)
            val f = fragmento(actividad)
            assertNotNull("el host no creó el visor", f)

            f!!.imagenCargada(2000, 1000)

            val v = visor(actividad)
            assertEquals(2000, v.estado.anchoImagen)
            assertEquals(1000, v.estado.altoImagen)
            assertTrue(v.estado.ajustada)
            assertTrue(texto(actividad, R.id.visor_estado).contains("Imagen cargada de 2000×1000"))
            assertEquals(View.GONE, actividad.findViewById<View>(R.id.visor_progress).visibility)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun el_fallo_de_carga_dice_el_motivo_y_no_quita_el_zoom() {
        val controller = controlador(post)
        try {
            val actividad = controller.get()
            medir(actividad)
            val f = fragmento(actividad)!!

            f.falloDeCarga("HTTP 403")

            val estado = texto(actividad, R.id.visor_estado)
            assertTrue(estado, estado.contains("No se pudo cargar la imagen: HTTP 403"))
            assertTrue(estado, estado.contains("El zoom sigue activo sobre las dimensiones declaradas"))
            assertTrue(visor(actividad).estado.lista)
            assertTrue(boton(actividad, R.id.visor_acercar).isEnabled)
            assertEquals(View.GONE, actividad.findViewById<View>(R.id.visor_progress).visibility)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun guardar_es_explicito_y_dice_donde_escribe() {
        val controller = controlador(post)
        try {
            val actividad = controller.get()
            val guardar = boton(actividad, R.id.visor_guardar)
            assertEquals("Guardar", guardar.text.toString())
            assertTrue(guardar.contentDescription.toString().contains("almacenamiento externo"))
            assertTrue(boton(actividad, R.id.visor_ficha).text.toString().contains("Safebooru"))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun la_miniatura_abre_el_visor_y_el_visor_carga_con_coil() {
        val lista = File("src/main/java/com/ceroX/app/modules/booru/BooruUIFragment.kt").readText()
        assertTrue("la miniatura tiene que abrir el visor", lista.contains("holder.image.setOnClickListener { ver(post) }"))
        assertTrue(lista.contains("FragmentHostActivity.intentoVisor"))

        val host = File("src/main/java/com/ceroX/app/ui/ajustes/FragmentHostActivity.kt").readText()
        assertTrue(host.contains("VISOR -> VisorBooruFragment()"))
        assertTrue(host.contains("VisorBooruFragment.argumentos(post)"))

        val visor = File("src/main/java/com/ceroX/app/modules/booru/VisorBooruFragment.kt").readText()
        assertTrue("el cargador por defecto tiene que ser Coil", visor.contains("vista.load(url)"))
        assertTrue(visor.contains("onSuccess"))
        assertTrue(visor.contains("onError"))

        val modulo = File("src/main/java/com/ceroX/app/modules/booru/BooruModule.kt").readText()
        assertFalse("queda el fallo fijo de descargas", modulo.contains("Esta fase no incluye descargas"))
        assertFalse(modulo.contains("DownloadResult"))
        assertTrue(modulo.contains("fun descargar(post: BooruPost, url: String): ResultadoDescarga"))
        assertTrue(modulo.contains("DescargasBooru.real(contexto)"))
        assertTrue("la descarga va fuera del hilo principal", visor.contains("Dispatchers.IO"))
    }
}
