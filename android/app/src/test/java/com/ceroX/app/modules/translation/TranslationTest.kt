package com.ceroX.app.modules.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transcripción, variantes, glosario, tiempos y privacidad.
 *
 * Lo que se prueba con más insistencia es lo que **no** se hace: no inventar
 * traducción cuando no hay modelo, no decir "local" cuando se manda texto fuera,
 * y no dejar que un subtítulo se desincronice en silencio.
 */
class TranslationTest {

    // ── El hueco se declara: sin modelo no hay traducción ────────────────────

    @Test
    fun `no hay backend de traduccion local todavia, y lo dice`() {
        assertFalse(TranslationBackend.ON_DEVICE.available)
        assertTrue(TranslationBackend.ON_DEVICE.unavailableReason.contains("no hay modelo"))
        assertTrue(
            "el motivo tiene que aclarar que el OCR solo lee",
            TranslationBackend.ON_DEVICE.unavailableReason.contains("lee texto"),
        )
    }

    @Test
    fun `el modo local no manda nada fuera y no pide clave`() {
        assertFalse(TranslationBackend.ON_DEVICE.sendsTextOffDevice)
        assertFalse(TranslationBackend.ON_DEVICE.needsApiKey)
    }

    @Test
    fun `la api del usuario si manda texto fuera y lo advierte`() {
        assertTrue(TranslationBackend.USER_API_KEY.sendsTextOffDevice)
        assertTrue(TranslationBackend.USER_API_KEY.needsApiKey)
        assertTrue(TranslationBackend.USER_API_KEY.privacyNote.contains("se envía"))
    }

    @Test
    fun `solo reglas no traduce y lo dice en la nota`() {
        assertFalse(TranslationBackend.RULES_ONLY.sendsTextOffDevice)
        assertTrue(TranslationBackend.RULES_ONLY.privacyNote.contains("no traduce"))
    }

    @Test
    fun `un backend desconocido cae en el mas conservador`() {
        assertEquals(TranslationBackend.RULES_ONLY, TranslationBackend.fromId("magia"))
    }

    // ── Segmentos: sin tiempos no hay subtítulo ──────────────────────────────

    @Test
    fun `un segmento con fin anterior al inicio no se construye`() {
        assertTrue(
            runCatching { TranscriptSegment(100, 50, "x", "ja") }.isFailure,
        )
    }

    @Test
    fun `activeAt devuelve solo lo que suena en ese instante`() {
        val t = Transcript(
            listOf(
                TranscriptSegment(0, 1_000, "uno", "ja"),
                TranscriptSegment(1_000, 2_000, "dos", "ja"),
            ),
            "ja", "manga-ocr",
        )
        assertEquals(listOf("uno"), t.activeAt(500).map { it.text })
        assertEquals(listOf("dos"), t.activeAt(1_500).map { it.text })
        assertTrue(t.activeAt(9_999).isEmpty())
    }

    @Test
    fun `joinedText une sin dejar huecos dobles`() {
        val t = Transcript(
            listOf(
                TranscriptSegment(0, 100, " hola ", "ja"),
                TranscriptSegment(100, 200, " mundo ", "ja"),
            ),
            "ja", "m",
        )
        assertEquals("hola mundo", t.joinedText())
    }

    // ── Variantes: se aplican reglas, no promesas ────────────────────────────

    @Test
    fun `el dominicano tiene vocabulario propio`() {
        assertEquals("guagua", LocaleCatalog.DO.vocabulary["autobús"])
        assertEquals("guineo", LocaleCatalog.DO.vocabulary["banana"])
    }

    @Test
    fun `el dominicano prohibe vosotros y ordenador`() {
        assertTrue(LocaleCatalog.DO.avoid.contains("vosotros"))
        assertTrue(LocaleCatalog.DO.avoid.contains("ordenador"))
        assertTrue(LocaleCatalog.DO.avoid.contains("móvil (aquí es celular)"))
    }

    @Test
    fun `el rioplatense usa voseo`() {
        assertTrue(LocaleCatalog.AR.grammarNotes.any { it.contains("voseo") })
    }

    @Test
    fun `una variante no soportada cae a neutro y se puede saber`() {
        assertEquals(LocaleCatalog.NEUTRAL_ES, LocaleCatalog.forTag("es-XX"))
        assertFalse(LocaleCatalog.isSupported("es-XX"))
        assertTrue(LocaleCatalog.isSupported("es-DO"))
    }

    @Test
    fun `cada variante declara lo que suena mal`() {
        LocaleCatalog.ALL.forEach { v ->
            assertTrue("${v.tag} no dice qué evitar", v.avoid.isNotEmpty())
        }
    }

    // ── Glosario: lo largo gana ──────────────────────────────────────────────

    @Test
    fun `el glosario sustituye y cuenta`() {
        val g = Glossary(mapOf("Luffy" to "Luffy"))
        val r = g.apply("Luffy y Luffy")
        assertEquals(2, r.hits)
    }

    @Test
    fun `el termino mas largo gana`() {
        val g = Glossary(mapOf("Luffy" to "LUFFY", "Monkey D. Luffy" to "MONKEY"))
        val r = g.apply("Monkey D. Luffy")
        assertTrue("el corto no debe partir el largo", r.text.contains("MONKEY"))
        assertFalse(r.text.contains("MONKEY D. LUFFY"))
    }

    @Test
    fun `sin entradas no se toca el texto`() {
        val r = Glossary().apply("texto intacto")
        assertEquals("texto intacto", r.text)
        assertEquals(0, r.hits)
    }

