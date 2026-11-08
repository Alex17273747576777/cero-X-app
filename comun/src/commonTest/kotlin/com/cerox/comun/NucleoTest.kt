package com.cerox.comun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pruebas del núcleo compartido.
 *
 * Estas pruebas se ejecutan en **todas** las plataformas que el módulo compila
 * (JVM y JS en esta máquina; iOS necesita macOS). Que pasen aquí y en JS es lo
 * que demuestra que las reglas no dependen de la JVM.
 */
class NucleoTest {

    // ── Orden natural ───────────────────────────────────────────────────────

    @Test
    fun el_dos_va_antes_que_el_diez() {
        assertEquals(
            listOf("1.jpg", "2.jpg", "3.jpg", "10.jpg", "20.jpg"),
            OrdenNatural.ordenar(listOf("10.jpg", "2.jpg", "1.jpg", "20.jpg", "3.jpg")),
        )
    }

    @Test
    fun el_orden_natural_aguanta_ceros_delante() {
        assertEquals(
            listOf("0001.jpg", "01.jpg", "002.jpg", "003.jpg"),
            OrdenNatural.ordenar(listOf("003.jpg", "01.jpg", "002.jpg", "0001.jpg")),
        )
    }

    @Test
    fun el_numero_del_capitulo_se_extrae() {
        assertEquals(12f, OrdenNatural.numeroDe("Capítulo 12"))
        assertEquals(3.5f, OrdenNatural.numeroDe("Cap 3,5"))
        assertEquals(-1f, OrdenNatural.numeroDe("Extras"))
    }

    // ── Presupuesto de cola ─────────────────────────────────────────────────

    @Test
    fun la_cola_tiene_tres_frenos() {
        assertEquals(100_000, PresupuestoDeCola.MAX_RECURSOS)
        assertEquals(3, PresupuestoDeCola.MAX_INTENTOS)
        assertEquals(2, PresupuestoDeCola.MAX_POR_HOST)
    }

    @Test
    fun un_ejecutable_desconocido_no_hereda_el_tope_de_un_video() {
        assertEquals(PresupuestoDeCola.TipoRecurso.OTRO, PresupuestoDeCola.clasificar("setup.exe"))
        assertTrue(
            PresupuestoDeCola.topePorTipo(PresupuestoDeCola.TipoRecurso.OTRO) <
                PresupuestoDeCola.topePorTipo(PresupuestoDeCola.TipoRecurso.VIDEO)
        )
    }

    @Test
    fun epub_es_libro_y_no_esta_en_dos_tipos() {
        assertEquals(PresupuestoDeCola.TipoRecurso.LIBRO, PresupuestoDeCola.clasificar("tomo.epub"))
        // La prueba que impide volver al fallo del orden del enum: ninguna
        // extensión puede estar clasificada en dos tipos distintos.
        val extensiones = listOf("cbz", "jpg", "mp4", "pdf", "epub", "mp3", "exe", "xyz")
        extensiones.forEach { ext ->
            assertEquals(
                PresupuestoDeCola.clasificar("a.$ext"),
                PresupuestoDeCola.clasificar("b.$ext"),
                "la clasificación de .$ext no es estable",
            )
        }
    }

    @Test
    fun la_concurrencia_nunca_pasa_de_dos() {
        assertEquals(1, PresupuestoDeCola.concurrencia("ahorro"))
        assertEquals(2, PresupuestoDeCola.concurrencia("rendimiento"))
        assertEquals(2, PresupuestoDeCola.concurrencia("calidad"))
    }

    @Test
    fun la_espera_crece_y_tiene_techo() {
        assertEquals(1000L, PresupuestoDeCola.esperaAntesDelIntento(1))
        assertEquals(2000L, PresupuestoDeCola.esperaAntesDelIntento(2))
        assertEquals(4000L, PresupuestoDeCola.esperaAntesDelIntento(3))
        assertTrue(PresupuestoDeCola.esperaAntesDelIntento(10) <= 30_000L)
    }

    @Test
    fun sin_tamano_el_progreso_es_cero_no_cien() {
        assertEquals(0f, PresupuestoDeCola.progreso(500, 0))
        assertEquals(0.5f, PresupuestoDeCola.progreso(50, 100))
        assertEquals(1f, PresupuestoDeCola.progreso(150, 100))
    }

    // ── Tracker ─────────────────────────────────────────────────────────────

    @Test
    fun no_se_retrocede_un_progreso() {
        val p = PlanDeTracker.planificar(capituloLocal = 12, capituloRemoto = 40, totalConocido = 200)
        assertEquals(PlanDeTracker.Resultado.OMITIR_POR_RETROCESO, p.resultado)
        assertTrue(p.motivo.contains("40"))
    }

