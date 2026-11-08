package com.ceroX.app.ui.estado

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Que las pantallas existan **y estén declaradas en el manifiesto**.
 *
 * POR QUÉ ESTA PRUEBA LEE ARCHIVOS EN VEZ DE EJECUTAR ANDROID
 * -----------------------------------------------------------
 * Una `Activity` que existe en Kotlin pero no está en `AndroidManifest.xml` no se
 * puede abrir: la app compila, las pruebas unitarias pasan, y al pulsar el botón
 * hay un `ActivityNotFoundException`. Ese fallo no lo detecta ninguna prueba de
 * lógica y no aparece hasta que alguien toca el botón en un teléfono —que aquí no
 * hay—. Leer el manifiesto y el fuente es lo que sí se puede verificar sin
 * aparato, y verifica justo lo que se rompe.
 *
 * Lo mismo con la navegación: si `MainActivity` no lanza el `Intent`, la pantalla
 * es inalcanzable aunque esté declarada. Se comprueban las dos cosas: declarada y
 * lanzada.
 */
class NavegacionTest {

    private val raiz: File = run {
        // Desde el directorio de trabajo de las pruebas se sube hasta `app/`.
        var d = File(".").absoluteFile
        while (!File(d, "src/main/AndroidManifest.xml").isFile && d.parentFile != null) d = d.parentFile
        require(File(d, "src/main/AndroidManifest.xml").isFile) { "no se encontró el manifiesto" }
        d
    }

    private fun manifiesto(): String = File(raiz, "src/main/AndroidManifest.xml").readText()

    private fun fuente(ruta: String): String {
        val f = File(raiz, "src/main/java/com/ceroX/app/$ruta")
        assertTrue("no existe el fuente: $ruta", f.isFile)
        return f.readText()
    }

    @Test
    fun las_cuatro_pantallas_del_camino_de_lectura_estan_declaradas() {
        val m = manifiesto()
        listOf(
            ".ui.main.MainActivity",
            ".ui.catalogo.CatalogoActivity",
            ".ui.reader.ReaderActivity",
            ".modules.houri.tracker.TrackerActivity",
            ".ui.estado.EstadoSistemaActivity",
        ).forEach { actividad ->
            assertTrue("falta en el manifiesto: $actividad", m.contains("android:name=\"$actividad\""))
        }
    }

    @Test
    fun desde_el_panel_se_llega_al_catalogo_y_al_lector() {
        // El camino completo: Main -> Catalogo -> Reader.
        val main = fuente("ui/main/MainActivity.kt")
        assertTrue("MainActivity no abre el catálogo", main.contains("CatalogoActivity::class.java"))

        val catalogo = fuente("ui/catalogo/CatalogoActivity.kt")
        assertTrue("el catálogo no abre el lector", catalogo.contains("ReaderActivity::class.java"))
        // Y le pasa la fuente, la obra y el capítulo: sin eso el lector no sabe
        // qué abrir y muestra una pantalla de error.
        listOf("EXTRA_FUENTE_ID", "EXTRA_OBRA", "EXTRA_CAPITULO", "EXTRA_URL_CAPITULO", "EXTRA_MODO")
            .forEach { assertTrue("el catálogo no pasa $it al lector", catalogo.contains(it)) }
    }

    @Test
    fun el_tracker_y_el_estado_del_sistema_son_alcanzables() {
        val catalogo = fuente("ui/catalogo/CatalogoActivity.kt")
        assertTrue(
            "no hay camino a la pantalla de sincronización",
            catalogo.contains("TrackerActivity::class.java"),
        )
        val main = fuente("ui/main/MainActivity.kt")
        assertTrue(
            "no hay camino al estado del sistema",
            main.contains("EstadoSistemaActivity::class.java"),
        )
    }

    @Test
    fun la_pantalla_de_lectura_no_es_un_esqueleto() {
        val lector = fuente("ui/reader/ReaderActivity.kt")
        // Lo que hace que sea un lector y no un visor vacío.
        listOf("LectorScreen", "PageLoader", "PageCache", "ReadingProgress", "ReaderState")
            .forEach { assertTrue("al lector le falta $it", lector.contains(it)) }
        // Y que se pueda salir: sin `finish` la pantalla atrapa al usuario.
        assertTrue("el lector no se puede cerrar", lector.contains("finish()"))
    }

    @Test
    fun la_pantalla_de_estado_comprueba_las_tres_cosas_que_dice_comprobar() {
        val estado = fuente("ui/estado/EstadoSistemaActivity.kt")
        assertTrue("no comprueba privilegios", estado.contains("PrivilegeProbe"))
        assertTrue("no comprueba el motor JS", estado.contains("QuickJsEngine"))
        assertTrue("no dice qué fuentes hay", estado.contains("RepositorioGlobal"))
        // Y que no prometa instalar ni pedir permisos: solo lee y pinta.
        assertTrue(
            "la pantalla de estado no puede instalar ni cambiar ajustes",
            !estado.contains("PackageInstaller") && !estado.contains("requestPermissions"),
        )
    }
}
