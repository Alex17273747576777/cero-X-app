package com.cerox.comun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Los idiomas, medidos contra lo que hay, no contra lo que nos gustaría.
 *
 * Lo que fijan estas pruebas:
 *   · que la interfaz cubra los 20 idiomas que aparecen de verdad en los
 *     repositorios de extensiones, y no invente otros;
 *   · que un idioma sin textos traducidos caiga a inglés **y lo diga**;
 *   · que los idiomas de derecha a izquierda se marquen, porque si no la
 *     interfaz árabe se pinta al revés;
 *   · que neutro y modismos den resultados **distintos** —si dieran lo mismo,
 *     el ajuste sería un adorno—;
 *   · que el proveedor de fábrica no mande nada fuera del dispositivo.
 */
class IdiomasTest {

    // ── Interfaz ──────────────────────────────────────────────────────────

    @Test
    fun cubre_los_idiomas_que_aparecen_en_los_repositorios_de_extensiones() {
        // Los medidos en el índice de Keiyoushi el 2026-11-08 (1556 extensiones).
        val medidos = listOf(
            "en", "ja", "pt", "es", "id", "vi", "tr", "fr", "zh", "ar",
            "th", "ru", "it", "ko", "uk", "ca", "bg", "cs", "de",
        )
        val faltan = medidos.filter { tag -> Idiomas.INTERFAZ.none { it.tag == tag } }
        assertTrue(faltan.isEmpty(), "la interfaz no cubre estos idiomas con extensiones: $faltan")
    }

    @Test
    fun el_arabe_el_persa_y_el_hebreo_se_marcan_de_derecha_a_izquierda() {
        val rtl = Idiomas.INTERFAZ.filter { it.escrituraDeDerechaAIzquierda }.map { it.tag }
        assertTrue("ar" in rtl, "el árabe tiene que estar marcado RTL: $rtl")
        assertTrue("he" in rtl, "el hebreo tiene que estar marcado RTL: $rtl")
        assertTrue(Idiomas.esDeDerechaAIzquierda("ar-EG"), "ar-EG se resuelve a árabe")
        assertFalse(Idiomas.esDeDerechaAIzquierda("es-DO"), "el español no es RTL")
    }

    @Test
    fun el_nombre_de_cada_idioma_esta_en_su_propio_idioma() {
        // Quien busca su idioma busca «日本語», no «japonés».
        assertEquals("日本語", Idiomas.interfaz("ja")?.nativo)
        assertEquals("العربية", Idiomas.interfaz("ar")?.nativo)
        assertEquals("Русский", Idiomas.interfaz("ru")?.nativo)
        assertEquals("Türkçe", Idiomas.interfaz("tr")?.nativo)
    }

    // ── Caída a inglés, dicha ─────────────────────────────────────────────

    @Test
    fun un_idioma_sin_traduccion_cae_a_ingles_y_lo_avisa() {
        val (tag, cayo) = Idiomas.resolverConCaida("sw")
        assertEquals("en", tag)
        assertTrue(cayo, "si no hay textos, la app lo tiene que decir, no fingir")

        val (tag2, cayo2) = Idiomas.resolverConCaida("es-DO")
        assertEquals("es", tag2, "es-DO se lee con los textos de es")
        assertFalse(cayo2)
    }

    @Test
    fun un_idioma_que_si_esta_traducido_no_cae() {
        assertEquals("es" to false, Idiomas.resolverConCaida("es"))
        assertEquals("en" to false, Idiomas.resolverConCaida("en-GB"))
    }

    @Test
    fun normaliza_los_tags_como_los_devuelve_android() {
        assertEquals("es-DO", Idiomas.normalizar("es_DO"))
        assertEquals("pt-BR", Idiomas.normalizar("pt-br"))
        assertEquals("zh-TW", Idiomas.normalizar("zh-tw"))
        assertEquals("es", Idiomas.normalizar("ES"))
        assertNull(Idiomas.normalizar(""))
        assertNull(Idiomas.normalizar(null))
        assertNull(Idiomas.normalizar("x"), "un tag de una letra no es un idioma")
    }

    // ── Contenido ─────────────────────────────────────────────────────────

    @Test
    fun los_filtros_de_contenido_incluyen_las_multiidioma() {
        val filtros = Idiomas.filtrosDeContenido()
        assertTrue("all" in filtros, "dejar fuera «all» esconde las fuentes que sirven en cualquier idioma")
        assertTrue("es" in filtros)
        assertFalse(Idiomas.filtrosDeContenido(incluirMultiidioma = false).contains("all"))
    }

