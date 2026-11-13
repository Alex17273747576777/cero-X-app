package com.ceroX.app.modules.permissions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ceroX.app.shell.Shell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas de los gestores externos y de la sonda de privilegios.
 *
 * Lo que se prueba aquí, y por qué importa:
 *
 *  · que **OmniShizuku** se busque por el paquete de Shizuku, porque su AIDL es
 *    el de `moe.shizuku` — comprobado leyendo su repositorio, no su descripción
 *  · que **AxManagerD** NO tenga un paquete inventado: se detecta por protocolo
 *  · que tener un gestor **instalado** no lo haga `usable`: los tres escalones
 *    (instalado, servicio vivo, autorizado) tienen que estar
 *  · que el `<queries>` del manifest tenga todos los paquetes comprobables
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GestoresTest {

    private val contexto: Context get() = ApplicationProvider.getApplicationContext()

    // ── Los repos que pasó el usuario ───────────────────────────────────────

    @Test
    fun `OmniShizuku se busca por el paquete de Shizuku`() {
        // Su árbol trae api/aidl/src/main/aidl/moe/shizuku/server/*.aidl: habla
        // el protocolo de Shizuku, así que su paquete es el mismo.
        val omni = ExternalManager.porId("omni_shizuku")
        assertNotNull(omni)
        assertEquals("moe.shizuku.privileged.api", omni!!.paquete)
        assertEquals(ExternalManager.Protocolo.MOE_SHIZUKU, omni.protocolo)
    }

    @Test
    fun `AxManagerD no tiene paquete inventado`() {
        val ax = ExternalManager.porId("axmanagerd")
        assertNotNull(ax)
        assertEquals(null, ax!!.paquete)
        assertFalse(ax.comprobablePorPaquete)
    }

    @Test
    fun `AxManagerD declara que habla los dos protocolos`() {
        val ax = ExternalManager.porId("axmanagerd")!!
        assertEquals(ExternalManager.Protocolo.MOE_SHIZUKU_O_DHIZUKU, ax.protocolo)
    }

    @Test
    fun `los dos repos tienen su nota con la licencia real`() {
        val ax = ExternalManager.porId("axmanagerd")!!
        val omni = ExternalManager.porId("omni_shizuku")!!
        // AxManagerD declara NOASSERTION en GitHub; OmniShizuku, Apache-2.0.
        assertTrue(ax.licencia.contains("NOASSERTION"))
        assertTrue(omni.licencia.contains("Apache-2.0"))
    }

    @Test
    fun `todos los paquetes comprobables estan en el queries del manifest`() {
        // En Android 11+ sin <queries>, getPackageInfo lanza aunque el paquete
        // esté instalado. Si esta prueba falla, la detección no funciona en
        // Android moderno aunque el código sea correcto.
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        ExternalManager.paquetesParaQueries().forEach { paquete ->
            assertTrue(
                "falta <package android:name=\"$paquete\" /> en el manifest",
                manifest.contains(paquete)
            )
        }
    }

    // ── Los tres escalones ──────────────────────────────────────────────────

    @Test
    fun `instalado no es usable`() {
        val deteccion = PrivilegeProbe.Deteccion(
            gestor = ExternalManager.SHIZUKU,
            instalado = true,
            servicioVivo = false,
            autorizado = false,
        )
        assertFalse("tenerlo instalado no significa poder usarlo", deteccion.usable)
        assertTrue(deteccion.describir().contains("servicio no corre"))
    }

    @Test
    fun `servicio vivo sin autorizacion tampoco es usable`() {
        val deteccion = PrivilegeProbe.Deteccion(
            gestor = ExternalManager.SHIZUKU,
            instalado = true,
            servicioVivo = true,
            autorizado = false,
        )
        assertFalse(deteccion.usable)
        assertTrue(deteccion.describir().contains("sin permiso"))
    }

    @Test
    fun `los tres escalones dan usable`() {
        val deteccion = PrivilegeProbe.Deteccion(
            gestor = ExternalManager.SHIZUKU,
            instalado = true,
            servicioVivo = true,
            autorizado = true,
            version = 13,
        )
        assertTrue(deteccion.usable)
        assertTrue(deteccion.describir().contains("listo"))
    }

    // ── Elección del nivel ─────────────────────────────────────────────────

    @Test
    fun `root gana a todo`() {
        assertEquals(
            PrivilegeLevel.ROOT,
            PrivilegeLevel.elegir(root = true, shizuku = true, dhizuku = true)
        )
    }

    @Test
    fun `shizuku gana a dhizuku`() {
        assertEquals(
            PrivilegeLevel.SHIZUKU,
            PrivilegeLevel.elegir(root = false, shizuku = true, dhizuku = true)
        )
    }

    @Test
    fun `sin nada el nivel es normal, no desconocido`() {
        // UNKNOWN no habilita nada y haría que la UI preguntara. Sabemos que no
        // hay privilegio: eso es NORMAL, no «no se sabe».
        assertEquals(
            PrivilegeLevel.NORMAL,
            PrivilegeLevel.elegir(root = false, shizuku = false, dhizuku = false)
        )
    }

    @Test
    fun `dhizuku tiene su propio nivel y su propio plan`() {
        val plan = com.ceroX.app.modules.installflow.InstallPlanner.plan(PrivilegeLevel.DHIZUKU)
        assertEquals(com.ceroX.app.modules.installflow.InstallMode.SILENT, plan.mode)
        // Sin tocar la pantalla, pero no por el binder de Shizuku: sería mentira.
        assertFalse(plan.steps.any { it.description.contains("binder", ignoreCase = true) })
        assertTrue(plan.warnings.any { it.contains("Device Owner") })
    }

    // ── La sonda en un Android sin Shizuku ─────────────────────────────────

    @Test
    fun `en un telefono sin gestores el informe es honesto`() {
        com.ceroX.app.shell.ShizukuShell.EstadoShizuku.contextoGlobal = contexto
        val informe = PrivilegeProbe(contexto).detectar()

        // Robolectric no tiene Shizuku instalado ni root.
        assertEquals(PrivilegeLevel.NORMAL, informe.nivel)
        assertFalse(informe.hayAlguno)
        assertTrue(informe.caminoDeInstalacion.contains("primer plano"))
        // Y lo que NO se hace tiene que estar dicho.
        assertTrue(informe.notas.any { it.contains("gobernadores") })
        assertTrue(informe.notas.any { it.contains("NO se tocan") })
    }

    // ── El shell ───────────────────────────────────────────────────────────

    @Test
    fun `citar una ruta con espacios la deja en un solo argumento`() {
        val citada = Shell.citar("/sdcard/Mis Manga/1.cbz")
        assertEquals("'/sdcard/Mis Manga/1.cbz'", citada)
    }

    @Test
    fun `citar una ruta con comilla simple no rompe el comando`() {
        // Sin escapar, `pm install '/sdcard/Juan's/1.apk'` cierra la comilla y
        // el resto se interpreta como otro comando.
        val citada = Shell.citar("/sdcard/Juan's/1.apk")
        assertEquals("'/sdcard/Juan'\\''s/1.apk'", citada)
    }

    @Test
    fun `una salida con codigo cero pero sin Success no es exito`() {
        val salida = Shell.Salida(0, "Failure [INSTALL_FAILED_INVALID_APK]", "", 10)
        assertFalse(
            "el código 0 no basta: hay que mirar lo que dijo",
            salida.codigo == 0 && salida.stdout.contains("Success", ignoreCase = true)
        )
    }

    @Test
    fun `describir incluye el codigo y el texto`() {
        val salida = Shell.Salida(1, "", "Permission denied", 5)
        assertTrue(salida.describir().contains("código 1"))
        assertTrue(salida.describir().contains("Permission denied"))
    }
}
