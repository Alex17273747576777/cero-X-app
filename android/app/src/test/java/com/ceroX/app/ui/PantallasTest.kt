package com.ceroX.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Que ninguna pantalla vuelva a pedir su módulo a un registro vacío.
 *
 * Seis pantallas se cerraban al abrirlas porque pedían su módulo a un motor que no
 * registraba nada y hacían un cast no nulo sobre el resultado. «Diseño» está en la
 * barra inferior, o sea que la app se caía al tocarla. Estas pruebas leen el fuente
 * y vigilan las tres cosas que lo hicieron posible: que el motor siga existiendo,
 * que alguna pantalla lo use, y que vuelva un cast no nulo sobre una búsqueda de
 * módulo.
 */
class PantallasTest {

    private val main: File by lazy {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "android/app/src/main") }
            .firstOrNull { it.isDirectory }
        assertTrue("no encuentro android/app/src/main desde " + File("").absolutePath, f != null)
        f!!
    }

    private val src: File get() = File(main, "java/com/ceroX/app")

    private fun leer(rel: String): String {
        val f = File(src, rel)
        assertTrue("falta $rel", f.exists())
        return f.readText()
    }

    private fun todosLosKotlin(): List<File> =
        src.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun elMotorLegacyEstaBorradoYNadieLoNombra() {
        assertFalse("el motor legacy sigue ahí", File(src, "core/engine/CoreEngine.kt").exists())
        // Nombrarlo para explicar que está borrado es justo lo que hace Pantallas.kt;
        // usarlo es lo que no puede volver a pasar.
        val sinExplicar = todosLosKotlin()
            .map { it to it.readText() }
            .filter { par -> par.second.contains("CoreEngine") || par.second.contains("coreEngine") }
            .filter { par -> !par.second.contains("está borrado") }
            .map { par -> par.first.relativeTo(src).path.replace(File.separatorChar, '/') }
        assertEquals("usan el motor legacy sin explicar que está borrado", emptyList<String>(), sinExplicar)
    }

    @Test
    fun lasSeisPantallasPidenSuModuloAlGestor() {
        val pantallas = listOf(
            "modules/theme/ThemeSwitcherFragment.kt" to "theme-engine",
            "modules/mode/ModeSwitcherFragment.kt" to "mode-manager",
            "modules/kisara/KisaraUiFragment.kt" to "kisara-reader",
            "modules/catalog/CatalogUIFragment.kt" to "app-catalog",
            "modules/booru/BooruUIFragment.kt" to "booru-viewer",
            "ui/StressTestActivity.kt" to "deep-reverse-engineering",
        )
        for (par in pantallas) {
            val t = leer(par.first)
            assertTrue(par.first + " no pide su módulo por Pantallas", t.contains("Pantallas.modulo"))
            assertTrue(par.first + " ya no pide " + par.second, t.contains(par.second))
            assertFalse(par.first + " sigue buscando en un registro", t.contains("registry.getInstance"))
        }
    }

    @Test
    fun losCatalogosExistenDesdeLaConstruccion() {
        // initialize() corre en el hilo de arranque; una pantalla que se abre antes
        // se encontraba la lista vacía. Los datos fijos se registran al construirse,
        // y el registro es idempotente porque initialize() lo vuelve a llamar.
        for (rel in listOf(
            "modules/theme/ThemeEngineModule.kt",
            "modules/mode/ModeManagerModule.kt",
            "modules/catalog/CatalogModule.kt",
            "modules/booru/BooruModule.kt",
        )) {
            val t = leer(rel)
            assertTrue(rel + " no registra en init", t.contains("init {"))
            assertTrue(rel + " no se protege de registrar dos veces", t.contains("isNotEmpty()) return"))
        }
        assertFalse("el !! de getCurrentTheme sigue ahí",
            leer("modules/theme/ThemeEngineModule.kt").contains("currentThemeId }!!"))
        assertFalse("y el de getCurrentMode también",
            leer("modules/mode/ModeManagerModule.kt").contains("currentModeId }!!"))
    }

    @Test
    fun laPaletaElegidaSeAplicaALaInterfaz() {
        assertTrue(leer("ui/theme/CeroXTheme.kt").contains("colorSchemeOverride"))
        val m = leer("ui/main/MainActivity.kt")
        assertTrue("MainActivity no implementa el repintado", m.contains("AplicaDiseno"))
        assertTrue("y no convierte la paleta", m.contains("UIThemeManager"))
        assertTrue("ni guarda el esquema elegido", m.contains("paleta.value"))
        val t = leer("modules/theme/ThemeSwitcherFragment.kt")
        assertTrue("la pantalla de Diseño no avisa a la actividad", t.contains("AplicaDiseno"))
        assertTrue("y no se repinta al inicializarse el módulo", t.contains("private fun pintar()"))
    }

    @Test
    fun elPanelDeModulosLeeDelGestorReal() {
        val d = leer("ui/dashboard/ModuleDashboardFragment.kt")
        assertFalse("sigue con el healthCheckAll del motor vacío", d.contains("healthCheckAll"))
        assertTrue(leer("ui/dashboard/DashboardViewModel.kt").contains("getAllHealth"))
        val c = leer("core/CrashHandler.kt")
        assertTrue("los crashes se publican en un bus que nadie escucha",
            c.contains("lifecycleManager.getEventBus()"))
        assertTrue(leer("core/ConnectivityReceiver.kt").contains("lifecycleManager.getEventBus()"))
    }
}
