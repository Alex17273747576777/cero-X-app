package com.ceroX.app.modules.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cableado de la pantalla de IPTV.
 *
 * Nada de esto abre un teléfono: crear el Fragment necesita una actividad y una
 * superficie de vídeo necesita un decodificador. Lo que se comprueba aquí es lo que
 * se rompe **en silencio** y no falla hasta que alguien toca la pantalla:
 *
 *   · una vista que el código pide y el layout no tiene (no compila, pero se escribe
 *     fácil cuando el layout y el Kotlin se hacen a la vez),
 *   · un texto suelto en el layout, que catorce idiomas no verían nunca,
 *   · una cadena nueva sin traducir,
 *   · un destino de la barra inferior que no lleva a ningún sitio, que es exactamente
 *     el fallo que tenía esta app y por el que se veía en blanco,
 *   · un reproductor que no se libera al morir la vista y se queda el decodificador.
 */
class IptvPantallaTest {

    private val fuentes = File("src/main/java/com/ceroX/app")
    private val recursos = File("src/main/res")

    private fun leer(f: File): String {
        assertTrue("falta " + f.path, f.isFile)
        return f.readText()
    }

    private val pantalla: String get() = leer(File(fuentes, "modules/iptv/IptvUiFragment.kt"))
    private val moduloKt: String get() = leer(File(fuentes, "modules/iptv/IptvModule.kt"))
    private val layoutFragmento: String get() = leer(File(recursos, "layout/fragment_iptv.xml"))
    private val layoutsDeItem: String
        get() = leer(File(recursos, "layout/item_fuente_iptv.xml")) +
            leer(File(recursos, "layout/item_canal_iptv.xml"))

