package com.ceroX.app.modules.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La regla publicada: **un banco de pruebas se declara con medidas**.
 *
 * Lo que impide: que se escriba "VM instalada" o "probado en emulador" en un
 * documento o en un flujo sin que haya una medida detrás. Esta prueba lee el
 * documento y los flujos, no se fía de lo que diga el commit.
 */
class BancosDeclaradosTest {

    private val raiz: File = run {
        var d = File(".").absoluteFile
        while (!File(d, "src/main/java").isDirectory && d.parentFile != null) {
            d = d.parentFile
        }
        require(File(d, "src/main/java").isDirectory) { "no se encontró src/main/java" }
        d
    }

    private fun enRepo(relativa: String): File {
        var d: File? = raiz
        while (d != null) {
            val f = File(d, relativa)
            if (f.isFile) return f
            d = d.parentFile
        }
        return File(raiz, relativa)
    }

    /** Como `enRepo` pero para carpetas: `isFile` nunca encuentra un directorio. */
    private fun carpetaEnRepo(relativa: String): File {
        var d: File? = raiz
        while (d != null) {
            val f = File(d, relativa)
            if (f.isDirectory) return f
            d = d.parentFile
        }
        return File(raiz, relativa)
    }

    private val doc: String get() = enRepo("docs/BANCOS.md").readText()

    @Test
    fun los_tres_bancos_reales_estan_escritos() {
        val d = doc
        assertTrue("falta el runner de CI", d.contains("GitHub Actions"))
        assertTrue("falta la JVM sin aparato", d.contains("JVM"))
        assertTrue("falta el teléfono", d.contains("V60"))
    }

    @Test
    fun el_documento_lleva_las_medidas_no_la_intencion() {
        val d = doc
        for (medida in listOf("/dev/kvm", "vmx", "MemTotal", "1982")) {
            assertTrue("falta la medida $medida en docs/BANCOS.md", d.contains(medida))
        }
        assertTrue(
            "el documento no dice por qué no hay emulador",
            d.contains("no arranca") || d.contains("imposible"),
        )
    }

    @Test
    fun la_pantalla_de_estado_dice_en_que_banco_esta() {
        val actividad = File(
            raiz, "src/main/java/com/ceroX/app/ui/estado/EstadoSistemaActivity.kt",
        ).readText()
        assertTrue(
            "la pantalla no pinta la sección de bancos",
            actividad.contains(Bancos.SECCION),
        )
        assertTrue(
            "la pantalla no llama al que mide",
            actividad.contains("BancosAndroid.lineas"),
        )
    }

    @Test
    fun ningun_flujo_finge_arrancar_un_emulador() {
        val flujos = carpetaEnRepo(".github/workflows").listFiles()?.filter { it.extension == "yml" }
        assertTrue("no encuentro los flujos", !flujos.isNullOrEmpty())
        val queFingen = flujos!!.filter { f ->
            val t = f.readText()
            t.contains("android-emulator-runner") || t.contains("emulator -avd")
        }.map { it.name }
        assertTrue("flujos que prometen un emulador: $queFingen", queFingen.isEmpty())
        val apk = flujos.first { it.name == "apk.yml" }.readText()
        assertTrue("apk.yml ya no dice que no hay emulador", apk.contains("no hay emulador"))
    }
}
