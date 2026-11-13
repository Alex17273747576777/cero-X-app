package com.ceroX.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Las pruebas corren en Android 16 (API 36), no en lo que Robolectric elija.
 *
 * Por qué existe: durante meses compileSdk y targetSdk fueron 36 mientras las
 * 18 clases con `@Config` fijaban sdk 34. El build compilaba contra Android 16
 * y la suite probaba contra Android 14, así que nada de lo que decía la suite
 * describía el aparato real. Nadie lo notó porque las pruebas pasaban en verde.
 *
 * Lo medido el 2026-11-13 con Robolectric 4.16.1 en este entorno: arrancan los
 * niveles 34, 35 y 36; el 37 falla con «API level 37 is not available»
 * (UnknownSdk.java:45) aunque el SDK tenga platforms/android-37.2-beta3
 * instalado. Tener la plataforma del SDK no equivale a que Robolectric la
 * pueda emular: la plataforma sirve para compilar, el jar android-all
 * instrumentado es otra cosa y para API 37 todavía no hay soporte estable.
 */
@RunWith(RobolectricTestRunner::class)
class NivelDeSdkDePruebasTest {

    /** API de Android 16, el nivel contra el que compila y se prueba el proyecto. */
    private val nivelEsperado = 36

    private val raizProyecto: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "android/app/src/test").isDirectory }
    }

    private val raizPruebas: File get() = File(raizProyecto, "android/app/src/test")

    @Test
    fun `el nivel en tiempo de ejecucion es Android 16`() {
        assertEquals(
            "Robolectric está emulando otro Android: mira src/test/resources/robolectric.properties",
            nivelEsperado,
            android.os.Build.VERSION.SDK_INT,
        )
        assertEquals("16", android.os.Build.VERSION.RELEASE)
    }

    @Test
    fun `robolectric properties fija el nivel por defecto`() {
        val archivo = File(raizPruebas, "resources/robolectric.properties")
        assertTrue("Falta ${archivo.path}; sin él cada clase nueva hereda un nivel arbitrario", archivo.isFile)
        val declarado = archivo.readLines()
            .map { it.substringBefore('#').trim() }
            .firstOrNull { it.startsWith("sdk=") }
            ?.substringAfter('=')
            ?.trim()
        assertEquals("El nivel por defecto no es $nivelEsperado", "$nivelEsperado", declarado)
    }

    @Test
    fun `ninguna clase ancla un nivel inferior al del proyecto`() {
        // Se buscan niveles escritos, no esta expresión: la regex no casa consigo
        // misma porque tras el corchete viene \d y no un dígito.
        val anclajes = Regex("""sdk\s*=\s*\[(\d+)\]""")
        val rezagados = mutableListOf<String>()
        raizPruebas.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { archivo ->
                archivo.readLines().forEachIndexed { indice, linea ->
                    anclajes.findAll(linea).forEach { coincidencia ->
                        val nivel = coincidencia.groupValues[1].toInt()
                        if (nivel < nivelEsperado) {
                            rezagados += "${archivo.relativeTo(raizPruebas)}:${indice + 1} sdk=[$nivel]"
                        }
                    }
                }
            }
        assertTrue(
            "Pruebas ancladas en un Android anterior al del proyecto:\n" + rezagados.joinToString("\n"),
            rezagados.isEmpty(),
        )
    }
}
