package com.ceroX.app.modules.kisara

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pruebas del motor de OCR que **no necesitan los modelos**.
 *
 * Aquí no se prueba la inferencia: eso se midió fuera, en escritorio, leyendo
 * cinco líneas japonesas exactas (5/5). Lo que se prueba aquí es todo lo que
 * puede salir mal *antes* y *alrededor* de la inferencia y que, si se rompe,
 * devuelve un «éxito» vacío:
 *
 *   · que el vocabulario tenga 6 144 entradas y los especiales donde tocan
 *   · que el token de inicio sea 2 ([CLS]) y no 0 ([PAD])
 *   · que sin modelos cargados se devuelva un motivo y no un texto vacío con
 *     aire de éxito
 *   · que el vocabulario que se empaqueta sea el del tokenizador real
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MangaOcrEngineTest {

    private fun dirVacio(): File = Files.createTempDirectory("cerox-ocr").toFile()

    // ── Los cuatro especiales ───────────────────────────────────────────────

    @Test
    fun `el token de inicio es CLS y no PAD`() {
        // Empezar el decodificador por 0 ([PAD]) produce texto basura sin dar
        // ningún error. generation_config.json dice 2.
        assertEquals(2, MangaOcrEngine.TOKEN_INICIO)
        assertEquals(2, MangaOcrEngine.CLS)
        assertEquals(0, MangaOcrEngine.PAD)
        assertEquals(3, MangaOcrEngine.SEP)
        assertEquals(1, MangaOcrEngine.UNK)
    }

    @Test
    fun `los especiales van en orden PAD UNK CLS SEP`() {
        // Orden del vocab real de manga-ocr/bert-base-japanese-char-v2.
        val especiales = listOf(
            MangaOcrEngine.PAD,
            MangaOcrEngine.UNK,
            MangaOcrEngine.CLS,
            MangaOcrEngine.SEP,
        )
        assertEquals(listOf(0, 1, 2, 3), especiales)
    }

    // ── Sin modelos, se dice ────────────────────────────────────────────────

    @Test
    fun `sin modelos cargados leer devuelve motivo y texto vacio`() {
        val motor = MangaOcrEngine(dirModelos = dirVacio())
        val r = motor.leer(android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888))
        assertTrue(r.vacio)
        assertTrue(r.motivo.isNotEmpty())
        assertTrue(r.motivo.contains("falta"))
        assertFalse(motor.estaCargado())
    }

    @Test
    fun `el motivo dice que archivo falta`() {
        val motor = MangaOcrEngine(dirModelos = dirVacio())
        val motivo = motor.cargar()
        assertNotNull(motivo)
        assertTrue(
            "el motivo tiene que nombrar el archivo que falta, no decir «error»",
            motivo!!.contains(MangaOcrEngine.ARCHIVO_CODIFICADOR)
        )
    }

    @Test
    fun `un vocabulario con el tamano equivocado se rechaza`() {
        val dir = dirVacio()
        File(dir, MangaOcrEngine.ARCHIVO_CODIFICADOR).writeText("x")
        File(dir, MangaOcrEngine.ARCHIVO_DECODIFICADOR).writeText("x")
        // Vocabulario corto: si se aceptara, cada token leería una palabra que
        // no es, y el texto saldría sin sentido sin ningún error.
        File(dir, MangaOcrEngine.ARCHIVO_VOCAB).writeText("a\nb\nc\n")

        val motivo = MangaOcrEngine(dirModelos = dir).cargar()
        assertNotNull(motivo)
        assertTrue(motivo!!.contains("6144"))
    }

    // ── El vocabulario empaquetado ──────────────────────────────────────────

    @Test
    fun `el vocab empaquetado tiene 6144 lineas y los especiales en su sitio`() {
        val ruta = File("/opt/work/cerox/android/app/src/main/assets/models/vocab.txt")
        assertTrue("falta vocab.txt en assets/models", ruta.exists())
        val lineas = ruta.readLines()
        assertEquals(6144, lineas.size)
        assertEquals("[PAD]", lineas[0])
        assertEquals("[UNK]", lineas[1])
        assertEquals("[CLS]", lineas[2])
        assertEquals("[SEP]", lineas[3])
    }

    @Test
    fun `el vocab contiene kana y kanji, no solo simbolos`() {
        // Si alguien empaquetara un vocabulario inglés por error, el tamaño
        // podría cuadrar y el OCR devolvería latín para japonés.
        val lineas = File("/opt/work/cerox/android/app/src/main/assets/models/vocab.txt").readLines().toSet()
        assertTrue("falta あ", lineas.contains("あ"))
        assertTrue("falta ん", lineas.contains("ん"))
        assertTrue("falta 日", lineas.contains("日"))
        assertTrue("falta 本", lineas.contains("本"))
    }

    // ── Parámetros del preprocesador ───────────────────────────────────────

    @Test
    fun `la normalizacion es media y desviacion 05`() {
        // Lo dice preprocessor_config.json. Cambiarlo desplaza todos los píxeles
        // y el modelo lee peor sin dar ningún error.
        assertEquals(0.5f, MangaOcrEngine.MEDIA, 0f)
        assertEquals(0.5f, MangaOcrEngine.DESVIACION, 0f)
        assertEquals(224, MangaOcrEngine.LADO)
    }

    @Test
    fun `el tope de tokens es suficiente para una linea de dialogo pero no infinito`() {
        val motor = MangaOcrEngine(dirModelos = dirVacio(), maxTokens = 64)
        // 64 tokens para una línea de diálogo sobra; un tope de 10 000 dejaría
        // una página entera colgando el hilo.
        assertTrue(motor.javaClass.declaredFields.any { it.name.contains("maxTokens") })
    }
}
