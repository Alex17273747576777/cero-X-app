package com.ceroX.app.modules.kisara

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BibliotecaKisaraTest {
    private fun obra(
        id: String, titulo: String, autor: String = "Autor", tags: List<String> = emptyList(),
        leidos: Int = 0, progreso: Float = 0f, fuente: String = id,
    ) = KisaraLibraryEntry(
        id, titulo, autor, null, null, progreso, leidos, 20,
        sourceId = fuente, sources = listOf(fuente), tags = tags,
    )

    @Test fun `busqueda admite comillas exclusion campos y AND`() {
        val lista = listOf(
            obra("1", "One Piece", tags = listOf("acción", "piratas")),
            obra("2", "One Punch Man", tags = listOf("acción", "héroes")),
            obra("3", "Boku no Hero", autor = "Kohei", tags = listOf("héroes")),
        )
        assertEquals(listOf("1"), BusquedaBiblioteca.filtrar(lista, "\"one piece\" -héroes").map { it.id })
        assertEquals(listOf("2"), BusquedaBiblioteca.filtrar(lista, "tag:acción -tag:piratas").map { it.id })
        assertEquals(listOf("3"), BusquedaBiblioteca.filtrar(lista, "author:kohei hero").map { it.id })
        assertEquals(3, BusquedaBiblioteca.filtrar(lista, "").size)
    }

    @Test fun `fusion conserva maximos y no suma capitulos repetidos`() {
        val lista = listOf(
            obra("a", "Obra", leidos = 4, progreso = .2f, fuente = "local").copy(isFavorite = true),
            obra("b", "Obra", leidos = 9, progreso = .5f, fuente = "web", tags = listOf("acción")),
            obra("c", "Obra", leidos = 7, progreso = .8f, fuente = "nox"),
        )
        val fusion = fusionarBiblioteca(lista, "a", listOf("b", "c")).single()
        assertEquals(9, fusion.chaptersRead)
        assertEquals(.8f, fusion.progress)
        assertEquals(listOf("local", "web", "nox"), fusion.sources)
        assertEquals(listOf("acción"), fusion.tags)
        assertTrue(fusion.isFavorite)
    }

    @Test fun `fusion usa el principal acumulado y es idempotente con ids repetidos`() {
        val lista = listOf(
            obra("a", "Obra", leidos = 2, fuente = "a"),
            obra("b", "Obra", leidos = 5, fuente = "b"),
            obra("c", "Obra", leidos = 8, fuente = "c"),
        )
        val fusion = fusionarBiblioteca(lista, "a", listOf("b", "b", "a", "c")).single()
        assertEquals(8, fusion.chaptersRead)
        assertEquals(3, fusion.sources.size)
    }

    @Test fun `modulo y pantalla comparten operaciones sin lista fantasma`() {
        val modulo = KisaraLibraryModule()
        modulo.initialize(emptyMap())
        modulo.replaceEntries(listOf(obra("a", "Uno"), obra("b", "Dos")))
        modulo.toggleBulkSelection("a")
        modulo.toggleBulkSelection("b")
        modulo.mergeEntries("a", listOf("b"))
        assertEquals(1, modulo.listEntries().size)
        assertEquals(0, modulo.selectedCount())
        modulo.setFavorite("a", true)
        assertTrue(modulo.listEntries().single().isFavorite)
        modulo.setFavorite("a", false)
        assertFalse(modulo.listEntries().single().isFavorite)
    }
}
