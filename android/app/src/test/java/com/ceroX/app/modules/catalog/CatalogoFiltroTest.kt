package com.ceroX.app.modules.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoFiltroTest {
    private val apps = listOf(
        CatalogApp("m", "Mihon", repository = "https://github.com/mihonapp/mihon", category = "manga", platform = "Android"),
        CatalogApp("a", "Anikku", repository = "https://anikku-app.github.io/", category = "anime", platform = "Android"),
        CatalogApp("s", "Komga", repository = "https://komga.org/", category = "server", platform = "Server"),
    )

    @Test
    fun categoria_y_busqueda_se_combinan_en_lugar_de_descartar_el_texto() {
        assertEquals(listOf("Mihon"), filtrarCatalogo(apps, "android", "manga").map { it.name })
        assertTrue(filtrarCatalogo(apps, "mihon", "anime").isEmpty())
        assertEquals(3, filtrarCatalogo(apps, "", null).size)
    }

    @Test
    fun tambien_busca_repositorio_plataforma_y_funciones() {
        val conFuncion = apps + CatalogApp(
            "x", "Otra", category = "tools", bestFeatures = listOf("Lectura sin conexión"),
        )
        assertEquals(listOf("Mihon"), filtrarCatalogo(conFuncion, "mihonapp", null).map { it.name })
        assertEquals(listOf("Otra"), filtrarCatalogo(conFuncion, "sin conexión", null).map { it.name })
        assertEquals(listOf("Komga"), filtrarCatalogo(conFuncion, "server", null).map { it.name })
    }
}
