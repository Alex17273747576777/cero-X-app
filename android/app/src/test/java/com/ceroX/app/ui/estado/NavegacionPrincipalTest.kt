package com.ceroX.app.ui.estado

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NavegacionPrincipalTest {
    private val raiz: File = run {
        var d = File(".").absoluteFile
        while (!File(d, "src/main/AndroidManifest.xml").isFile && d.parentFile != null) d = d.parentFile
        d
    }
    private fun fuente(ruta: String) = File(raiz, "src/main/java/com/ceroX/app/$ruta").readText()

    @Test
    fun la_barra_tiene_cinco_destinos_claros_y_conserva_la_pestana() {
        val main = fuente("ui/main/MainActivity.kt")
        val ids = Regex("""Destino\("([a-z]+)"""").findAll(main).map { it.groupValues[1] }.toList()
        assertEquals(listOf("panel", "biblioteca", "fuentes", "web", "ia"), ids)
        assertTrue(main.contains("rememberSaveable"))
        assertFalse(main.contains("Destino(\"modos\""))
        assertFalse(main.contains("Destino(\"booru\""))
        assertFalse(Regex("""Destino\([^\n]+Icons\.Default\.Refresh""").containsMatchIn(main))
    }

    @Test
    fun ajustes_y_sus_pantallas_secundarias_son_alcanzables() {
        val manifest = File(raiz, "src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".ui.ajustes.AjustesActivity"))
        assertTrue(manifest.contains(".ui.ajustes.FragmentHostActivity"))
        val ajustes = fuente("ui/ajustes/AjustesActivity.kt")
        listOf("DISENO", "MODOS", "IPTV", "APPS", "IdiomasActivity", "EstadoSistemaActivity")
            .forEach { assertTrue("Falta $it", ajustes.contains(it)) }
        val host = fuente("ui/ajustes/FragmentHostActivity.kt")
        listOf("ThemeSwitcherFragment", "ModeSwitcherFragment", "IptvUiFragment", "CatalogUIFragment")
            .forEach { assertTrue("Falta $it", host.contains(it)) }
    }

    @Test
    fun diseno_y_modos_no_muestran_controles_sin_efecto() {
        val tema = fuente("modules/theme/ThemeSwitcherFragment.kt")
        assertFalse(tema.contains("blurSeekbar"))
        assertFalse(tema.contains("liquidGlassSwitch"))
        val modos = fuente("modules/mode/ModeSwitcherFragment.kt")
        listOf("CatalogoActivity", "IntegracionesActivity", "PantallaIaActivity", "FragmentHostActivity")
            .forEach { assertTrue("Modo sin destino $it", modos.contains(it)) }
        assertTrue(fuente("modules/mode/ModeManagerModule.kt").contains("MODOS_NAVEGABLES"))
    }

    @Test
    fun el_catalogo_de_apps_filtra_y_abre_enlaces_en_vez_de_mostrar_un_toast_placeholder() {
        val pantalla = fuente("modules/catalog/CatalogUIFragment.kt")
        assertTrue(pantalla.contains("filtrarCatalogo"))
        assertTrue(pantalla.contains("AlertDialog"))
        assertTrue(pantalla.contains("Intent.ACTION_VIEW"))
        assertTrue(pantalla.contains("emptyView"))
        assertFalse(pantalla.contains("El filtro por categorías no está implementado"))
    }
}