    @Test
    fun no_se_ofrece_un_idioma_de_contenido_sin_extensiones() {
        assertTrue(Idiomas.hayContenidoEn("es"))
        assertTrue(Idiomas.hayContenidoEn("ja"))
        assertTrue(Idiomas.hayContenidoEn("all"))
        // «sw» no tiene ninguna extensión: ofrecerlo sería un filtro vacío.
        assertFalse(Idiomas.hayContenidoEn("sw"))
        // pt-BR se resuelve a pt, que sí tiene.
        assertTrue(Idiomas.hayContenidoEn("pt-BR"))
    }

    @Test
    fun los_conteos_de_contenido_son_los_medidos() {
        assertEquals(20, Idiomas.CONTENIDO.size, "son los 20 idiomas medidos en Keiyoushi")
        assertEquals(455, Idiomas.CONTENIDO.first { it.codigo == "en" }.extensiones)
        assertEquals(112, Idiomas.CONTENIDO.first { it.codigo == "es" }.extensiones)
        // Y las 1556 extensiones del índice se reparten entre estos idiomas.
        val suma = Idiomas.CONTENIDO.sumOf { it.extensiones }
        assertEquals(1556, suma, "la suma tiene que dar las 1556 extensiones medidas")
    }

    // ── Registro: neutro contra modismos ──────────────────────────────────

    @Test
    fun neutro_y_modismos_dan_resultados_distintos() {
        val frase = "Toma el autobús y paga con el móvil"
        val neutro = Idiomas.aplicarRegistro(frase, Idiomas.Registro.NEUTRO)
        val conModismos = Idiomas.aplicarRegistro(
            frase,
            Idiomas.Registro.MODISMOS,
            Variante.DOMINICANA,
        )
        // El neutro NO cambia la frase: no hay glosario del usuario ni variante.
        assertEquals(frase, neutro)
        // El dominicano sí: guagua (con su artículo femenino) y celular.
        assertTrue(conModismos.contains("la guagua"), "esperaba «la guagua»: $conModismos")
        assertFalse(
            conModismos.contains("el guagua"),
            "«el guagua» es un error de concordancia: la sustitución tiene que ser del " +
                "sintagma entero, no de la palabra suelta",
        )
        assertTrue(conModismos.contains("celular"), "esperaba «celular»: $conModismos")
        // Y si dieran lo mismo, el ajuste no serviría para nada.
        assertTrue(neutro != conModismos, "neutro y modismos no pueden dar lo mismo")
    }

    @Test
    fun el_modo_neutro_respeta_el_glosario_del_usuario() {
        // El glosario es del usuario y se aplica siempre, en los dos registros.
        val r = Idiomas.aplicarRegistro(
            "El servidor está caído",
            Idiomas.Registro.NEUTRO,
            glosario = mapOf("servidor" to "server"),
        )
        assertEquals("El server está caído", r)
    }

    @Test
    fun rioplatense_y_dominicana_no_son_lo_mismo() {
        val frase = "Toma el autobús"
        val do_ = Idiomas.aplicarRegistro(frase, Idiomas.Registro.MODISMOS, Variante.DOMINICANA)
        val ar = Idiomas.aplicarRegistro(frase, Idiomas.Registro.MODISMOS, Variante.RIOPLATENSE)
        assertEquals("Toma la guagua", do_)
        assertEquals("Toma el autobús", ar, "el rioplatense no dice guagua")
    }

    // ── Proveedores y privacidad ──────────────────────────────────────────

    @Test
    fun el_proveedor_de_fabrica_no_manda_nada_fuera() {
        assertFalse(
            Traduccion.Proveedor.POR_DEFECTO.enviaTextoFuera,
            "el proveedor de fábrica no puede sacar texto del dispositivo sin que el usuario lo elija",
        )
        assertFalse(Traduccion.Proveedor.POR_DEFECTO.necesitaClave)
        assertEquals(Traduccion.Proveedor.LOCAL, Traduccion.Proveedor.POR_DEFECTO)
    }

    @Test
    fun cada_proveedor_dice_si_saca_el_texto_y_si_pide_clave() {
        for (p in Traduccion.Proveedor.entries) {
            if (p != Traduccion.Proveedor.LOCAL) {
                assertTrue(p.enviaTextoFuera, "${p.nombre} envía texto y no lo declara")
            }
            if (p == Traduccion.Proveedor.LIBRE) {
                assertFalse(p.necesitaClave, "Libre no pide clave; si mintiera, no se podría usar")
                assertTrue(p.enviaTextoFuera, "Libre manda el texto a un servidor público y hay que decirlo")
            }
            assertTrue(p.nota.isNotBlank(), "${p.nombre} no explica nada al usuario")
        }
    }

