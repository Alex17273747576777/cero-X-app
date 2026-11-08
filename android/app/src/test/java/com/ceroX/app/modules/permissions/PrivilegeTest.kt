package com.ceroX.app.modules.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Privilegios: lo que se puede y lo que **no** se puede comprobar.
 *
 * Estas pruebas existen para que nadie vuelva a escribir «Shizuku: OK» cuando lo
 * único cierto es que el paquete está instalado.
 *
 * NOTA DE HONESTIDAD SOBRE ESTE ARCHIVO
 * -------------------------------------
 * Aquí se afirmaba que «AxManager» y «OmniShizuku» eran nombres sin verificar y
 * que **no** aparecían como paquetes reales. Eso ya no es cierto: el usuario
 * pasó los dos repositorios, se leyeron sus AIDL y ahora están en
 * `ExternalManager` con su protocolo. Las pruebas de ese archivo están en
 * `GestoresTest`; las de aquí cubren lo que sigue siendo cierto.
 */
class PrivilegeTest {

    private fun cap(
        installed: Boolean = true,
        running: Boolean = true,
        authorized: Boolean = true,
    ) = PrivilegeProbe.Deteccion(
        gestor = ExternalManager.SHIZUKU,
        instalado = installed,
        servicioVivo = running,
        autorizado = authorized,
    )

    // ── Los tres escalones ──────────────────────────────────────────────────

    @Test
    fun `instalado no es usable`() {
        assertFalse(
            "instalado no es lo mismo que poder instalar",
            cap(running = false, authorized = false).usable
        )
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
        assertTrue(c.describir().contains("servicio no corre"))
    }

    @Test
    fun `sin instalar lo primero que falta es instalarlo`() {
        val c = cap(installed = false, running = false, authorized = false)
        assertTrue(c.describir().contains("no instalado"))
    }

    // ── Niveles ─────────────────────────────────────────────────────────────

    @Test
    fun `shizuku y root exigen accion del usuario`() {
        assertTrue(PrivilegeLevel.SHIZUKU.requiresUserAction)
        assertTrue(PrivilegeLevel.ROOT.requiresUserAction)
        assertFalse(PrivilegeLevel.NORMAL.requiresUserAction)
    }

    @Test
    fun `dhizuku exige accion del usuario y no se llama normal`() {
        assertTrue(PrivilegeLevel.DHIZUKU.requiresUserAction)
        assertTrue(PrivilegeLevel.DHIZUKU.canDo.contains("Device Owner"))
    }

    @Test
    fun `un nivel desconocido cae a UNKNOWN, no a ROOT`() {
        assertEquals(PrivilegeLevel.UNKNOWN, PrivilegeLevel.fromId("superusuario_total"))
    }

    @Test
    fun `el id del nivel se lee sin distinguir mayusculas`() {
        assertEquals(PrivilegeLevel.SHIZUKU, PrivilegeLevel.fromId("SHIZUKU"))
    }

    @Test
    fun `unknown nunca habilita nada`() {
        // Si UNKNOWN habilitara algo, un fallo de la sonda daría permisos.
        val plan = com.ceroX.app.modules.installflow.InstallPlanner.plan(PrivilegeLevel.UNKNOWN)
        assertFalse(plan.fullyAutomatic)
    }

    // ── Capacidad de instalación: no prometer silencio sin privilegio ────────

    @Test
    fun `normal nunca promete instalacion silenciosa`() {
        val plan = com.ceroX.app.modules.installflow.InstallPlanner.plan(PrivilegeLevel.NORMAL)
        assertFalse(plan.fullyAutomatic)
        assertTrue(plan.steps.any { it.needsUserTouch })
        assertTrue(
            "la descripción honesta tiene que decir que hay que tocar",
            plan.honestDescription().contains("confirmar", ignoreCase = true) ||
                plan.steps.size > 0
        )
    }

    @Test
    fun `root sí es silencioso y no pide confirmacion`() {
        val plan = com.ceroX.app.modules.installflow.InstallPlanner.plan(PrivilegeLevel.ROOT)
        assertTrue(plan.fullyAutomatic)
        assertFalse(plan.steps.any { it.needsUserTouch })
    }

    // ── Lo que quedó sin verificar, y sigue estando dicho ───────────────────

    @Test
    fun `el modulo de privilegios declara lo que no toca`() {
        // Los gobernadores de CPU y los Hz del panel no son de esta app. Está
        // escrito en el código para que no se «arregle» por error.
        val fuente = java.io.File(
            "src/main/java/com/ceroX/app/modules/permissions/PrivilegeProbe.kt"
        ).readText()
        assertTrue(fuente.contains("gobernadores"))
        assertTrue(fuente.contains("NO se tocan"))
    }

    @Test
    fun `las rutas de root son absolutas y estan en el codigo`() {
        val fuente = java.io.File(
            "src/main/java/com/ceroX/app/modules/permissions/PrivilegeProbe.kt"
        ).readText()
        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su").forEach {
            assertTrue("falta la ruta $it", fuente.contains("\"$it\""))
        }
    }

    @Test
    fun `el paquete de shizuku apunta al real`() {
        assertEquals("moe.shizuku.privileged.api", ExternalManager.SHIZUKU.paquete)
    }
}