    @Test
    fun `se puede quitar un termino`() {
        val g = Glossary(mapOf("a" to "b", "c" to "d"))
        assertEquals(1, g.without("a").size)
    }

    // ── Tiempos: el subtítulo no se desincroniza ─────────────────────────────

    @Test
    fun `un segmento puede crecer si hay hueco`() {
        val segs = listOf(
            TranscriptSegment(0, 1_000, "a", "ja"),
            TranscriptSegment(5_000, 6_000, "b", "ja"),
        )
        val r = TimingAdjuster.adjust(segs, listOf(2_000, 1_000))
        assertEquals(2_000, r.segments[0].durationMs)
        assertTrue(r.compressed.isEmpty())
    }

    @Test
    fun `sin hueco el segmento se queda y se marca comprimido`() {
        val segs = listOf(
            TranscriptSegment(0, 500, "a", "ja"),
            TranscriptSegment(500, 1_000, "b", "ja"),
        )
        val r = TimingAdjuster.adjust(segs, listOf(3_000, 500))
        assertTrue("no cabe crecer a 3 s sin pisar al siguiente", r.compressed.contains(0))
        assertTrue(r.segments[0].endMs <= 500)
    }

    @Test
    fun `el inicio no se mueve nunca`() {
        val segs = listOf(TranscriptSegment(700, 1_000, "a", "ja"))
        val r = TimingAdjuster.adjust(segs, listOf(2_000))
        assertEquals("el inicio marca la boca: moverlo es desincronizar", 700, r.segments[0].startMs)
    }

    @Test
    fun `el crecimiento tiene techo de dos veces y media`() {
        val segs = listOf(TranscriptSegment(0, 1_000, "a", "ja"))
        val r = TimingAdjuster.adjust(segs, listOf(100_000))
        assertTrue(r.segments[0].durationMs <= 2_500)
        assertTrue(r.compressed.contains(0))
    }

    @Test
    fun `un segmento no encoge aunque la traduccion sea corta`() {
        val segs = listOf(TranscriptSegment(0, 1_000, "a", "ja"))
        val r = TimingAdjuster.adjust(segs, listOf(100))
        assertEquals(1_000, r.segments[0].durationMs)
    }

    @Test
    fun `duraciones y segmentos tienen que cuadrar`() {
        val segs = listOf(TranscriptSegment(0, 100, "a", "ja"))
        assertTrue(runCatching { TimingAdjuster.adjust(segs, listOf(1, 2)) }.isFailure)
    }

    // ── Resultado parcial: se dice, no se disimula ───────────────────────────

    @Test
    fun `un resultado parcial no se declara completo`() {
        val r = TranslationResult(
            translated = emptyList(), segmentsRequested = 10, segmentsTranslated = 6, model = "x",
        )
        assertFalse(r.complete)
        assertEquals(60.0, r.coveragePercent, 0.01)
    }

    @Test
    fun `sin segmentos la cobertura es cero, no cien`() {
        val r = TranslationResult(emptyList(), 0, 0, "x")
        assertEquals(0.0, r.coveragePercent, 0.001)
    }

    // ── Perfil local: aprende sin guardar contenido ──────────────────────────

    @Test
    fun `un perfil vacio esta en frio y pregunta`() {
        val p = StyleProfile()
        assertEquals(LearningStage.COLD, p.stage())
        assertTrue(p.shouldAsk())
        assertNull(p.preferredVariant())
    }

    @Test
    fun `tras elegir varias veces domina esa variante`() {
        var p = StyleProfile()
        repeat(5) { p = p.withChoice("es-DO") }
        p = p.withChoice("es-MX")
        assertEquals("es-DO", p.preferredVariant())
    }

    @Test
    fun `abandonar una variante baja su puntuacion`() {
        var p = StyleProfile().withChoice("es-MX")
        repeat(3) { p = p.withAbandon("es-MX") }
        assertEquals(-2, p.preferenceFor("es-MX").score())
        assertNull("no se elige una variante que el usuario abandonó", p.preferredVariant())
    }

    @Test
    fun `tras muchas muestras deja de preguntar`() {
        var p = StyleProfile()
        repeat(LearningStage.NO_MORE_ASKING) { p = p.withChoice("es-DO") }
        assertFalse(p.shouldAsk())
        assertEquals(LearningStage.LOCKED, p.stage())
    }

    @Test
    fun `las etapas suben con las muestras`() {
        assertEquals(LearningStage.COLD, LearningStage.forSamples(0))
        assertEquals(LearningStage.WARM, LearningStage.forSamples(3))
        assertEquals(LearningStage.SETTLED, LearningStage.forSamples(10))
        assertEquals(LearningStage.LOCKED, LearningStage.forSamples(100))
    }

    @Test
    fun `el perfil guarda registro por tipo de obra`() {
        var p = StyleProfile().withChoice("es-DO")
        p = p.withRegister("manga", SpeechRegister.COLLOQUIAL)
        assertEquals(
            SpeechRegister.COLLOQUIAL,
            p.preferenceFor("es-DO").registerByKind["manga"],
        )
    }

    @Test
    fun `la preferencia no contiene campos de texto de la obra`() {
        // Comprobación por reflexión: si alguien añade "lastTranslatedText",
        // esta prueba lo caza antes de que se envíe nada.
        val campos = StylePreference::class.java.declaredFields.map { it.name.lowercase() }
        val prohibidos = listOf("text", "content", "transcript", "original", "path", "voice", "audio")
        prohibidos.forEach { p ->
            assertFalse(
                "el perfil no puede guardar '$p': sería contenido del usuario",
                campos.any { it.contains(p) },
            )
        }
        assertNotNull(campos)
    }
}
