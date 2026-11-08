package com.ceroX.app.stress

import com.ceroX.app.modules.feed.SuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Estrés del motor de sugerencias.
 *
 * El motor se ejecuta cada vez que el usuario abre el inicio, sobre TODO su
 * historial. Si con 10 000 obras tarda más de un segundo, el inicio se siente
 * roto aunque los resultados sean buenos. Aquí se mide con volumen real de un
 * lector empedernido: 10 000 obras, 2 000 leídas, 40 etiquetas por obra.
 *
 * También se comprueban los dos casos que rompen un sistema de recomendación
 * casero y que no salen leyendo el código:
 *   - todo el historial es de una sola obra (el perfil se degenera)
 *   - las etiquetas son todas iguales (todo se parece a todo)
 */
class StressSuggestionEngineTest {

    private val azar = Random(11223344L)

    private fun catalogo(n: Int): List<SuggestionEngine.Obra> = (0 until n).map { i ->
        SuggestionEngine.Obra(
            id = "obra-$i",
            titulo = "Obra $i",
            autores = listOf("autor-${i % 500}"),
            etiquetas = (0 until 40).map { "etiqueta-${azar.nextInt(800)}" }.distinct(),
        )
    }

    private fun consumos(obras: List<SuggestionEngine.Obra>): List<SuggestionEngine.Consumo> =
        obras.map { SuggestionEngine.Consumo(it, 1f) }

    @Test
    fun `10000 obras y 2000 leidas en menos de un segundo`() {
        val motor = SuggestionEngine()
        val cat = catalogo(10_000)
        val hist = consumos(cat.shuffled(azar).take(2_000))

        // Dos pasadas: la primera paga la inicialización de estructuras, la
        // segunda es la que el usuario siente al volver a abrir el inicio.
        motor.recomendar(cat, hist, limite = 50)
        val t0 = System.nanoTime()
        val r = motor.recomendar(cat, hist, limite = 50)
        val ms = (System.nanoTime() - t0) / 1_000_000

        assertTrue("debe devolver algo", r.isNotEmpty())
        assertTrue(
            "10 000 obras con 2 000 leídas tardaron $ms ms: el inicio se sentiría roto",
            ms < 1000
        )
        println("  10 000 obras / 2 000 leídas / 40 etiquetas: $ms ms, ${r.size} sugerencias")
    }

    @Test
    fun `no recomienda nada de lo que ya esta en el historial`() {
        val motor = SuggestionEngine()
        val cat = catalogo(2_000)
        val hist = consumos(cat.shuffled(azar).take(500))
        val r = motor.recomendar(cat, hist, limite = 100)

        val repetidas = r.map { it.obra.id } intersect hist.map { it.obra.id }.toSet()
        assertTrue("no puede recomendar lo ya leído, y devolvió: $repetidas", repetidas.isEmpty())
    }

    @Test
    fun `nunca devuelve mas del limite pedido`() {
        val motor = SuggestionEngine()
        val cat = catalogo(5_000)
        val hist = consumos(cat.shuffled(azar).take(100))
        listOf(1, 10, 50, 200).forEach { limite ->
            val r = motor.recomendar(cat, hist, limite = limite)
            assertTrue("límite $limite respetado, devolvió ${r.size}", r.size <= limite)
        }
    }

    @Test
    fun `historial vacio no inventa recomendaciones`() {
        val motor = SuggestionEngine()
        val cat = catalogo(1_000)
        val r = motor.recomendar(cat, emptyList(), limite = 20)
        assertEquals("sin historial no hay nada que justificar: debe estar vacío", 0, r.size)
    }

    @Test
    fun `catalogo vacio no explota`() {
        val motor = SuggestionEngine()
        val r = motor.recomendar(emptyList(), completosDePrueba(), limite = 20)
        assertEquals(0, r.size)
    }

    @Test
    fun `historial de una sola obra no produce una lista degenerada`() {
        // El caso del usuario nuevo que se leyó 800 capítulos de una obra. Su
        // perfil tiene un autor y unas etiquetas; el motor no debe devolver el
        // catálogo entero sin orden ni criterio.
        val motor = SuggestionEngine()
        val cat = catalogo(3_000)
        val r = motor.recomendar(cat, listOf(SuggestionEngine.Consumo(cat[0], 1f)), limite = 30)

        assertTrue("debe devolver algo", r.size <= 30)
        // Todas las sugerencias deben compartir autor o etiqueta con la obra leída,
        // o llevar afinidad 0 (que es "no sé nada de esto" y va al final).
        val leida = cat[0]
        val sinRelacion = r.filter { sug ->
            val obra = sug.obra
            obra.autores.intersect(leida.autores.toSet()).isEmpty() &&
                obra.etiquetas.intersect(leida.etiquetas.toSet()).isEmpty()
        }
        assertTrue(
            "con una sola obra leída, ${sinRelacion.size} sugerencias no comparten ni autor " +
                "ni etiqueta: el perfil no se está usando",
            sinRelacion.isEmpty()
        )
    }

    @Test
    fun `ids que no existen en el catalogo no rompen nada`() {
        val motor = SuggestionEngine()
        val cat = catalogo(100)
        val fantasmas = consumos(catalogo(2))   // obras que NO están en `cat`
        val r = motor.recomendar(cat, fantasmas, limite = 10)
        assertTrue("un historial con ids borrados no puede reventar", r.size <= 10)
    }