    @Test
    fun el_aviso_de_privacidad_no_dice_que_todo_es_local_si_no_lo_es() {
        assertTrue(
            Traduccion.avisoDePrivacidad(Traduccion.Proveedor.LOCAL).contains("nada sale"),
        )
        for (p in Traduccion.Proveedor.entries.filter { it.enviaTextoFuera }) {
            val aviso = Traduccion.avisoDePrivacidad(p)
            assertFalse(
                aviso.contains("nada sale"),
                "${p.nombre} manda el texto fuera y su aviso dice que no: $aviso",
            )
            assertTrue(
                aviso.contains("sale") || aviso.contains("enviará"),
                "el aviso de ${p.nombre} tiene que decir que el texto se envía: $aviso",
            )
        }
    }

    @Test
    fun el_traductor_local_no_pretende_traducir_paginas() {
        // Un botón que no hace lo que promete es peor que un botón que no está.
        val r = Traduccion.puedeTraducir(Traduccion.Proveedor.LOCAL, Traduccion.Objetivo.CONTENIDO)
        assertFalse(r.posible)
        assertTrue(r.motivo.contains("no traduce páginas"))

        assertTrue(Traduccion.puedeTraducir(Traduccion.Proveedor.LOCAL, Traduccion.Objetivo.INTERFAZ).posible)
        assertTrue(Traduccion.puedeTraducir(Traduccion.Proveedor.DEEPL, Traduccion.Objetivo.CONTENIDO).posible)
    }

    @Test
    fun las_urls_de_los_proveedores_son_las_reales() {
        assertEquals(
            "https://api-free.deepl.com/v2/translate",
            Traduccion.urlDe(Traduccion.Proveedor.DEEPL, null, "abc:fx"),
            "las claves :fx son de la API gratuita",
        )
        assertEquals(
            "https://api.deepl.com/v2/translate",
            Traduccion.urlDe(Traduccion.Proveedor.DEEPL, null, "abc"),
        )
        assertEquals(
            "https://translate.disroot.org",
            Traduccion.urlDe(Traduccion.Proveedor.LIBRE, null),
            "el servidor por defecto de LibreTranslate",
        )
        assertEquals(
            "https://mi-servidor.local",
            Traduccion.urlDe(Traduccion.Proveedor.LIBRE, "https://mi-servidor.local"),
            "la URL de Libre se puede cambiar",
        )
        assertNull(Traduccion.urlDe(Traduccion.Proveedor.LOCAL, null), "el local no tiene URL")
        assertNotNull(Traduccion.urlDe(Traduccion.Proveedor.GOOGLE, null))
        assertNotNull(Traduccion.urlDe(Traduccion.Proveedor.MICROSOFT, null))
        assertNotNull(Traduccion.urlDe(Traduccion.Proveedor.YOUDAO, null))
    }

    @Test
    fun no_se_ofrecen_destinos_que_el_proveedor_no_soporta() {
        // DeepL no tiene tailandés: pedirlo da un error del servidor que el
        // usuario no puede arreglar.
        assertFalse(Traduccion.soporta(Traduccion.Proveedor.DEEPL, "th"))
        assertTrue(Traduccion.soporta(Traduccion.Proveedor.DEEPL, "ja"))
        assertTrue(Traduccion.soporta(Traduccion.Proveedor.GOOGLE, "th"))
    }

    @Test
    fun los_idiomas_de_recurso_no_prometen_mas_que_los_proveedores() {
        // Todo idioma al que la app dice poder traducir sus textos tiene que
        // estar soportado por al menos un proveedor con clave o LibreTranslate.
        val soportados = Idiomas.RECURSO.filter { destino ->
            listOf(
                Traduccion.Proveedor.GOOGLE,
                Traduccion.Proveedor.MICROSOFT,
                Traduccion.Proveedor.LIBRE,
            ).any { Traduccion.soporta(it, destino) }
        }
        assertEquals(
            Idiomas.RECURSO.size,
            soportados.size,
            "hay idiomas de recurso que ningún proveedor soporta: " +
                (Idiomas.RECURSO - soportados.toSet()),
        )
    }
}
