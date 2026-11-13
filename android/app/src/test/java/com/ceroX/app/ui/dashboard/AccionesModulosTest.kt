package com.ceroX.app.ui.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ceroX.app.core.AlmacenExterno
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import com.ceroX.app.core.engine.ModuleLifecycleManager
import com.ceroX.app.core.engine.ModuleMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

class ModuloDashboardPrueba : ModuleInterface() {
    override val name = "modulo-dashboard-prueba"
    override val version = "1.0"
    override fun initialize(config: Map<String, Any>): Boolean { _initialized = true; return true }
    override fun start(): Boolean { if (!_initialized) return false; _started = true; return true }
    override fun stop(): Boolean { _started = false; return true }
    override fun healthCheck() = ModuleHealth(name, version, if (_started) "healthy" else "stopped")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccionesModulosTest {
    @Test fun dependencias_inicio_y_parada_son_reales() {
        AlmacenExterno.olvidar()
        val manager = ModuleLifecycleManager(ApplicationProvider.getApplicationContext<Context>())
        try {
            assertTrue(manager.registerModule("base", ModuloDashboardPrueba::class.java, ModuleMetadata("base", "1.0", emptyList())))
            assertTrue(manager.registerModule("hijo", ModuloDashboardPrueba::class.java, ModuleMetadata("hijo", "1.0", listOf("base"))))
            manager.initializeAll()
            manager.startAll()
            assertTrue(manager.isModuleStarted("base"))
            assertTrue(manager.isModuleStarted("hijo"))
            val bloqueada = manager.detenerEnSegundoPlano("base").get(10, TimeUnit.SECONDS)
            assertFalse(bloqueada.exito)
            assertTrue(bloqueada.mensaje.contains("hijo"))
            assertTrue(manager.isModuleStarted("base"))
            assertTrue(manager.detenerEnSegundoPlano("hijo").get(10, TimeUnit.SECONDS).exito)
            assertTrue(manager.detenerEnSegundoPlano("base").get(10, TimeUnit.SECONDS).exito)
            assertFalse(manager.isModuleStarted("base"))
            val iniciado = manager.iniciarYActivarEnSegundoPlano("base").get(10, TimeUnit.SECONDS)
            assertTrue(iniciado.mensaje, iniciado.exito)
            assertTrue(manager.isModuleStarted("base"))
            assertFalse(manager.iniciarYActivarEnSegundoPlano("inexistente").get(10, TimeUnit.SECONDS).exito)
            assertFalse(manager.detenerEnSegundoPlano("inexistente").get(10, TimeUnit.SECONDS).exito)
        } finally { manager.onDestroy() }
    }

    @Test fun filtro_y_acceso_no_son_decorativos() {
        val items = listOf(
            ModuleItem("web", "1.0", "healthy", "navegador Brave"),
            ModuleItem("audio", "2.0", "stopped", "ecualizador"),
        )
        assertEquals(listOf("web"), filtrarModulos(items, "brave").map { it.name })
        assertEquals(listOf("audio"), filtrarModulos(items, "STOPPED").map { it.name })
        assertEquals(2, filtrarModulos(items, " ").size)
        val fragmento = java.io.File("src/main/java/com/ceroX/app/ui/dashboard/ModuleDashboardFragment.kt").readText()
        assertTrue(fragmento.contains("setOnQueryTextListener"))
        assertTrue(fragmento.contains("iniciarYActivarEnSegundoPlano"))
        assertTrue(fragmento.contains("detenerEnSegundoPlano"))
        assertFalse(fragmento.contains("Log.i(TAG"))
        val host = java.io.File("src/main/java/com/ceroX/app/ui/ajustes/FragmentHostActivity.kt").readText()
        assertTrue(host.contains("MODULOS -> ModuleDashboardFragment()"))
    }
}