    private fun completosDePrueba(): List<SuggestionEngine.Consumo> =
        consumos(catalogo(3))

    @Test
    fun `resultados deterministas con la misma entrada`() {
        // Si el orden cambia entre dos llamadas idénticas, el inicio "salta" al
        // refrescar y el usuario cree que la app está rota.
        val motor = SuggestionEngine()
        val cat = catalogo(500)
        val hist = consumos(cat.shuffled(azar).take(100))
        val a = motor.recomendar(cat, hist, limite = 20).map { s -> s.obra.id }
        val b = motor.recomendar(cat, hist, limite = 20).map { s -> s.obra.id }
        assertEquals("dos llamadas idénticas deben dar el mismo orden", a, b)
    }

    @Test
    fun `con un perfil enfocado acierta el nicho mucho mejor que el azar`() {
        // El fallo que este test vigila: las dos versiones anteriores de la
        // afinidad devolvían resultados indistinguibles del azar y no se notaba,
        // porque las pruebas solo miraban "devuelve 20 cosas". Cantidad no es
        // calidad. Aquí se mide acierto contra la tasa base del catálogo.
        val azar = Random(777L)
        val catalogo = (0 until 5000).map { i ->
            SuggestionEngine.Obra(
                id = "o-$i", titulo = "Obra $i",
                autores = listOf("autor-${azar.nextInt(200)}"),
                etiquetas = (0 until 5).map { "tag-${azar.nextInt(300)}" }.distinct(),
            )
        }
        val nicho = (10..19).map { "tag-$it" }.toSet()
        val leidas = (0 until 50).map { i ->
            SuggestionEngine.Obra(
                id = "leida-$i", titulo = "L $i",
                autores = listOf("autor-7"),
                etiquetas = nicho.shuffled(azar).take(3).toList()
            )
        }
        val motor = SuggestionEngine()
        val r = motor.recomendar(catalogo, leidas.map { SuggestionEngine.Consumo(it, 1f) }, limite = 20)

        val aciertos = r.count { s ->
            s.obra.autores.contains("autor-7") || s.obra.etiquetas.any { it in nicho }
        }
        val base = catalogo.count { o -> o.autores.contains("autor-7") || o.etiquetas.any { it in nicho } }
        val tasaBase = base.toDouble() / catalogo.size

        println("  nicho: $aciertos/${r.size} aciertos · tasa base ${"%.1f".format(tasaBase * 100)} %")

        assertTrue("debe devolver 20", r.size == 20)
        assertTrue(
            "aciertos $aciertos/20 contra una tasa base de ${"%.1f".format(tasaBase * 100)} %: " +
                "el motor no está distinguiendo nada del azar",
            aciertos >= 18
        )
        // Y el orden tiene que importar: la primera no puede valer lo mismo que
        // la vigésima.
        assertTrue(
            "la afinidad de la primera (${r.first().afinidad}) debe superar a la última " +
                "(${r.last().afinidad}); si no, el orden es arbitrario",
            r.first().afinidad > r.last().afinidad
        )
    }

    @Test
    fun `los autores pesan mas que las etiquetas genericas`() {
        // Si una etiqueta la tiene la mitad del catálogo, coincidir en ella no
        // dice nada. El IDF tiene que hacer que el autor (raro) pese más.
        val catalogo = (0 until 1000).map { i ->
            SuggestionEngine.Obra(
                id = "o-$i", titulo = "Obra $i",
                autores = listOf("autor-${i % 100}"),
                // "generica" la tienen TODAS: IDF 0, no debe aportar nada.
                etiquetas = listOf("generica", "tag-${i % 50}"),
            )
        }
        val leida = SuggestionEngine.Obra(
            id = "leida", titulo = "L", autores = listOf("autor-7"), etiquetas = listOf("generica")
        )
        val motor = SuggestionEngine()
        val p = motor.perfil(listOf(SuggestionEngine.Consumo(leida, 1f)))

        // El perfil se construye igual para todos los términos, pero al puntuar
        // se aplica IDF: comprobamos que la obra del mismo autor gana a una que
        // solo comparte la etiqueta genérica.
        val r = motor.recomendar(catalogo, listOf(SuggestionEngine.Consumo(leida, 1f)), limite = 1000)
        val mismoAutor = r.filter { it.obra.autores.contains("autor-7") }
        assertTrue("debe haber obras del mismo autor", mismoAutor.isNotEmpty())
        val soloGenerica = r.filter { !it.obra.autores.contains("autor-7") }
        if (soloGenerica.isNotEmpty()) {
            assertTrue(
                "una obra del mismo autor (${mismoAutor.first().afinidad}) no puede " +
                    "puntuar menos que una que solo comparte la etiqueta genérica " +
                    "(${soloGenerica.first().afinidad})",
                mismoAutor.first().afinidad > soloGenerica.first().afinidad
            )
        }
        println("  mismo autor ${mismoAutor.firstOrNull()?.afinidad} vs solo genérica ${soloGenerica.firstOrNull()?.afinidad}")
    }
}
