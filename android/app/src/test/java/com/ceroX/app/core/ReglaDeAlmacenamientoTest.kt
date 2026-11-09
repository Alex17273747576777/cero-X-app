package com.ceroX.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La regla de almacenamiento, comprobada **sobre las fuentes**, después de la migración.
 *
 * QUÉ CAMBIÓ Y POR QUÉ ESTA PRUEBA ES DISTINTA
 * --------------------------------------------
 * La ronda anterior comprobaba que ningún archivo escribiera sin estar declarado
 * en el inventario. Eso impedía que la deuda creciera, pero no obligaba a que
 * bajara: se podía declarar «deuda» para siempre y la prueba seguía verde.
 *
 * Ahora se comprueba lo que de verdad importa:
 *
 *   1. **Nadie usa `SharedPreferences`** salvo para *leer y borrar* lo viejo.
 *   2. **`Room.databaseBuilder` aparece en un solo sitio** y con ruta absoluta.
 *   3. **No queda ninguna caída silenciosa al interno** (`?: filesDir`,
 *      `java.io.tmpdir`): era el patrón que metía respaldos e informes ahí.
 *   4. La deuda del inventario **bajó de 14 a 3**.
 *
 * LO QUE ESTA PRUEBA NO PUEDE DEMOSTRAR
 * -------------------------------------
 * No abre un teléfono. `getExternalFilesDirs` puede devolver una ruta no
 * escribible en un dispositivo concreto, y eso se mide en el aparato. Lo que
 * demuestra es que **el código de cero-X no pide el almacenamiento interno** salvo
 * en los tres sitios declarados.
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

    private val base: File = File(raiz, "src/main/java/com/ceroX/app")

    private val fuentes: List<File> = base.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    /** Las líneas de código de un archivo, sin comentarios ni KDoc. */
    private fun codigo(f: File): String = f.readLines()
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    private fun ruta(f: File): String = f.relativeTo(base).path.replace('\\', '/')

    // ── 1 · SharedPreferences, prohibido salvo para migrar ─────────────────

    @Test
    fun sharedPreferences_solo_se_toca_para_migrar_y_borrar() {
        val infractores = fuentes.filter { f ->
            val c = codigo(f)
            if (!c.contains("getSharedPreferences")) return@filter false
            val esLaMigracion = ruta(f) == "core/Prefs.kt" && c.contains(".edit().clear()")
            !esLaMigracion
        }.map { ruta(it) }

        assertTrue(
            "estos archivos usan SharedPreferences, que vive en el almacenamiento interno y no " +
                "se puede redirigir: $infractores. Los datos van a core/Prefs (JSON en " +
                "AlmacenExterno); SharedPreferences solo se permite en " +
                "Prefs.migrarDesdeSharedPreferences, que además borra lo migrado.",
            infractores.isEmpty(),
        )
    }

    @Test
    fun las_preferencias_borran_lo_que_migran_del_interno() {
        val c = codigo(File(base, "core/Prefs.kt"))
        assertTrue(
            "Prefs tiene que borrar las claves del interno después de copiarlas; si no, la " +
                "deuda sigue ahí, solo que disimulada",
            c.contains(".edit().clear()"),
        )
        assertTrue(
            "Prefs tiene que migrar lo de versiones anteriores, o actualizar borra los ajustes",
            c.contains("migrarDesdeSharedPreferences"),
        )
    }

    // ── 2 · Room, una sola puerta y con ruta absoluta ──────────────────────

    @Test
    fun solo_hay_un_constructor_de_room_y_usa_ruta_externa() {
        val constructores = fuentes.filter { codigo(it).contains("Room.databaseBuilder") }
            .map { ruta(it) }
        assertEquals(
            "tiene que haber exactamente un constructor de Room (BibliotecaDb): $constructores",
            listOf("modules/reader/BibliotecaDb.kt"),
            constructores,
        )

        val c = codigo(File(base, "modules/reader/BibliotecaDb.kt"))
        assertTrue(
            "BibliotecaDb tiene que sacar la ruta de AlmacenExterno",
            c.contains("AlmacenExterno.rutaBaseDeDatos"),
        )
        assertTrue(
            "el tercer argumento de databaseBuilder tiene que ser la ruta absoluta (`ruta,`), no " +
                "un nombre entre comillas: con nombre relativo Room usa el directorio interno",
            Regex("""databaseBuilder\([\s\S]{0,200}?ReaderRoomDatabase::class\.java,\s*\n\s*ruta,""")
                .containsMatchIn(c),
        )
        assertFalse(
            "no puede quedar ningún literal de base de datos pasado a databaseBuilder",
            Regex("""databaseBuilder[\s\S]{0,200}?"[a-zA-Z_]+\.db"""").containsMatchIn(c),
        )
        assertTrue(
            "la base vieja del interno se tiene que copiar CON sus -wal y -shm y borrarse; " +
                "copiar solo el .db pierde lo que no se ha volcado",
            c.contains("\"-wal\"") && c.contains("\"-shm\"") && c.contains(".delete()"),
        )
    }

    // ── 3 · Ninguna caída silenciosa al almacenamiento interno ─────────────

    @Test
    fun ninguna_ruta_cae_al_interno_sin_decirlo() {
        val patrones = listOf(
            "?: context.filesDir",
            "?: filesDir",
            "?: this.filesDir",
            "System.getProperty(\"java.io.tmpdir\")",
        )
        val infractores = fuentes.mapNotNull { f ->
            val c = codigo(f)
            val encontrados = patrones.filter { c.contains(it) }
            if (encontrados.isEmpty() || ruta(f) == "core/InventoryStorage.kt") null
            else "${ruta(f)} → $encontrados"
        }
        assertTrue(
            "estos archivos se caen al almacenamiento interno cuando no hay externo: " +
                "$infractores. La regla no admite «si no hay externo, al interno»: hay que " +
                "devolver el fallo con su motivo.",
            infractores.isEmpty(),
        )
    }

    @Test
    fun quien_escribe_fuera_lo_dice_en_su_archivo() {
        val escriben = listOf(
            "ui/reader/ReadingProgress.kt",
            "modules/reader/BibliotecaDb.kt",
            "modules/backup/BackupManager.kt",
            "core/CrashHandler.kt",
            "modules/extension_sandbox/ExtensionInstaller.kt",
            "modules/ai/NeuralAIModule.kt",
            "sources/RepositorioGlobal.kt",
            "core/Prefs.kt",
        )
        val sinRuta = escriben.filterNot { codigo(File(base, it)).contains("AlmacenExterno") }
        assertTrue(
            "estos archivos escriben y no resuelven la ruta por AlmacenExterno: $sinRuta",
            sinRuta.isEmpty(),
        )
    }

    // ── 4 · La deuda bajó de verdad ────────────────────────────────────────

    @Test
    fun la_deuda_bajo_de_catorce_a_tres() {
        val deudas = InventoryStorage.deudas().map { it.archivo }
        assertTrue(
            "la deuda tiene que haber bajado de 14 a 3; hoy hay ${deudas.size}: $deudas",
            deudas.size <= 3,
        )
    }

    @Test
    fun cada_sitio_declarado_existe_de_verdad() {
        val faltantes = InventoryStorage.sitios.filterNot { File(base, it.archivo).isFile }
        assertTrue(
            "el inventario nombra archivos que no existen: ${faltantes.map { it.archivo }}",
            faltantes.isEmpty(),
        )
    }

    @Test
    fun todo_sitio_que_escribe_esta_declarado_en_el_inventario() {
        val patrones = listOf(
            "filesDir", "cacheDir", "noBackupFilesDir", "MODE_PRIVATE",
            "databaseBuilder", "openFileOutput", "getExternalFilesDir", "getExternalFilesDirs",
        )
        val declarados = InventoryStorage.sitios.map { it.archivo }.toSet()
        val sinDeclarar = fuentes.mapNotNull { f ->
            val c = codigo(f)
            val encontrados = patrones.filter { c.contains(it) }
            if (encontrados.isEmpty()) return@mapNotNull null
            if (ruta(f) == "core/InventoryStorage.kt") return@mapNotNull null
            if (declarados.contains(ruta(f))) return@mapNotNull null
            "${ruta(f)} → $encontrados"
        }
        assertTrue(
            "estos archivos escriben en el almacenamiento interno y no están declarados: " +
                "$sinDeclarar",
            sinDeclarar.isEmpty(),
        )
    }

    @Test
    fun el_resumen_no_dice_que_todo_cumple_mientras_queden_deudas() {
        val resumen = InventoryStorage.resumen()
        if (InventoryStorage.deudas().isNotEmpty()) {
            assertFalse(
                "el resumen no puede decir «cumple» entero mientras queden " +
                    "${InventoryStorage.deudas().size} deudas: $resumen",
                resumen.startsWith("cumple") || resumen.contains("0 por migrar"),
            )
        }
        assertTrue(resumen.contains("por migrar"))
    }

    // ── 5 · Los sitios arreglados no vuelven atrás ────────────────────────

    @Test
    fun ningun_sitio_arreglado_esta_marcado_como_deuda() {
        val arreglados = listOf(
            "ReadingProgress", "LibraryRepository", "SingleSQLiteConnection",
            "BackupManager", "CrashHandler", "ConfigManager", "TrackerAutoSync",
            "ExtensionInstaller", "ExtensionFactory", "BibliotecaDb",
        )
        val deudas = InventoryStorage.deudas()
        val regresiones = deudas.filter { s -> arreglados.any { s.archivo.contains(it) } }
        assertTrue(
            "estos sitios se migraron fuera del almacenamiento interno y no pueden volver a la " +
                "lista de deudas: ${regresiones.map { it.archivo }}",
            regresiones.isEmpty(),
        )
    }

    @Test
    fun los_tokens_siguen_siendo_la_unica_excepcion_y_van_cifrados() {
        val tokens = InventoryStorage.sitios.filter {
            it.verdicto == InventoryStorage.Verdict.TOKEN_CIFRADO
        }
        assertTrue(
            "tiene que haber exactamente un sitio de tokens, TrackerCredentials",
            tokens.size == 1 && tokens[0].archivo.contains("TrackerCredentials"),
        )
        val c = codigo(File(base, "modules/houri/tracker/TrackerCredentials.kt"))
        assertTrue(
            "los tokens van en EncryptedSharedPreferences; sin cifrado no se guardan",
            c.contains("EncryptedSharedPreferences"),
        )
    }
}
