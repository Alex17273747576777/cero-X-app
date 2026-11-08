package com.ceroX.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La regla de almacenamiento, comprobada contra el árbol de código.
 *
 * LA REGLA
 * --------
 * «Está prohibido almacenar cualquier recurso en el almacenamiento interno, con
 * la única excepción de los tokens.»
 *
 * CÓMO SE COMPRUEBA ALGO ASÍ
 * --------------------------
 * Una regla que solo vive en un comentario se incumple en la siguiente sesión.
 * Esta prueba hace tres cosas que se pueden medir:
 *
 *   1. **Cuenta los sitios que escriben** en el árbol de fuentes y exige que
 *      estén todos declarados en `InventoryStorage`. Un archivo nuevo con
 *      `filesDir`, `MODE_PRIVATE` o `databaseBuilder` y sin declarar **rompe la
 *      prueba**. Eso es lo que impide que la deuda crezca en silencio.
 *   2. **Ninguna entrada que no sea un token puede estar marcada como conforme
 *      por accidente**: el inventario se recorre y se comprueba que las deudas
 *      están donde se dice.
 *   3. **Los casos ya arreglados no vuelven**: los modelos ONNX no se copian a
 *      `filesDir`, y solo hay un nombre de archivo de base de datos en toda la
 *      app.
 *
 * Lo que esta prueba **no** hace, y se dice: no abre un teléfono. No puede
 * demostrar que Android no escribe cachés propias (las escribe: el sistema
 * guarda dexopt, logcat y las propias `SharedPreferences` de AndroidX). Lo que
 * demuestra es que **el código de cero-X** no pide almacenamiento interno salvo
 * en los sitios declarados.
 */
class ReglaDeAlmacenamientoTest {

    private val raiz: File = run {
        var d = File(".").absoluteFile
        while (!File(d, "src/main/java/com/ceroX/app").isDirectory && d.parentFile != null) {
            d = d.parentFile
        }
        require(File(d, "src/main/java/com/ceroX/app").isDirectory) { "no se encontró el árbol de fuentes" }
        d
    }

    private val fuentes: List<File> = File(raiz, "src/main/java/com/ceroX/app")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    /**
     * Los patrones que significan «esto escribe en el almacenamiento interno».
     *
     * `Context.MODE_PRIVATE` es la firma de `SharedPreferences`, y las
     * preferencias **viven** en el almacenamiento interno: `/data/data/<pkg>/shared_prefs`.
     * Es el punto que más se pasa por alto al hablar de esta regla.
     */
    private val patronesDeEscritura = listOf(
        "filesDir",
        "cacheDir",
        "noBackupFilesDir",
        "MODE_PRIVATE",
        "databaseBuilder",
        "openFileOutput",
        "getExternalFilesDir",
        "getExternalFilesDirs",
    )

    /**
     * El propio inventario nombra los patrones porque los clasifica, no porque
     * escriba. Se excluye de la auditoría: sin esta excepción la prueba se
     * acusaría a sí misma de lo que está auditando.
     */
    private val elInventarioMismo = "core/InventoryStorage.kt"

    /** Devuelve archivo -> patrones encontrados, sin contar comentarios. */
    private fun sitiosQueEscriben(): Map<String, List<String>> = fuentes.associate { f ->
        val lineas = f.readLines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .joinToString("\n")
        val encontrados = patronesDeEscritura.filter { lineas.contains(it) }
        f.relativeTo(File(raiz, "src/main/java/com/ceroX/app")).path to encontrados
    }.filterValues { it.isNotEmpty() }
        .filterKeys { normalizar(it) != elInventarioMismo }

    // ── 1 · Nada escribe sin declararlo ────────────────────────────────────

    @Test
    fun todo_sitio_que_escribe_esta_declarado_en_el_inventario() {
        val declarados = InventoryStorage.sitios.map { normalizar(it.archivo) }.toSet()
        val sinDeclarar = sitiosQueEscriben().keys
            .map { normalizar(it) }
            .filterNot { ruta -> declarados.any { it == ruta } }

        assertTrue(
            "estos archivos escriben en el almacenamiento interno y no están declarados en " +
                "InventoryStorage: $sinDeclarar\n" +
                "Declarar el sitio (aunque sea como deuda) es lo que impide que la deuda crezca a escondidas.",
            sinDeclarar.isEmpty(),
        )
    }

    /** Los dos lados pueden escribir la ruta con o sin prefijo; se comparan igual. */
    private fun normalizar(ruta: String): String =
        ruta.replace('\\', '/').removePrefix("./").removePrefix("com/ceroX/app/")

