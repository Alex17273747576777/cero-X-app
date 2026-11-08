package com.ceroX.app.modules.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del traductor que corre dentro del APK.
 *
 * Lo que se prueba es lo que **sí** hace: variante, glosario del usuario, y no
 * partir palabras. Lo que no hace —traducir japonés— está declarado en el
 * código y esta prueba lo comprueba, para que nadie lo cambie por optimismo.
 */
class LocalTranslatorTest {

    private val dominicano = LocaleVariant(
        language = "es",
        region = "DO",
        displayName = "Español dominicano",
        vocabulary = mapOf(
            "autobús" to "guagua",
            "banana" to "guineo",
            "está bien" to "ta' bien",
            "móvil" to "celular",
            "ordenador" to "computadora",
        ),
        avoid = listOf("vosotros", "ordenador", "móvil", "vale", "guay"),
    )

    // ── El glosario del usuario manda ────────────────────────────────────────

    @Test
    fun `el glosario del usuario tiene prioridad sobre la variante`() {
        val r = LocalTranslator.traducir(
            texto = "El móvil está bien",
            variante = dominicano,
            glosario = mapOf("móvil" to "teléfono"),
        )
        // Si la variante se aplicara primero, saldría «celular» y la decisión
        // del usuario quedaría pisada.
        assertTrue(r.texto.contains("teléfono"))
        assertFalse(r.texto.contains("celular"))
    }

    @Test
    fun `se aplica el vocabulario de la variante`() {
        val r = LocalTranslator.traducir("Voy en autobús con una banana", dominicano)
        assertEquals("Voy en guagua con una guineo", r.texto)
        assertEquals(2, r.numCambios)
    }

    @Test
    fun `el termino mas largo gana`() {
        // Con «Luffy» y «Monkey D. Luffy» a la vez, aplicar el corto primero
        // partiría el largo en «Monkey D. Luffy» → «Monkey D. [Luffy]».
        val glosario = mapOf(
            "Luffy" to "Luffy",
            "Monkey D. Luffy" to "Monkey D. Luffy",
            "Mugiwara" to "Sombrero de Paja",
        )
        val r = LocalTranslator.traducir("Monkey D. Luffy y los Mugiwara", dominicano, glosario)
        assertEquals("Monkey D. Luffy y los Sombrero de Paja", r.texto)
        assertEquals(1, r.numCambios)  // solo Mugiwara cambió
    }

    // ── Límites de palabra ──────────────────────────────────────────────────

    @Test
    fun `no se sustituye dentro de otra palabra`() {
        // Este es el fallo clásico de los glosarios con String.replace: «os»
        // dentro de «vosotros».
        val r = LocalTranslator.reemplazarPalabra("vosotros os vais", "os", "ustedes")
        assertEquals("vosotros ustedes vais", r)
    }

    @Test
    fun `se conserva la mayuscula inicial`() {
        assertEquals("Guagua llena", LocalTranslator.reemplazarPalabra("Autobús llena", "autobús", "guagua"))
    }

    @Test
    fun `se conserva el texto todo en mayusculas`() {
        assertEquals("¡GUAGUA!", LocalTranslator.reemplazarPalabra("¡AUTOBÚS!", "autobús", "guagua"))
    }

    @Test
    fun `la sustitucion no distingue mayusculas al buscar`() {
        assertEquals("la guagua", LocalTranslator.reemplazarPalabra("La Autobús", "autobús", "guagua").lowercase())
    }

    // ── Lo que se detecta sin corregir ──────────────────────────────────────

    @Test
    fun `se detectan las formas de otra region`() {
        val encontradas = LocalTranslator.detectarAjenas("Vale, guay, lo haré en el ordenador", dominicano)
        assertEquals(listOf("ordenador", "vale", "guay"), encontradas)
    }

    @Test
    fun `un texto limpio no reporta nada`() {
        assertTrue(LocalTranslator.detectarAjenas("Eso ta' bien, mi hermano", dominicano).isEmpty())
    }

    @Test
    fun `detectar no modifica el texto`() {
        val original = "Vale, uso el ordenador"
        LocalTranslator.detectarAjenas(original, dominicano)
        assertEquals("Vale, uso el ordenador", original)
    }

    // ── Casos límite ───────────────────────────────────────────────────────

    @Test
    fun `un texto vacio no es un error`() {
        val r = LocalTranslator.traducir("   ", dominicano)
        assertEquals("   ", r.texto)
        assertFalse(r.huboCambios)
        assertTrue(r.motivo.contains("vacío"))
    }

    @Test
    fun `si no hay nada que cambiar se dice`() {
        val r = LocalTranslator.traducir("Eso ta' bien", dominicano)
        assertFalse(r.huboCambios)
        assertTrue(r.motivo.contains("nada que cambiar"))
    }

    @Test
    fun `un glosario vacio no rompe nada`() {
        val r = LocalTranslator.traducir("texto cualquiera", dominicano, emptyMap())
        assertEquals("texto cualquiera", r.texto)
    }

    // ── Transcripción completa ─────────────────────────────────────────────

    @Test
    fun `se traduce la transcripcion sin mover los tiempos`() {
        val original = Transcript(
            segments = listOf(
                TranscriptSegment(0, 1_000, "Voy en autobús", "ja", 0.9),
                TranscriptSegment(1_000, 2_500, "con una banana", "ja", 0.8),
            ),
            sourceLanguage = "ja",
            model = "manga-ocr",
        )
        val (traducida, resultados) = LocalTranslator.traducirTranscripcion(original, dominicano)

        assertEquals(2, resultados.size)
        assertEquals(original.segments.map { it.startMs }, traducida.segments.map { it.startMs })
        assertEquals(original.segments.map { it.endMs }, traducida.segments.map { it.endMs })
        assertEquals("Voy en guagua", traducida.segments[0].text)
        assertEquals("con una guineo", traducida.segments[1].text)
    }

    @Test
    fun `un segmento que falla no deja la transcripcion a medias`() {
        // Un segmento vacío no puede producir texto vacío: se conserva el
        // original, porque un subtítulo en blanco es peor que uno sin traducir.
        val original = Transcript(
            segments = listOf(
                TranscriptSegment(0, 500, "  ", "ja"),
                TranscriptSegment(500, 1_000, "banana", "ja"),
            ),
            sourceLanguage = "ja",
            model = "manga-ocr",
        )
        val (traducida, _) = LocalTranslator.traducirTranscripcion(original, dominicano)
        assertEquals("  ", traducida.segments[0].text)
    }

    // ── El límite declarado ────────────────────────────────────────────────

    @Test
    fun `el traductor local declara que no traduce de japones`() {
        // Esto parece una prueba tonta y no lo es: `LocalTranslator` no tiene
        // modelos, así que no puede traducir idioma. Si alguien le añade un
        // «traducir» que devuelva el texto sin más y lo llame traducción, aquí
        // no se detectaría; lo que sí se detecta es que el backend declare
        // disponibilidad que no tiene.
        assertFalse(
            "ON_DEVICE no puede estar disponible sin modelo de traducción",
            TranslationBackend.ON_DEVICE.available
        )
    }

    @Test
    fun `solo el backend con clave de usuario manda texto fuera`() {
        assertTrue(TranslationBackend.USER_API_KEY.sendsTextOffDevice)
        assertFalse(TranslationBackend.ON_DEVICE.sendsTextOffDevice)
        assertFalse(TranslationBackend.RULES_ONLY.sendsTextOffDevice)
    }
}
