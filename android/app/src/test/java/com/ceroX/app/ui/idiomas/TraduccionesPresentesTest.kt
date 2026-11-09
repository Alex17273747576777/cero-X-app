package com.ceroX.app.ui.idiomas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Las traducciones que la app dice tener **tienen que existir en el APK**.
 *
 * EL DEFECTO QUE ESTA PRUEBA IMPIDE
 * --------------------------------
 * La pantalla de idiomas ofrece 40 idiomas y, si no hay archivo de recursos para
 * uno, Android cae al idioma por defecto y el usuario ve la app en otro idioma sin
 * haberlo pedido. Eso no es un detalle estético: es ofrecer algo que no se cumple.
 *
 * Lo que se comprueba, contra los archivos de `res/`:
 *
 *   1. Cada carpeta `values-<tag>` corresponde a una clave que existe en `values/`,
 *      y ninguna cadena se ha quedado sin traducir (no hay huecos silenciosos).
 *   2. Cada idioma de la lista `Idiomas.INTERFAZ` está en uno de estos dos estados,
 *      y **se dice cuál**: con archivo de traducción, o sin él (y entonces se lee
 *      en inglés). Lo que no se permite es un estado intermedio raro.
 *   3. El número de idiomas con traducción no baja del que ya había: 15 (es, en,
 *      pt, id, ja, zh, ar, fr, tr, vi, th, ru, ko, uk, ca).
 */
class TraduccionesPresentesTest {

    private val raiz: File = run {
        var d = File(".").absoluteFile
        while (!File(d, "src/main/res/values").isDirectory && d.parentFile != null) {
            d = d.parentFile
        }
        require(File(d, "src/main/res/values").isDirectory) { "no se encontró res/" }
        d
    }

    private val res: File = File(raiz, "src/main/res")

    /** Sube desde `raiz` hasta encontrar el archivo; la ruta relativa depende del cwd. */
    private fun buscarEnRepo(relativa: String): File {
        var d: File? = raiz
        while (d != null) {
            val f = File(d, relativa)
            if (f.isFile) return f
            d = d.parentFile
        }
        return File(raiz, relativa)
    }

    /** Carpeta de recursos → tag de idioma. `values-en` → `en`. */
    private val carpetasDeIdioma: Map<File, String> = res.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values-") }
        .associateWith { it.name.removePrefix("values-") }

    private fun cadenas(de: File): Map<String, String> {
        val f = File(de, "strings.xml")
        if (!f.isFile) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val nodos = doc.getElementsByTagName("string")
        return (0 until nodos.length).associate { i ->
            val n = nodos.item(i)
            n.attributes.getNamedItem("name").nodeValue to (n.textContent ?: "")
        }
    }

    private val base: Map<String, String> = cadenas(File(res, "values"))

    @Test
    fun el_espanol_es_la_referencia_y_no_esta_vacio() {
        assertTrue("values/strings.xml no tiene cadenas", base.size >= 56)
        assertTrue("falta la cadena app_name", base.containsKey("app_name"))
    }

    @Test
    fun ninguna_traduccion_deja_cadenas_sin_traducir() {
        // Un hueco significa que esa cadena se lee en español dentro de una app en
        // japonés: medio traducida y sin avisar.
        val huecos = mutableListOf<String>()
        carpetasDeIdioma.forEach { (dir, tag) ->
            val tr = cadenas(dir)
            if (tr.isEmpty()) return@forEach
            val faltan = base.keys - tr.keys
            if (faltan.isNotEmpty()) huecos.add("$tag → $faltan")
        }
        assertTrue("idiomas con cadenas sin traducir: $huecos", huecos.isEmpty())
    }