    private fun ids(xml: String): Set<String> =
        Regex("""android:id="@\+id/([a-z0-9_]+)"""").findAll(xml)
            .map { it.groupValues[1] }.toSet()

    /** `fuenteNombre` → `fuente_nombre`, que es como ViewBinding nombra las vistas. */
    private fun aSnake(nombre: String): String = buildString {
        for (c in nombre) {
            if (c.isUpperCase()) append('_').append(c.lowercaseChar()) else append(c)
        }
    }

    @Test
    fun `la pantalla pide su modulo al gestor y no a un registro`() {
        val t = pantalla
        assertTrue("no pide el módulo por Pantallas", t.contains("Pantallas.modulo"))
        assertTrue("y no pide el módulo iptv", t.contains("\"iptv\""))
        assertFalse("sigue buscando en un registro", t.contains("registry.getInstance"))
        assertTrue(
            "si el módulo no está, la pantalla tiene que decirlo en vez de quedarse muda",
            t.contains("iptv_modulo_no_disponible"),
        )
    }

    @Test
    fun `cada vista que toca el codigo existe en su layout`() {
        val delFragmento = ids(layoutFragmento)
        assertTrue("el layout del fragmento no declara ninguna vista", delFragmento.isNotEmpty())
        val pedidos = Regex("""binding\.([a-z][A-Za-z0-9]*)""").findAll(pantalla)
            .map { it.groupValues[1] }.filter { it != "root" }.toSet()
        assertTrue("el código no toca ninguna vista", pedidos.isNotEmpty())
        assertEquals(
            "el código pide vistas que el layout no tiene: no compilaría",
            emptySet<String>(),
            pedidos.filter { aSnake(it) !in delFragmento }.toSet(),
        )

        val deItems = ids(layoutsDeItem)
        val pedidosItems = Regex("""\bb\.([a-z][A-Za-z0-9]*)""").findAll(pantalla)
            .map { it.groupValues[1] }.filter { it != "root" }.toSet()
        assertTrue("los adapters no tocan ninguna vista", pedidosItems.isNotEmpty())
        assertEquals(
            "un adapter pide vistas que su layout no tiene",
            emptySet<String>(),
            pedidosItems.filter { aSnake(it) !in deItems }.toSet(),
        )

        // Y al revés: una vista declarada y no usada es un adorno, que es lo que esta
        // app tenía por todas partes antes de la limpieza.
        val usados = pedidos.map { aSnake(it) } + pedidosItems.map { aSnake(it) }
        assertEquals(
            "vistas declaradas que nadie usa",
            emptySet<String>(),
            (delFragmento + deItems).filter { it !in usados }.toSet(),
        )
    }

    @Test
    fun `ningun layout escribe texto a mano`() {
        for (xml in listOf(layoutFragmento, layoutsDeItem)) {
            val sueltos = Regex("""android:(?:text|hint)="([^"]*)"""").findAll(xml)
                .map { it.groupValues[1] }
                .filter { !it.startsWith("@string/") }
                .toList()
            assertEquals(
                "texto que no pasa por strings.xml y por tanto no se traduce",
                emptyList<String>(),
                sueltos,
            )
        }
    }

    @Test
    fun `las cadenas de la pantalla estan en los quince idiomas`() {
        val usadas = (
            Regex("""R\.string\.(iptv_[a-z_]+)""").findAll(pantalla).map { it.groupValues[1] } +
                Regex("""@string/(iptv_[a-z_]+)""").findAll(layoutFragmento + layoutsDeItem)
                    .map { it.groupValues[1] }
            ).toSet()
        assertTrue("la pantalla no usa ninguna cadena propia: " + usadas, usadas.size >= 20)

        val carpetas = listOf("values") + listOf(
            "en", "ca", "pt", "fr", "id", "ja", "ko", "ru", "th", "tr", "uk", "vi", "zh", "ar",
        ).map { "values-$it" }
        for (carpeta in carpetas) {
            val xml = leer(File(recursos, "$carpeta/strings.xml"))
            for (clave in usadas) {
                val m = Regex("""<string name="$clave">([^<]*)</string>""").find(xml)
                assertTrue("falta $clave en $carpeta", m != null)
                assertTrue(
                    "$clave está vacía en $carpeta: una cadena vacía se ve como un hueco",
                    (m?.groupValues?.get(1) ?: "").trim().isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `el destino de ajustes lleva a la pantalla`() {
        val ajustes = leer(File(fuentes, "ui/ajustes/AjustesActivity.kt"))
        val host = leer(File(fuentes, "ui/ajustes/FragmentHostActivity.kt"))
        assertTrue("IPTV no tiene acción en Ajustes", ajustes.contains("FragmentHostActivity.IPTV"))
        assertTrue("el host no crea el fragmento", host.contains("IPTV -> IptvUiFragment()"))
    }

    @Test
    fun `el reproductor se libera al morir la vista`() {
        val t = pantalla
        assertTrue("no hay onDestroyView", t.contains("override fun onDestroyView()"))
        assertTrue("y no libera el reproductor", t.contains("liberarReproductor()"))

        val delModulo = moduloKt
        assertTrue(
            "el módulo no sabe liberar el reproductor sin crearlo",
            delModulo.contains("fun liberarReproductor(): Boolean"),
        )
        val cuerpo = delModulo.substringAfter("fun liberarReproductor(): Boolean")
            .substringBefore("\n    }\n")
        assertFalse(
            "liberarReproductor no puede llamar a reproductor(): lo crearía sólo para soltarlo",
            cuerpo.contains("reproductor()"),
        )
        assertTrue("y no libera nada", cuerpo.contains("liberar()"))
    }

    @Test
    fun `la red va fuera del hilo principal y el reproductor dentro`() {
        val cargar = pantalla.substringAfter("private fun cargar()")
            .substringBefore("\n    private fun")
        assertTrue(
            "cargar descarga una lista: en el hilo principal congelaría la interfaz",
            cargar.contains("enSegundoPlano"),
        )
        val rep = pantalla.substringAfter("private fun reproducir(canal: Canal)")
            .substringBefore("\n    private fun")
        assertFalse(
            "ExoPlayer lanza si se le llama desde un hilo que no es el suyo",
            rep.contains("enSegundoPlano"),
        )
        assertTrue("y no reproduce nada", rep.contains("m.reproducir(canal, binding.superficieVideo)"))
        assertTrue(
            "el repintado tiene que volver al hilo principal",
            pantalla.contains("Handler(Looper.getMainLooper())"),
        )
    }
}
