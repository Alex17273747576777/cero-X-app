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
    fun el_asistente_es_alcanzable_y_ejecuta_un_cliente_real() {
        val main = fuente("ui/main/MainActivity.kt")
        assertTrue("no hay camino del panel a IA", main.contains("PantallaIaActivity::class.java"))
        assertTrue("falta la actividad de IA", manifiesto().contains(".ui.ia.PantallaIaActivity"))
        val pantalla = fuente("ui/ia/AsistenteChat.kt")
        assertTrue("el chat no usa el módulo", pantalla.contains("motor.conversar"))
        assertTrue("la red bloquea la interfaz", pantalla.contains("Dispatchers.IO"))
        val modulo = fuente("modules/aihub/AIHubModule.kt")
        assertTrue("el módulo no llama al cliente", modulo.contains("cliente.conversar"))
    }

    @Test
    fun extensiones_y_biblioteca_son_alcanzables_sin_prometer_parsers_cargados() {
        val main = fuente("ui/main/MainActivity.kt")
        assertTrue(main.contains("IntegracionesActivity::class.java"))
        assertTrue(manifiesto().contains(".ui.extensiones.IntegracionesActivity"))
        assertTrue(manifiesto().contains(".ui.extensiones.KisaraActivity"))
        val pantalla = fuente("ui/extensiones/IntegracionesActivity.kt")
        assertTrue(pantalla.contains("InstaladorProjectNox"))
        assertTrue(pantalla.contains("KisaraActivity::class.java"))
        assertTrue("Kotatsu no debe confundirse con Nox ejecutándose", pantalla.contains("Explorador, no ejecución de parsers"))
        assertTrue("debe existir la acción interna explícita", pantalla.contains("InstaladorInternoNox"))
        assertTrue("debe distinguir ejecución de instalación Android", pantalla.contains("Son acciones distintas"))
        val repositorio = fuente("sources/ContentRepository.kt")
        assertTrue(repositorio.contains("getSearchManga"))
        assertTrue(repositorio.contains("getMangaUpdate"))
        assertTrue(repositorio.contains("getPageList"))
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

    // ── Tres fallos que el CI no veía (2026-11-10) ────────────────────────────
    //
    // Las pruebas de Robolectric inflan una Activity aunque no esté en el
    // manifiesto y no resuelven autoridades de FileProvider, así que estos tres
    // errores pasaban en verde y sólo reventaban en un teléfono.

    private fun fuentes(): List<File> =
        File(raiz, "src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val claseActivity = Regex("""class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*(\w*Activity)\(""")

    @Test
    fun toda_activity_del_codigo_esta_declarada_en_el_manifiesto() {
        val m = manifiesto()
        val sinDeclarar = fuentes().flatMap { f ->
            claseActivity.findAll(f.readText()).map { it.groupValues[1] }.toList()
        }.filter { nombre -> !Regex("""android:name="[\w.]*\.""" + nombre + "\"").containsMatchIn(m) }
        assertTrue("Activity sin declarar (startActivity lanzaría): $sinDeclarar", sinDeclarar.isEmpty())
    }

    @Test
    fun ninguna_pantalla_appcompat_usa_un_tema_de_plataforma() {
        // Todas las pantallas son AppCompatActivity: un tema `@android:style/…`
        // no desciende de Theme.AppCompat y la pantalla muere al crearse.
        val temas = Regex("""android:theme="([^"]+)"""").findAll(manifiesto()).map { it.groupValues[1] }.toList()
        assertTrue("no se leyó ningún tema", temas.isNotEmpty())
        val malos = temas.filter { it.startsWith("@android:style/") }
        assertTrue("tema que no es AppCompat: $malos", malos.isEmpty())
        val estilos = File(raiz, "src/main/res/values/themes.xml").readText()
        temas.filter { it.startsWith("@style/") }.forEach { t ->
            assertTrue("el tema $t no existe en themes.xml",
                estilos.contains("name=\"" + t.removePrefix("@style/") + "\""))
        }
    }

    @Test
    fun toda_autoridad_de_fileprovider_usada_esta_declarada() {
        val m = manifiesto()
        val declaradas = Regex("""android:authorities="\$\{applicationId\}(\.\w+)"""").findAll(m)
            .map { it.groupValues[1] }.toSet()
        assertTrue("no hay FileProvider declarado", ".fileprovider" in declaradas)
        // En el código la autoridad siempre se arma como "${…packageName}.algo".
        val usadas = fuentes().flatMap { f ->
            Regex("""ackageName\}(\.\w+)"""").findAll(f.readText()).map { f.name to it.groupValues[1] }.toList()
        }
        assertTrue("no se encontró ningún uso de FileProvider", usadas.isNotEmpty())
        val huerfanas = usadas.filter { it.second !in declaradas }
        assertTrue("autoridad usada pero no declarada (getUriForFile lanzaría): $huerfanas", huerfanas.isEmpty())
    }

    @Test
    fun el_fileprovider_no_expone_la_raiz_del_telefono() {
        val rutas = File(raiz, "src/main/res/xml/file_paths.xml").readText()
        assertTrue("root-path deja compartir cualquier ruta, /data incluida", !rutas.contains("<root-path"))
    }
}
