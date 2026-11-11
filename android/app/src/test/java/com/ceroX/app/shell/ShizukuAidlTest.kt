package com.ceroX.app.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Por qué no hay .aidl de Shizuku copiados en el repo.
 *
 * Estaban: cuatro ficheros en src/main/aidl/moe/shizuku/server con el mismo
 * contrato que trae `dev.rikka.shizuku:aidl`, dependencia transitiva de
 * `dev.rikka.shizuku:api`. R8 se negó a minificar con la clase definida dos veces
 * y el APK no se pudo construir. Se compararon los códigos de transacción con
 * `javap` sobre el artefacto publicado (docs/SHIZUKU-AIDL.md) y el artefacto es
 * superconjunto, así que se quedó el oficial.
 *
 * Estas pruebas no hablan con Shizuku: sólo impiden que la duplicación vuelva.
 */
class ShizukuAidlTest {

    private fun delRepo(rel: String): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, rel) }
            .firstOrNull { it.exists() }

    @Test
    fun noHayAidlDeShizukuCopiadoAMano() {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "android/app/src/main/aidl/moe/shizuku") }
            .firstOrNull { it.exists() }
        assertTrue(
            "vuelven los .aidl copiados: duplican dev.rikka.shizuku:aidl y R8 falla",
            dir == null,
        )
    }

    @Test
    fun laAppDependeDelArtefactoQueTraeEsasInterfaces() {
        val g = delRepo("android/app/build.gradle")
        assertTrue("falta build.gradle", g != null)
        val t = g!!.readText()
        assertTrue("falta dev.rikka.shizuku:api", t.contains("dev.rikka.shizuku:api"))
        assertFalse(
            "excluir el artefacto aidl dejaría a api sin las interfaces contra las que compiló",
            t.contains("module: 'aidl'") || t.contains("module: \"aidl\""),
        )
    }

    @Test
    fun elShellUsaElBinderPorAidlYNadaInventado() {
        val f = delRepo("android/app/src/main/java/com/ceroX/app/shell/ShizukuShell.kt")
        assertTrue("falta ShizukuShell.kt", f != null)
        val t = f!!.readText()
        assertTrue("importa la interfaz del servicio", t.contains("import moe.shizuku.server.IShizukuService"))
        assertTrue("y la del proceso remoto", t.contains("import moe.shizuku.server.IRemoteProcess"))
        assertTrue("convierte el binder con asInterface", t.contains("IShizukuService.Stub.asInterface"))
        assertFalse(
            "un número de transacción escrito a mano sería inventárselo",
            t.contains("transact("),
        )
    }
}
