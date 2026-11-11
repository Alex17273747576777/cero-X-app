package com.ceroX.app.modules.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Que el repositorio no se contradiga sobre su propia licencia.
 *
 * `LicenseTest` comprueba las REGLAS (qué obliga cada licencia). Estas comprueban
 * los HECHOS del repo: el texto que hay, el que viaja en el APK, y lo que dicen el
 * README, el NOTICE y el módulo que informa al usuario. Antes había cuatro sitios
 * afirmando cosas distintas: LICENSE decía Apache, el resumen decía «Apache con
 * excepción GPL para Namida», el módulo de licencia informaba de cinco modelos que
 * el build ya no empaqueta, y el README aseguraba que cero-X «es Apache-2.0 y así
 * se queda».
 */
class LicenciaRepoTest {

    private fun delRepo(rel: String): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, rel) }
            .firstOrNull { it.exists() }

    private fun leer(rel: String): String {
        val f = delRepo(rel)
        assertTrue("falta " + rel, f != null)
        return f!!.readText()
    }

    @Test
    fun elLicenseDelRepoEsLaGplVersion3() {
        val t = leer("LICENSE")
        assertTrue("no es la GPL", t.contains("GNU GENERAL PUBLIC LICENSE"))
        assertTrue("y no es la versión 3", t.contains("Version 3, 29 June 2007"))
        assertEquals("el texto canónico son 35149 caracteres", 35149, t.length)
    }

    @Test
    fun elApkLlevaElMismoTextoQueElRepo() {
        // El build copia LICENSE y NOTICE a src/main/assets, y falla si faltan.
        // Las copias están en git: si se desincronizan, el APK repartiría una
        // licencia distinta de la que dice el repositorio.
        assertEquals(leer("LICENSE"), leer("android/app/src/main/assets/LICENSE"))
        assertEquals(leer("NOTICE"), leer("android/app/src/main/assets/NOTICE"))
    }

    @Test
    fun elModuloDeLicenciaInformaDeGpl3() {
        val m = leer("android/app/src/main/java/com/ceroX/app/modules/license/LicenseModule.kt")
        assertTrue("el módulo sigue diciendo Apache", m.contains("license = LicenseId.GPL3"))
        assertFalse("queda un APACHE2 como licencia del proyecto", m.contains("license = LicenseId.APACHE2"))
        assertTrue("y declara que no viaja ningún modelo", m.contains("emptyList()"))
    }

    @Test
    fun elReadmeYElNoticeCuentanElRelicenciamiento() {
        val r = leer("README.md")
        val seccion = r.substring(r.indexOf("## Licencia"))
        assertTrue("el README no dice GPL-3.0 en su sección de licencia", seccion.contains("GPL-3.0"))
        assertFalse("y sigue prometiendo que se queda en Apache", seccion.contains("así se queda"))
        val n = leer("NOTICE")
        assertTrue("el NOTICE no menciona la GPL-3.0", n.contains("GPL-3.0"))
        assertTrue("ni que no viaja ningún modelo", n.contains("cero archivos .onnx"))
        assertTrue("ni por qué Namida queda fuera", n.contains("EULA propietaria"))
    }
}