    @Test
    fun si_no_se_sabe_el_remoto_no_se_envia() {
        assertEquals(
            PlanDeTracker.Resultado.OMITIR_POR_DATOS,
            PlanDeTracker.planificar(12, -1, 200).resultado,
        )
    }

    @Test
    fun terminado_solo_si_el_progreso_es_el_total() {
        assertTrue(PlanDeTracker.sePuedeTerminar(200, 200))
        assertFalse(PlanDeTracker.sePuedeTerminar(12, 200))
        assertFalse(PlanDeTracker.sePuedeTerminar(50, 0))
    }

    @Test
    fun un_capitulo_mas_adelante_si_se_envia() {
        val p = PlanDeTracker.planificar(41, 40, 200)
        assertEquals(PlanDeTracker.Resultado.ENVIAR, p.resultado)
        assertEquals(41, p.capituloAEnviar)
    }

    // ── Variantes ───────────────────────────────────────────────────────────

    @Test
    fun el_dominicano_cambia_el_vocabulario() {
        assertEquals(
            "Voy en guagua con una guineo",
            Variante.aplicar("Voy en autobús con una banana", Variante.DOMINICANA),
        )
    }

    @Test
    fun se_respeta_la_mayuscula_inicial() {
        assertEquals("Guagua llena", Variante.reemplazar("Autobús llena", "autobús", "guagua"))
        assertEquals("¡GUAGUA!", Variante.reemplazar("¡AUTOBÚS!", "autobús", "guagua"))
    }

    @Test
    fun no_se_parte_una_palabra_al_reemplazar() {
        // El fallo clásico: «os» dentro de «vosotros».
        assertEquals("vosotros ustedes vais", Variante.reemplazar("vosotros os vais", "os", "ustedes"))
    }

    @Test
    fun el_glosario_del_usuario_gana_a_la_variante() {
        val r = Variante.aplicar("El móvil", Variante.DOMINICANA, mapOf("móvil" to "teléfono"))
        assertTrue(r.contains("teléfono"))
        assertFalse(r.contains("celular"))
    }

    @Test
    fun una_variante_desconocida_cae_a_la_neutra() {
        assertEquals("es", Variante.porCodigo("es-XX").codigo)
        assertEquals("es-DO", Variante.porCodigo("ES-do").codigo)
    }

    // ── El sobre de la extensión ────────────────────────────────────────────

    @Test
    fun el_sobre_encierra_la_extension_en_una_funcion() {
        val script = SobreDeExtension.construir("var a = 1;")
        assertTrue(script.contains("(function (ceroX, http, dom, prefs, log)"))
        assertTrue(script.contains("\"use strict\""))
        assertTrue(script.contains("module.exports"))
    }

    @Test
    fun la_extension_no_recibe_el_contexto_de_android() {
        SobreDeExtension.prohibidos.forEach { prohibido ->
            assertFalse(
                SobreDeExtension.parametros.contains(prohibido),
                "el sobre no puede pasar $prohibido a la extensión",
            )
        }
    }

    @Test
    fun se_cuentan_los_nombres_declarados() {
        val json = """[{"name":"One Piece","lang":"es","baseUrl":"https://a"},{"name":"Berserk","lang":"es","baseUrl":"https://b"}]"""
        assertEquals(listOf("One Piece", "Berserk"), SobreDeExtension.nombresDeclarados(json))
        assertTrue(SobreDeExtension.nombresDeclarados("[]").isEmpty())
        assertTrue(SobreDeExtension.nombresDeclarados(null).isEmpty())
    }

    // ── Capacidades por plataforma ─────────────────────────────────────────

    @Test
    fun la_tabla_de_capacidades_no_promete_lo_que_no_hay() {
        val lector = Plataforma.capacidades.first { it.funcion == "lector" }
        // Pintar páginas necesita canvas nativo y ONNX: hoy no hay ni escritorio
        // ni iOS para el lector, y la tabla lo dice.
        assertTrue(lector.jvm)
        assertFalse(lector.ios)
        assertFalse(lector.js)
    }

    @Test
    fun las_extensiones_js_si_van_en_las_tres() {
        val js = Plataforma.capacidades.first { it.funcion == "extensiones-js" }
        assertTrue(js.jvm && js.ios && js.js)
    }

    @Test
    fun toda_capacidad_explica_su_motivo() {
        Plataforma.capacidades.forEach {
            assertTrue(it.nota.length > 20, "la capacidad ${it.funcion} no explica nada")
        }
    }
}
