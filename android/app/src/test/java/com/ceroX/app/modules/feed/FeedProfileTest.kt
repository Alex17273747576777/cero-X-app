package com.ceroX.app.modules.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Perfiles de feed: los cinco rankings y la rotación por días.
 *
 * La rotación se prueba con fechas concretas, no con "hoy" — una prueba que
 * depende del día en que se ejecuta falla los martes y nadie sabe por qué.
 */
class FeedProfileTest {

    private val titulos = mapOf(
        "a" to "Zeta",
        "b" to "alfa",
        "c" to "Mu",
    )

    @Test
    fun `el orden de prioridad respeta al usuario`() {
        val p = FeedProfile("p", "x", entradas = listOf("c", "a", "b"), ranking = FeedProfile.Ranking.PRIORIDAD)
        assertEquals("el orden lo pone el usuario, no la app", listOf("c", "a", "b"), p.ordenar(titulos))
    }

    @Test
    fun `el orden por uso pone primero lo que mas se usa`() {
        val p = FeedProfile("p", "x", entradas = listOf("a", "b", "c"), ranking = FeedProfile.Ranking.USO)
        val m = mapOf(
            "a" to FeedProfile.Metricas(vecesUsada = 3),
            "b" to FeedProfile.Metricas(vecesUsada = 40),
            "c" to FeedProfile.Metricas(vecesUsada = 12),
        )
        assertEquals(listOf("b", "c", "a"), p.ordenar(titulos, m))
    }

    @Test
    fun `el orden por novedades pone primero la que mas publica`() {
        val p = FeedProfile("p", "x", entradas = listOf("a", "b", "c"), ranking = FeedProfile.Ranking.NOVEDADES)
        val m = mapOf(
            "a" to FeedProfile.Metricas(novedadesPorActualizacion = 0.5f),
            "b" to FeedProfile.Metricas(novedadesPorActualizacion = 8f),
            "c" to FeedProfile.Metricas(novedadesPorActualizacion = 2f),
        )
        assertEquals(listOf("b", "c", "a"), p.ordenar(titulos, m))
    }

    @Test
    fun `el orden alfabetico ignora mayusculas y pone las vacias al final`() {
        val p = FeedProfile("p", "x", entradas = listOf("a", "b", "c"), ranking = FeedProfile.Ranking.ALFABETICO)
        // "alfa" < "Mu" < "Zeta" sin distinguir mayúsculas.
        assertEquals(listOf("b", "c", "a"), p.ordenar(titulos))

        // Una entrada sin título no puede desaparecer: va al final.
        val sinTitulo = p.ordenar(mapOf("a" to "Zeta", "c" to "Mu"))
        assertEquals("la que no tiene título va al final, no se pierde", "b", sinTitulo.last())
        assertEquals("y siguen estando las tres", 3, sinTitulo.size)
    }

    @Test
    fun `el orden por recientes pone primero la ultima añadida`() {
        val p = FeedProfile("p", "x", entradas = listOf("a", "b", "c"), ranking = FeedProfile.Ranking.RECIENTES)
        val m = mapOf(
            "a" to FeedProfile.Metricas(añadidaEn = 1000),
            "b" to FeedProfile.Metricas(añadidaEn = 3000),
            "c" to FeedProfile.Metricas(añadidaEn = 2000),
        )
        assertEquals(listOf("b", "c", "a"), p.ordenar(titulos, m))
    }

    @Test
    fun `sin metricas el orden no revienta`() {
        listOf(
            FeedProfile.Ranking.USO, FeedProfile.Ranking.NOVEDADES,
            FeedProfile.Ranking.RECIENTES, FeedProfile.Ranking.ALFABETICO,
        ).forEach { r ->
            val p = FeedProfile("p", "x", entradas = listOf("a", "b"), ranking = r)
            val orden = p.ordenar(emptyMap())
            assertEquals("con ranking $r no se pueden perder entradas", 2, orden.size)
        }
    }

    @Test
    fun `la rotacion por dias es predecible y no depende de la instalacion`() {
        // 1 de enero de 2026 = día 20454 desde epoch.
        // Con cadaDias = 7, toca el día 20454, 20461, 20468...
        val semanal = FeedProfile("s", "Semanal", cadaDias = 7)
        val dia = 86_400_000L

        assertTrue("toca el día base", semanal.tocaHoy(20_454L * dia))
        assertTrue("y 7 días después", semanal.tocaHoy(20_461L * dia))
        assertTrue("y 14 días después", semanal.tocaHoy(20_468L * dia))
        assertTrue("pero no al día siguiente", !semanal.tocaHoy(20_455L * dia))
        assertTrue("ni dos días después", !semanal.tocaHoy(20_456L * dia))
    }

    @Test
    fun `cadaCero nunca toca sola`() {
        val manual = FeedProfile("m", "Manual", cadaDias = 0)
        val dia = 86_400_000L
        repeat(10) { d ->
            assertTrue("un perfil manual no debe saltar solo", !manual.tocaHoy((20_454L + d) * dia))
        }
    }

    @Test
    fun `si dos perfiles tocan el mismo dia gana el de periodo mas largo`() {
        // Uno cada 2 días y otro cada 4. En el día múltiplo de 4, ambos tocan.
        val cada2 = FeedProfile("2", "Cada 2", cadaDias = 2)
        val cada4 = FeedProfile("4", "Cada 4", cadaDias = 4)
        val dia = 86_400_000L
        // OJO: 20456 tiene desfase 2, divisible por 2 pero NO por 4, así que solo
        // toca el perfil de 2 días. El día en que ambos coinciden es el desfase 4,
        // o sea 20458. La primera versión de esta prueba usaba 20456 y acusaba al
        // código de un fallo que era de la prueba.
        val momento = 20_458L * dia    // desfase 4: divisible por 2 y por 4

        val elegido = FeedProfile.perfilDeHoy(listOf(cada2, cada4), momento)
        assertEquals("el de periodo más largo es el más específico", "4", elegido?.id)
    }

    @Test
    fun `sin perfiles activos no se elige ninguno`() {
        assertNull(FeedProfile.perfilDeHoy(emptyList()))
        assertNull(FeedProfile.perfilDeHoy(listOf(FeedProfile("m", "manual", cadaDias = 0))))
    }

    @Test
    fun `el reparto de la semana es el esperado`() {
        // Un perfil por día de la semana: cada uno con cadaDias = 7 y desfase
        // distinto no existe en la API, así que se comprueba que con 7 días el
        // mismo perfil cae siempre en el mismo día de la semana.
        val dia = 86_400_000L
        val perfiles = listOf(FeedProfile("p", "Semanal", cadaDias = 7))
        val toca = (0 until 28).count { d ->
            FeedProfile.perfilDeHoy(perfiles, (20_454L + d) * dia) != null
        }
        assertEquals("en 28 días toca 4 veces, no más", 4, toca)
    }
}
