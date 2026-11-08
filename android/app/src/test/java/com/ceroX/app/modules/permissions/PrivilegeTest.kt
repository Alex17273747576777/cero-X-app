package com.ceroX.app.modules.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Privilegios: lo que se puede y lo que **no** se puede comprobar.
 *
 * Estas pruebas existen para que nadie vuelva a escribir "Shizuku: OK" cuando lo
 * único cierto es que el paquete está instalado.
 */
class PrivilegeTest {

    private fun cap(
        installed: Boolean = true,
        running: Boolean = true,
        authorized: Boolean = true,
    ) = ManagerCapability(
        packageName = ExternalManagers.SHIZUKU_PKG,
        displayName = "Shizuku",
        installed = installed,
        serviceRunning = running,
        authorized = authorized,
    )

    @Test
    fun `instalado no es usable`() {
        assertFalse("instalado no es lo mismo que poder instalar", cap(running = false, authorized = false).usable)
    }

    @Test
    fun `corriendo sin autorizar tampoco es usable`() {
        assertFalse(cap(authorized = false).usable)
    }

    @Test
    fun `solo con los tres escalones es usable`() {
        assertTrue(cap().usable)
    }

    @Test
    fun `dice exactamente que falta`() {
        val c = cap(running = false, authorized = false)
        val faltan = c.missing()
        assertEquals(1, faltan.size)
        assertTrue(faltan[0].contains("servicio no corre"))
    }

    @Test
    fun `sin instalar lo primero que falta es instalarlo`() {
        val c = cap(installed = false, running = false, authorized = false)
        assertTrue(c.missing()[0].contains("no está instalado"))
    }

    // ── Niveles ──────────────────────────────────────────────────────────────

    @Test
    fun `solo shizuku y root exigen accion del usuario`() {
        assertTrue(PrivilegeLevel.SHIZUKU.requiresUserAction)
        assertTrue(PrivilegeLevel.ROOT.requiresUserAction)
        assertFalse(PrivilegeLevel.NORMAL.requiresUserAction)
    }

    @Test
    fun `un nivel desconocido cae a UNKNOWN, no a ROOT`() {
        assertEquals(PrivilegeLevel.UNKNOWN, PrivilegeLevel.fromId("superusuario_total"))
    }

    @Test
    fun `el id del nivel se lee sin distinguir mayusculas`() {
        assertEquals(PrivilegeLevel.SHIZUKU, PrivilegeLevel.fromId("SHIZUKU"))
    }

    // ── Capacidad de instalación: no prometer silencio sin privilegio ────────

    @Test
    fun `normal nunca promete instalacion silenciosa`() {
        val c = InstallCapability(
            silent = false, needsConfirmation = true,
            level = PrivilegeLevel.NORMAL, reason = "sin privilegio",
        )
        assertFalse(c.silent)
        assertTrue(c.describe().contains("confirmación"))
    }

    @Test
    fun `si es silenciosa no pide confirmacion`() {
        val c = InstallCapability(
            silent = true, needsConfirmation = false,
            level = PrivilegeLevel.ROOT, reason = "root",
        )
        assertTrue(c.describe().contains("silenciosa"))
        assertFalse(c.needsConfirmation)
    }

    // ── Nombres no verificados: declarados, no inventados ────────────────────

    @Test
    fun `los nombres sin verificar estan escritos y no fingen soporte`() {
        assertTrue(ExternalManagers.UNVERIFIED_NAMES.contains("AxManager"))
        // Y no aparecen como paquetes reales en el catalogo.
        val pkgs = ExternalManagers.PACKAGE_MANAGERS.map { it.packageName.lowercase() }
        assertFalse(pkgs.any { it.contains("axmanager") })
        assertFalse(pkgs.any { it.contains("omni") })
    }

    @Test
    fun `las rutas de root estan declaradas y son absolutas`() {
        ExternalManagers.ROOT_BINARIES.forEach { p ->
            assertTrue("ruta no absoluta: $p", p.startsWith("/"))
        }
        assertTrue(ExternalManagers.ROOT_BINARIES.contains("/system/bin/su"))
    }

    @Test
    fun `las variantes de shizuku apuntan al paquete real del proyecto`() {
        assertTrue(ExternalManagers.SHIZUKU_VARIANTS.contains("moe.shizuku.privileged.api"))
    }
}