    @Test
    fun ninguna_traduccion_tiene_cadenas_que_no_existen_en_el_espanol() {
        // Una cadena de más es una traducción de algo que ya no se usa, o un nombre
        // mal escrito: las dos cosas se pudren sin que nadie lo note.
        val sobran = mutableListOf<String>()
        carpetasDeIdioma.forEach { (dir, tag) ->
            val tr = cadenas(dir)
            if (tr.isEmpty()) return@forEach
            val extra = tr.keys - base.keys
            if (extra.isNotEmpty()) sobran.add("$tag → $extra")
        }
        assertTrue("idiomas con cadenas que no están en values/: $sobran", sobran.isEmpty())
    }

    @Test
    fun cada_traduccion_tiene_texto_de_verdad() {
        // Una cadena vacía pasa las dos pruebas anteriores y deja un hueco en la
        // pantalla. Se comprueba que hay texto.
        val vacias = mutableListOf<String>()
        carpetasDeIdioma.forEach { (dir, tag) ->
            cadenas(dir).forEach { (k, v) ->
                if (v.isBlank()) vacias.add("$tag/$k")
            }
        }
        assertTrue("cadenas vacías: $vacias", vacias.isEmpty())
    }

    @Test
    fun no_se_perdio_ningun_idioma_de_los_que_ya_estaban() {
        // Los que trae la app escritos a mano. Si alguno desaparece, esta prueba lo
        // dice en vez de que el usuario lo descubra leyendo en inglés.
        val esperados = setOf(
            "es", "en", "pt", "id", "ja", "zh", "ar",   // primera ronda
            "fr", "tr", "vi", "th", "ru", "ko", "uk", "ca", // segunda ronda
        )
        val presentes = carpetasDeIdioma.values.toSet() + "es"
        val faltan = esperados - presentes
        assertTrue("desaparecieron traducciones que existían: $faltan", faltan.isEmpty())
        assertTrue("se esperaban al menos 14 idiomas traducidos, hay ${presentes.size}", presentes.size >= 14)
    }

    @Test
    fun los_idiomas_de_la_pantalla_estan_en_uno_de_dos_estados_y_se_sabe_cual() {
        // La lista de la pantalla no puede prometer más de lo que hay: o hay
        // archivo (y se lee traducido) o no lo hay (y la pantalla lo avisa).
        val conArchivo = carpetasDeIdioma.values.toSet() + "es"
        // Lo que la pantalla ofrece, según el fuente de Idiomas.kt del núcleo.
        val fuenteIdiomas = buscarEnRepo("comun/src/commonMain/kotlin/com/cerox/comun/Idiomas.kt")
        assertTrue(
            "no se encuentra el fuente de Idiomas.kt: ${fuenteIdiomas.absolutePath}",
            fuenteIdiomas.isFile,
        )
        val texto = fuenteIdiomas.readText()
        val ofrecidos = Regex("""IdiomaInterfaz\("([a-zA-Z-]+)""")
            .findAll(texto).map { it.groupValues[1] }.toSet()
        assertTrue("la pantalla no ofrece ningún idioma", ofrecidos.size >= 20)

        // Ninguno de los ofrecidos está en un estado imposible: o tiene archivo o
        // no lo tiene, pero no puede tener un archivo a medias (eso ya lo cubren
        // las pruebas de arriba).
        val imposibles = ofrecidos.filter { tag ->
            val dir = File(res, "values-$tag")
            dir.isDirectory && cadenas(dir).size != base.size
        }
        assertTrue("idiomas ofrecidos con traducción a medias: $imposibles", imposibles.isEmpty())

        // Y se informa el reparto real, para que la documentación pueda copiarlo.
        val traducidosDeLosOfrecidos = ofrecidos.intersect(conArchivo)
        assertFalse(
            "ninguno de los idiomas ofrecidos tiene traducción, algo se rompió",
            traducidosDeLosOfrecidos.isEmpty(),
        )
        println(
            "idiomas ofrecidos: ${ofrecidos.size} · con traducción escrita: " +
                "${traducidosDeLosOfrecidos.size} ($traducidosDeLosOfrecidos) · " +
                "en inglés hasta que se traduzcan: ${ofrecidos.size - traducidosDeLosOfrecidos.size}",
        )
    }
}
