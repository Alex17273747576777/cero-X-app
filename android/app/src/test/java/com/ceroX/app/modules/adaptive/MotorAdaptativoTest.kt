package com.ceroX.app.modules.adaptive

import com.ceroX.app.modules.directories.EntradaDirectorio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorAdaptativoTest {
    private fun evento(sesion: String, tipo: MotorAdaptativo.TipoEvento, origen: String, categoria: String = "Manga") =
        MotorAdaptativo.Evento(sesion, tipo, categoria, origen, 1L)

    @Test fun sin_senales_o_desactivado_usa_base() {
        val base = MotorAdaptativo.perfil(emptyList(), "s", "Manga")
        assertFalse(base.adaptado)
        assertEquals(5, base.lineasDescripcion)
        assertEquals(MotorAdaptativo.DENSIDAD_COMPACTA, MotorAdaptativo.base("Apps").densidad)
        assertEquals(2, MotorAdaptativo.base("Datos").lineasDescripcion)
        val desactivado = MotorAdaptativo.perfil(listOf(evento("s", MotorAdaptativo.TipoEvento.ENLACE, "miyomi")), "s", "Manga", false)
        assertFalse(desactivado.adaptado)
        assertTrue(desactivado.prioridadesOrigen.isEmpty())
    }

    @Test fun sesion_actual_pesa_mas_que_historial_y_otra_categoria_no_contamina() {
        val eventos = buildList {
            repeat(3) { add(evento("vieja", MotorAdaptativo.TipoEvento.ENLACE, "fmhy")) }
            add(evento("actual", MotorAdaptativo.TipoEvento.ENLACE, "miyomi"))
            add(evento("actual", MotorAdaptativo.TipoEvento.ENLACE, "anipirate", "Anime"))
        }
        val perfil = MotorAdaptativo.perfil(eventos, "actual", "Manga")
        assertEquals("miyomi", perfil.prioridadesOrigen.keys.first())
        assertFalse(perfil.prioridadesOrigen.containsKey("anipirate"))
        assertTrue(perfil.explicacion.contains("esta sesión"))
        assertTrue(perfil.explicacion.contains("historial"))
    }

    @Test fun densidad_y_contexto_siguen_acciones_no_un_texto_magico() {
        val aperturas = List(4) { evento("s", MotorAdaptativo.TipoEvento.ENLACE, "miyomi") }
        val compacto = MotorAdaptativo.perfil(aperturas, "s", "Manga")
        assertEquals(MotorAdaptativo.DENSIDAD_COMPACTA, compacto.densidad)
        assertEquals(2, compacto.lineasDescripcion)
        val fichas = List(2) { evento("s", MotorAdaptativo.TipoEvento.FICHA, "miyomi") }
        assertEquals(6, MotorAdaptativo.perfil(fichas, "s", "Manga").lineasDescripcion)
        assertEquals(6, MotorAdaptativo.perfil(fichas, "s", "Guías").lineasDescripcion)
    }

    @Test fun recomendacion_es_explicable_y_empates_son_estables() {
        val a = EntradaDirectorio("fmhy", "a", "Demo", url = "https://a.example", detalle = "https://a.example", categoria = "Manga")
        val b = a.copy(origen = "miyomi", id = "b", url = "https://b.example", detalle = "https://b.example")
        val perfil = MotorAdaptativo.perfil(listOf(evento("s", MotorAdaptativo.TipoEvento.ENLACE, "miyomi")), "s", "Manga")
        val orden = MotorAdaptativo.ordenar(listOf(a, b), perfil, "demo")
        assertEquals("miyomi", orden.first().entrada.origen)
        assertTrue(orden.first().motivo.contains("afinidad local"))
        assertTrue(orden.first().motivo.contains("nombre exacto"))
        val estable = MotorAdaptativo.ordenar(listOf(b, a), MotorAdaptativo.base("Manga"), "")
        assertEquals(listOf("b", "a"), estable.map { it.entrada.id })
    }
}