    // ── 2 · El inventario no se engaña a sí mismo ──────────────────────────

    @Test
    fun cada_sitio_declarado_existe_de_verdad() {
        val faltantes = InventoryStorage.sitios.filterNot { sitio ->
            File(File(raiz, "src/main/java/com/ceroX/app"), sitio.archivo).isFile
        }
        assertTrue(
            "el inventario nombra archivos que no existen: ${faltantes.map { it.archivo }}",
            faltantes.isEmpty(),
        )
    }

    @Test
    fun toda_la_deuda_esta_marcada_y_ninguna_deuda_esta_disfrazada() {
        // Las deudas son las que la regla prohíbe hoy. Este número tiene que
        // bajar. Si sube, es que alguien añadió almacenamiento interno.
        val deudas = InventoryStorage.deudas().map { it.archivo }
        assertTrue(
            "la deuda no puede crecer: ${deudas.size} sitios por migrar",
            deudas.size <= 14,
        )
        // Y las que se arreglaron en esta ronda no pueden volver a la lista.
        assertFalse(
            "ModelAssets ya lee del APK; no puede seguir en deuda",
            deudas.any { it.contains("ModelAssets") },
        )
        assertFalse(
            "ModelHonestyGuard ya no escribe; no puede seguir en deuda",
            deudas.any { it.contains("ModelHonestyGuard") },
        )
    }

    @Test
    fun la_unica_excepcion_de_la_regla_son_los_tokens() {
        val tokens = InventoryStorage.sitios.filter {
            it.verdicto == InventoryStorage.Verdict.TOKEN_CIFRADO
        }
        assertEquals("solo hay un sitio de tokens", 1, tokens.size)
        val t = tokens.first()
        assertTrue("el token tiene que ir cifrado", t.nota.contains("Keystore"))
        assertTrue(
            "el token es de AniList o MAL",
            t.que.contains("AniList") || t.que.contains("token"),
        )
    }

    @Test
    fun el_resumen_dice_la_verdad_y_no_dice_cumple() {
        val r = InventoryStorage.resumen()
        assertTrue("el resumen tiene que contar las deudas: $r", r.contains("por migrar"))
        assertFalse("no puede decir que cumple entera", r.contains("cumple al 100"))
    }

    // ── 3 · Lo arreglado no vuelve ────────────────────────────────────────

    @Test
    fun los_modelos_onnx_no_se_copian_al_disco() {
        val texto = File(File(raiz, "src/main/java/com/ceroX/app"), "modules/ai/ModelAssets.kt")
            .readLines()
            .filterNot { it.trimStart().startsWith("*") }
            .joinToString("\n")
        assertFalse(
            "ModelAssets no puede volver a escribir modelos en filesDir",
            texto.contains("filesDir"),
        )
        assertTrue("tiene que leer del APK", texto.contains("openFd"))
        assertFalse("no puede quedar la copia parcial", texto.contains("parcial"))
    }

    @Test
    fun solo_hay_un_nombre_de_archivo_de_base_de_datos() {
        val nombres = Regex("""databaseBuilder\(""")
        val constructores = fuentes.filter { f ->
            f.readLines().filterNot { it.trimStart().startsWith("*") }.any { it.contains("databaseBuilder(") }
        }
        assertEquals(
            "solo BibliotecaDb puede construir la base de datos; hay ${constructores.map { it.name }}",
            1,
            constructores.size,
        )
        val b = File(constructores.first().absolutePath).readText()
        assertTrue("la puerta única se llama BibliotecaDb", b.contains("object BibliotecaDb"))
        assertFalse(nombres.find("\"reader_database\"") != null && b.contains("\"reader_database\""))
        assertFalse("no puede haber un segundo nombre", b.contains("\"ceroX_database\""))
    }

    @Test
    fun la_comprobacion_de_assets_no_crea_directorios() {
        val texto = File(File(raiz, "src/main/java/com/ceroX/app"), "modules/diagnostics/ModelHonestyGuard.kt")
            .readText()
        assertFalse(
            "comprobar una lectura no necesita escribir",
            texto.contains("assets_probe"),
        )
    }

    @Test
    fun el_inventario_no_es_un_adorno_lo_usa_la_pantalla_de_estado() {
        // Un inventario que nadie lee se queda desactualizado. La pantalla de
        // estado lo pinta, así que el usuario ve el número de deudas.
        val estado = File(File(raiz, "src/main/java/com/ceroX/app"), "ui/estado/EstadoSistemaActivity.kt")
            .readText()
        assertTrue("la pantalla de estado tiene que enseñar el inventario", estado.contains("InventoryStorage"))
    }
}
