package com.ceroX.app.modules.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del motor de sugerencias.
 *
 * Qué se comprueba y por qué importa cada caso:
 *
 *  - Que una obra del mismo autor que lo que lees salga arriba. Si esto falla,
 *    el motor no está recomendando nada, está devolviendo el catálogo.
 *  - Que lo ya leído NO se recomiende. Es el error más común y el más visible.
 *  - Que sin historial no se invente nada: mejor un feed vacío que uno con
 *    recomendaciones al azar.
 *  - Que las obras con muchas etiquetas no ganen solo por tener muchas. Sin
 *    normalizar, la obra con 20 etiquetas genéricas gana siempre.
 *  - Que la afinidad sea un número con sentido (0 a 1, no 0 a infinito).
 */
class SuggestionEngineTest {

    private val motor = SuggestionEngine()

    private fun obra(
        id: String,
        autores: List<String> = emptyList(),
        etiquetas: List<String> = emptyList(),
        valoracion: Float = 0f,
    ) = SuggestionEngine.Obra(
        id = id, titulo = "Obra $id", autores = autores, etiquetas = etiquetas, valoracion = valoracion,
    )

    @Test
    fun `recomienda del mismo autor que lo que ya lees`() {
        val leido = obra("leido", autores = listOf("Naoki Urasawa"), etiquetas = listOf("thriller"))
        val mismoAutor = obra("mismo-autor", autores = listOf("Naoki Urasawa"), etiquetas = listOf("drama"))
        val otro = obra("otro", autores = listOf("Otra Persona"), etiquetas = listOf("comedia"))

        val r = motor.recomendar(
            catalogo = listOf(mismoAutor, otro),
            historial = listOf(SuggestionEngine.Consumo(leido, 10f)),
        )

        assertEquals(1, r.size)
        assertEquals("mismo-autor", r[0].obra.id)
        assertTrue("la afinidad debe estar entre 0 y 1, fue ${r[0].afinidad}",
            r[0].afinidad in 0f..1f)
        assertTrue("el motivo debe nombrar el autor",
            r[0].motivo.contains("urasawa", ignoreCase = true))
    }

    @Test
    fun `no recomienda lo que ya esta en el historial`() {
        val leido = obra("leido", autores = listOf("Autor X"), etiquetas = listOf("accion"))
        val r = motor.recomendar(
            catalogo = listOf(leido),
            historial = listOf(SuggestionEngine.Consumo(leido, 5f)),
        )
        assertTrue("lo ya leído no puede aparecer como sugerencia", r.isEmpty())
    }

    @Test
    fun `sin historial no inventa nada`() {
        val r = motor.recomendar(
            catalogo = listOf(obra("a", autores = listOf("X"))),
            historial = emptyList(),
        )
        assertTrue("sin datos no hay recomendación honesta", r.isEmpty())
    }

    @Test
    fun `normaliza para que muchas etiquetas no ganen solas`() {
        val leido = obra("leido", autores = listOf("A"), etiquetas = listOf("accion", "drama"))

        // Esta comparte las dos etiquetas reales.
        val afin = obra("afin", etiquetas = listOf("accion", "drama"))

        // Esta tiene 20 etiquetas, casi todas irrelevantes, y solo una en común.
        val ruidosa = obra(
            "ruidosa",
            etiquetas = listOf("accion") + (1..20).map { "relleno$it" },
        )

        val r = motor.recomendar(
            catalogo = listOf(ruidosa, afin),
            historial = listOf(SuggestionEngine.Consumo(leido, 5f)),
        )

        assertTrue("debería haber resultados", r.isNotEmpty())
        assertEquals(
            "la obra con etiquetas relevantes debe ir primero, no la que tiene más",
            "afin", r[0].obra.id,
        )
    }

    @Test
    fun `el peso del historial se amortigua con logaritmo`() {
        val unaVez = obra("a", autores = listOf("Autor"))
        val perfil1 = motor.perfil(listOf(SuggestionEngine.Consumo(unaVez, 1f)))
        val perfil100 = motor.perfil(listOf(SuggestionEngine.Consumo(unaVez, 100f)))

        val v1 = perfil1["autor:autor"] ?: 0f
        val v100 = perfil100["autor:autor"] ?: 0f

        // Con normalización ambos valen 1 si solo hay un término; lo que se
        // comprueba es que el perfil no explota ni se vuelve negativo.
        assertTrue("el peso debe ser positivo", v1 > 0f && v100 > 0f)
        assertTrue("el peso no debe superar 1 tras normalizar", v1 <= 1.0001f && v100 <= 1.0001f)
    }

    @Test
    fun `descarta lo que queda por debajo de la afinidad minima`() {
        val leido = obra("leido", autores = listOf("A"), etiquetas = listOf("x"))
        // Comparte una etiqueta de veinte: el coseno será minúsculo.
        val lejano = obra("lejano", etiquetas = listOf("x") + (1..40).map { "t$it" })
        val r = motor.recomendar(
            catalogo = listOf(lejano),
            historial = listOf(SuggestionEngine.Consumo(leido, 3f)),
        )
        for (s in r) {
            assertTrue("nada por debajo del mínimo", s.afinidad >= FeedLimits.AFINIDAD_MINIMA)
        }
    }
}
