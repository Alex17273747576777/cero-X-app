package com.ceroX.app.ui.estado

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DisenoViewsTest {
    private fun leer(ruta: String) = File(ruta).readText()

    @Test fun tema_daynight_separa_views_y_compose() {
        val temas = leer("src/main/res/values/themes.xml")
        val base = Regex("<style name=\"Theme.ceroX\" parent=\"([^\"]+)\">").find(temas)
        assertTrue("El tema base perdió ActionBar", base?.groupValues?.get(1) == "Theme.Material3.DayNight")
        val compose = temas.substringAfter("name=\"Theme.ceroX.Compose\"").substringBefore("</style>")
        assertTrue(compose.contains("windowActionBar\">false"))
        assertTrue(temas.contains("android:windowLightStatusBar\">@bool/cerox_light_bars"))
        assertTrue(File("src/main/res/values-night/colors.xml").isFile)
        assertFalse(leer("src/main/res/values/colors.xml") == leer("src/main/res/values-night/colors.xml"))
        val manifest = leer("src/main/AndroidManifest.xml")
        for (ruta in listOf(".ui.main.MainActivity", ".ui.ajustes.AjustesActivity", ".ui.extensiones.IntegracionesActivity", ".ui.StressTestActivity")) {
            val bloque = manifest.substringAfter("android:name=\"$ruta\"").substringBefore("/>")
            assertTrue("$ruta debe usar tema Compose sin barra duplicada", bloque.contains("android:theme=\"@style/Theme.ceroX.Compose\""))
        }
        for (ruta in listOf(".ui.idiomas.IdiomasActivity", ".ui.ia.PantallaIaActivity")) {
            val bloque = manifest.substringAfter("android:name=\"$ruta\"").substringBefore("/>")
            assertFalse("$ruta necesita su ActionBar nativa", bloque.contains("Theme.ceroX.Compose"))
        }
    }

    @Test fun layouts_no_fijan_colores_de_texto_o_fondo() {
        val layouts = File("src/main/res/layout").listFiles().orEmpty().filter { it.extension == "xml" }
        val fijos = layouts.flatMap { archivo ->
            Regex("android:(?:textColor|background)=\"#[0-9A-Fa-f]+\"")
                .findAll(archivo.readText()).map { "${archivo.name}:${it.value}" }
        }
        assertTrue("Colores que rompen DayNight: $fijos", fijos.isEmpty())
    }

    @Test fun salidas_visibles_en_ambas_tecnologias() {
        val integraciones = leer("src/main/java/com/ceroX/app/ui/extensiones/IntegracionesActivity.kt")
        assertTrue(integraciones.contains("navigationIcon"))
        assertTrue(integraciones.contains("contentDescription = \"Volver\""))
        for (ruta in listOf(
            "src/main/java/com/ceroX/app/ui/idiomas/IdiomasActivity.kt",
            "src/main/java/com/ceroX/app/ui/ia/PantallaIa.kt",
            "src/main/java/com/ceroX/app/modules/houri/tracker/TrackerActivity.kt",
            "src/main/java/com/ceroX/app/ui/ajustes/FragmentHostActivity.kt",
            "src/main/java/com/ceroX/app/ui/extensiones/KisaraActivity.kt",
        )) assertTrue("$ruta sin salida", leer(ruta).contains("setDisplayHomeAsUpEnabled(true)"))
        val catalogo = leer("src/main/java/com/ceroX/app/ui/catalogo/CatalogoActivity.kt")
        assertTrue(catalogo.contains("resources.displayMetrics.density"))
        assertTrue(catalogo.contains("Volver muestra resultados"))
    }
}
