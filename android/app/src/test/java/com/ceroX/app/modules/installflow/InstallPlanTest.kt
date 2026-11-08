package com.ceroX.app.modules.installflow

import com.ceroX.app.modules.permissions.PrivilegeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Planes de instalación: que la app no anuncie "automático" cuando hay que pulsar
 * un botón.
 */
class InstallPlanTest {

    // ── Sin privilegio, no hay silencio. Punto. ──────────────────────────────

    @Test
    fun `sin privilegio el plan nunca es silencioso`() {
        listOf(PrivilegeLevel.NORMAL, PrivilegeLevel.UNKNOWN).forEach { nivel ->
            val p = InstallPlanner.plan(nivel)
            assertFalse("$nivel no debería dar silencioso", p.mode == InstallMode.SILENT)
        }
    }

    @Test
    fun `sin privilegio el plan NO es totalmente automatico`() {
        val p = InstallPlanner.plan(PrivilegeLevel.NORMAL)
        assertFalse(
            "Android exige confirmación: decir 'automático' aquí es la mentira clásica",
            p.fullyAutomatic,
        )
        assertEquals(1, p.steps.count { it.needsUserTouch })
    }

    @Test
    fun `el plan sin privilegio lo dice en palabras claras`() {
        val p = InstallPlanner.plan(PrivilegeLevel.NORMAL)
        assertTrue(p.honestDescription().contains("confirmación"))
        assertFalse(p.honestDescription().contains("sin tocar la pantalla"))
    }

    @Test
    fun `el plan sin privilegio avisa de la opcion de origenes desconocidos`() {
        val p = InstallPlanner.plan(PrivilegeLevel.NORMAL)
        assertTrue(p.warnings.any { it.contains("SourceUnknownSources") })
    }

    // ── Con privilegio, sí ───────────────────────────────────────────────────

    @Test
    fun `con root el plan es silencioso y automatico`() {
        val p = InstallPlanner.plan(PrivilegeLevel.ROOT)
        assertEquals(InstallMode.SILENT, p.mode)
        assertEquals(InstallMethod.ROOT_PM, p.method)
        assertTrue(p.fullyAutomatic)
        assertTrue(p.honestDescription().contains("sin tocar la pantalla"))
    }

    @Test
    fun `con shizuku tambien es silencioso, y avisa de que puede caerse`() {
        val p = InstallPlanner.plan(PrivilegeLevel.SHIZUKU)
        assertEquals(InstallMode.SILENT, p.mode)
        assertTrue(p.fullyAutomatic)
        assertTrue(p.warnings.any { it.contains("Shizuku se detiene") })
    }

    @Test
    fun `el plan de root avisa de que la app no escala privilegios`() {
        val p = InstallPlanner.plan(PrivilegeLevel.ROOT)
        assertTrue(p.warnings.any { it.contains("no lo pide ni lo escala") })
    }

    // ── Verificación antes de instalar: en los tres planes ───────────────────

    @Test
    fun `todo plan verifica el APK antes de instalarlo`() {
        listOf(PrivilegeLevel.NORMAL, PrivilegeLevel.SHIZUKU, PrivilegeLevel.ROOT).forEach { n ->
            val p = InstallPlanner.plan(n)
            assertTrue(
                "$n no verifica el APK: se estaría instalando a ciegas",
                p.steps.first().description.contains("sha256"),
            )
        }
    }

    @Test
    fun `todo plan confirma al final que el paquete quedo instalado`() {
        listOf(PrivilegeLevel.NORMAL, PrivilegeLevel.SHIZUKU, PrivilegeLevel.ROOT).forEach { n ->
            val p = InstallPlanner.plan(n)
            assertTrue(
                "$n no confirma el resultado: 'éxito' sin comprobar es una mentira",
                p.steps.last().description.contains("quedó instalado"),
            )
        }
    }

    @Test
    fun `los pasos van numerados sin huecos`() {
        listOf(PrivilegeLevel.NORMAL, PrivilegeLevel.SHIZUKU, PrivilegeLevel.ROOT).forEach { n ->
            val p = InstallPlanner.plan(n)
            assertEquals((1..p.steps.size).toList(), p.steps.map { it.order })
        }
    }

    // ── Modos: primer plano, segundo plano, silencioso ───────────────────────

    @Test
    fun `el segundo plano sin privilegio sigue pidiendo confirmacion`() {
        val p = InstallPlanner.plan(PrivilegeLevel.NORMAL, wantBackground = true)
        assertEquals(InstallMode.BACKGROUND, p.mode)
        assertFalse("segundo plano no es silencioso", p.fullyAutomatic)
    }

    @Test
    fun `sin querer segundo plano sigue siendo primer plano`() {
        val p = InstallPlanner.plan(PrivilegeLevel.NORMAL, wantBackground = false)
        assertEquals(InstallMode.FOREGROUND, p.mode)
    }

    @Test
    fun `solo los metodos con privilegio son silenciosos`() {
        assertTrue(InstallMethod.ROOT_PM.isSilent)
        assertTrue(InstallMethod.SHIZUKU.isSilent)
        assertFalse(
            "PackageInstaller muestra siempre la pantalla del sistema",
            InstallMethod.PACKAGE_INSTALLER.isSilent,
        )
    }

    // ── Qué se puede ofrecer en la UI ────────────────────────────────────────

    @Test
    fun `el instalador del sistema se puede usar siempre`() {
        PrivilegeLevel.entries.forEach { n ->
            assertTrue("$n debería poder usar PackageInstaller", InstallPlanner.canUse(InstallMethod.PACKAGE_INSTALLER, n))
        }
    }

    @Test
    fun `root puede usar shizuku y shizuku no puede usar root`() {
        assertTrue(InstallPlanner.canUse(InstallMethod.SHIZUKU, PrivilegeLevel.ROOT))
        assertFalse(InstallPlanner.canUse(InstallMethod.ROOT_PM, PrivilegeLevel.SHIZUKU))
    }

    @Test
    fun `un nivel desconocido no habilita nada privilegiado`() {
        assertFalse(InstallPlanner.canUse(InstallMethod.ROOT_PM, PrivilegeLevel.UNKNOWN))
        assertFalse(InstallPlanner.canUse(InstallMethod.SHIZUKU, PrivilegeLevel.UNKNOWN))
        // Pero sí el instalador normal, que no necesita privilegio.
        assertTrue(InstallPlanner.canUse(InstallMethod.PACKAGE_INSTALLER, PrivilegeLevel.UNKNOWN))
    }
}
