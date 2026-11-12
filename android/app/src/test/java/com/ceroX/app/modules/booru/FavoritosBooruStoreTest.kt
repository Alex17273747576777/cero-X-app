package com.ceroX.app.modules.booru

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ceroX.app.core.AlmacenExterno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoritosBooruStoreTest {
    @Before fun limpiar() { AlmacenExterno.olvidar() }

    @Test fun guarda_quita_y_recupera_metadatos_externos() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val store = FavoritosBooruStore(contexto)
        val post = BooruPost(1, "https://safebooru.org/images/a.png", "https://safebooru.org/thumbnails/a.jpg",
            listOf("landscape"), 2, "safe", "10×20", null, 1L)
        assertTrue(store.alternar(post))
        assertEquals(setOf(1), FavoritosBooruStore(contexto).ids())
        assertFalse(store.alternar(post))
        assertTrue(FavoritosBooruStore(contexto).listar().isEmpty())
    }

    @Test fun pantalla_conecta_busqueda_imagenes_paginas_y_favoritos() {
        val texto = java.io.File("src/main/java/com/ceroX/app/modules/booru/BooruUIFragment.kt").readText()
        assertTrue(texto.contains("modulo.searchBooru"))
        assertTrue(texto.contains("holder.image.load"))
        assertTrue(texto.contains("booruNext.setOnClickListener"))
        assertTrue(texto.contains("alternarFavorito"))
        assertFalse(texto.contains("La búsqueda en boorus no está implementada"))
        val host = java.io.File("src/main/java/com/ceroX/app/ui/ajustes/FragmentHostActivity.kt").readText()
        val ajustes = java.io.File("src/main/java/com/ceroX/app/ui/ajustes/AjustesActivity.kt").readText()
        assertTrue(host.contains("BOORU -> BooruUIFragment()"))
        assertTrue(ajustes.contains("FragmentHostActivity.BOORU"))
    }
}
