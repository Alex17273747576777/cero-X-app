package com.ceroX.app.modules.translation

/**
 * Un segmento de transcripción con su tiempo.
 *
 * `startMs`/`endMs` son lo que se usa para sincronizar con el audio. Sin tiempos
 * no hay sincronización, y una transcripción sin tiempos es un texto, no un
 * subtítulo.
 */
data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    /** Idioma detectado o forzado por el usuario. ISO 639-1. */
    val sourceLanguage: String,
    /** Confianza de la etapa que produjo el texto. 0 = no se sabe. */
    val confidence: Double = 0.0,
) {
    init {
        require(startMs >= 0) { "inicio negativo" }
        require(endMs >= startMs) { "fin ($endMs) anterior al inicio ($startMs)" }
    }

    val durationMs: Long get() = endMs - startMs
}

/** Una transcripción completa. */
data class Transcript(
    val segments: List<TranscriptSegment>,
    val sourceLanguage: String,
    val model: String,
) {
    fun joinedText(): String = segments.joinToString(" ") { it.text.trim() }.trim()

    fun totalDurationMs(): Long = segments.lastOrNull()?.endMs ?: 0L

    /** Segmentos que empiezan en una ventana, para pintar subtítulos. */
    fun activeAt(millis: Long): List<TranscriptSegment> =
        segments.filter { millis >= it.startMs && millis < it.endMs }
}

/**
 * Registro de idioma. Se usa **para no romper la traducción con modismos**, que
 * es justo lo que pedía el usuario: una traducción chueca solo se entiende en su
 * contexto.
 *
 * `pronouns` existe porque en japonés el sujeto se omite y en español hay que
 * ponerlo o suena a telegrama.
 */
enum class SpeechRegister(val id: String, val hint: String) {
    NEUTRAL("neutral", "sin marca: trata de usted implícito"),
    FORMAL("formal", "usted, sin contracciones"),
    COLLOQUIAL("coloquial", "tú, contracciones, frases cortas"),
    VULGAR("vulgar", "insultos y jerga tal cual, sin suavizar"),
    ARCHAIC("arcaico", "registro antiguo: fantasía, época"),
}

/**
 * Cómo hablar en una variante concreta.
 *
 * El usuario pidió «que se adapte a los modismos». Eso no se consigue con un
 * prompt genérico: se consigue con una tabla de diferencias por variante y unas
 * pocas reglas duras. Aquí están las reglas, no una promesa.
 */
data class LocaleVariant(
    val language: String,          // es
    val region: String,            // DO
    val displayName: String,
    /** Términos que el hablante usa. */
    val vocabulary: Map<String, String> = emptyMap(),
    /** Formas verbales o gramaticales de la variante. */
    val grammarNotes: List<String> = emptyList(),
    /** Lo que en esta variante suena mal viniendo de otra. */
    val avoid: List<String> = emptyList(),
) {
    val tag: String get() = "$language-$region"
}

/**
 * Variantes que cero-X reconoce.
 *
 * Solo están las que se pueden sostener con reglas escritas. **No es una lista
 * de todo el español**: es lo que está documentado aquí. Una variante que no
 * esté, usa [NEUTRAL_ES] y la app lo dice en vez de fingir conocerla.
 */
object LocaleCatalog {

    val NEUTRAL_ES = LocaleVariant(
        language = "es", region = "001", displayName = "Español neutro",
        grammarNotes = listOf("tuteo", "evita localismos"),
        avoid = listOf("vosotros (en Latinoamérica)", "coger con sentido sexual"),
    )

    val DO = LocaleVariant(
        language = "es", region = "DO", displayName = "Español dominicano",
        vocabulary = mapOf(
            "autobús" to "guagua",
            "palomitas" to "pochoclo",   // sí, se usa; y "crispetas" es de otro país
            "banana" to "guineo",
            "aguacate" to "aguacate",
            "cerveza" to "fría",
            "niño" to "chamaco / carajito",
            "trabajo" to "trabajo / jevi",
            "está bien" to "ta' bien",
            "mira" to "mira / dimelo",
        ),
        grammarNotes = listOf(
            "tuteo, casi nunca usted fuera de respeto formal",
            "«vaina» es comodín: no traducir literalmente, adaptar",
            "el diminutivo -ito no indica tamaño: indica cariño o cortesía",
            "se omite la -s final en registro coloquial (ej. «vamo'»), solo si es diálogo",
        ),
        avoid = listOf(
            "vosotros", "coger (como 'tomar')", "vale (como 'ok')",
            "ordenador", "móvil (aquí es celular)", "pijo", "guay",
        ),
    )

    val MX = LocaleVariant(
        language = "es", region = "MX", displayName = "Español mexicano",
        vocabulary = mapOf(
            "autobús" to "camión", "palomitas" to "palomitas", "banana" to "plátano",
            "niño" to "chavo / niño", "está bien" to "está bien / órale",
        ),
        grammarNotes = listOf("tuteo", "«güey» solo en coloquial y con cuidado"),
        avoid = listOf("vosotros", "ordenador", "guagua", "coger (como 'tomar')"),
    )

    val AR = LocaleVariant(
        language = "es", region = "AR", displayName = "Español rioplatense",
        vocabulary = mapOf("autobús" to "colectivo", "palomitas" to "pochoclo", "banana" to "banana"),
        grammarNotes = listOf("voseo: vos tenés, vos sabés", "«che» en coloquial"),
        avoid = listOf("tú tienes (aquí es vos tenés)", "vosotros", "ordenador"),
    )

    val ALL = listOf(NEUTRAL_ES, DO, MX, AR)

    fun forTag(tag: String): LocaleVariant =
        ALL.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: NEUTRAL_ES

    /** Si la variante está realmente soportada, o se está usando la neutra. */
    fun isSupported(tag: String): Boolean = ALL.any { it.tag.equals(tag, ignoreCase = true) }
}

/**
 * Glosario del usuario.
 *
 * Sirve para lo que ninguna regla general acierta: el nombre de un personaje, un
 * ataque, un honorífico. Se aplica **antes** de la traducción y **después**, y
 * las dos veces es necesario: antes para que el modelo no invente una variante
 * del nombre, después para arreglarlo si la inventó.
 */
class Glossary(entries: Map<String, String> = emptyMap()) {

    private val entries: Map<String, String> =
        entries.filterKeys { it.isNotBlank() }.mapKeys { it.key.trim() }

    val size: Int get() = entries.size

    fun apply(text: String): AppliedText {
        if (entries.isEmpty()) return AppliedText(text, hits = 0, applied = emptyList())
        var out = text
        var hits = 0
        val applied = mutableListOf<String>()
        // Se ordenan por longitud descendente: si existe "Luffy" y "Monkey D.
        // Luffy", el largo tiene que ganar o queda "Monkey D. Luffy" a medias.
        entries.keys.sortedByDescending { it.length }.forEach { from ->
            val to = entries.getValue(from)
            if (out.contains(from)) {
                out = out.replace(from, to)
                hits += out.split(to).size - 1
                applied += "$from → $to"
            }
        }
        return AppliedText(out, hits, applied)
    }

    fun without(source: String): Glossary = Glossary(entries - source)

    data class AppliedText(val text: String, val hits: Int, val applied: List<String>)
}

/**
 * Qué se le pide al motor de traducción.
 *
 * `preserveTiming` es lo que hace que un subtítulo no se desincronice: la
 * traducción suele cambiar la longitud, y si se reparte el texto nuevo por
 * tiempo proporcional, el subtítulo aparece antes que la voz.
 */
data class TranslationRequest(
    val transcript: Transcript,
    val targetLanguage: String,
    val variant: LocaleVariant,
    val register: SpeechRegister,
    val glossary: Glossary = Glossary(),
    val preserveTiming: Boolean = true,
)

/**
 * Resultado de traducir. Puede ser parcial, y **lo dice**.
 *
 * `segmentsTranslated < segmentsRequested` significa que quedó texto sin
 * traducir. La app debe enseñar el original en esos huecos, no inventar.
 */
data class TranslationResult(
    val translated: List<TranscriptSegment>,
    val segmentsRequested: Int,
    val segmentsTranslated: Int,
    val model: String,
    val notes: List<String> = emptyList(),
) {
    val complete: Boolean get() = segmentsTranslated == segmentsRequested

    val coveragePercent: Double
        get() = if (segmentsRequested == 0) 0.0 else 100.0 * segmentsTranslated / segmentsRequested
}

/**
 * Reajuste de tiempos tras traducir.
 *
 * El problema concreto: en japonés «うん» son 0,3 s y en español «Sí, claro que
 * sí» es más largo. Si se respeta la duración original, el subtítulo se corta.
 * Si se estira sin tocar los vecinos, se solapan.
 *
 * La solución que se aplica aquí: se permite crecer hasta [MAX_GROWTH_RATIO]
 * metiendo la diferencia en los huecos libres siguientes **sin mover el inicio**
 * (el inicio es lo que marca la sincronía con la boca). Si no hay hueco, el
 * segmento se queda con su duración y se marca como comprimido.
 */
object TimingAdjuster {

    /** Cuánto puede crecer un segmento como máximo, respecto al original. */
    const val MAX_GROWTH_RATIO = 2.5

    /** Hueco mínimo entre dos segmentos para que no se peguen. */
    const val MIN_GAP_MS = 40L

    data class Adjusted(val segments: List<TranscriptSegment>, val compressed: List<Int>)

    fun adjust(segments: List<TranscriptSegment>, spokenDurationsMs: List<Long>): Adjusted {
        if (segments.isEmpty()) return Adjusted(emptyList(), emptyList())
        require(spokenDurationsMs.size == segments.size) {
            "hacen falta duraciones para los ${segments.size} segmentos, llegaron ${spokenDurationsMs.size}"
        }

        val out = ArrayList<TranscriptSegment>(segments.size)
        val compressed = mutableListOf<Int>()

        segments.forEachIndexed { i, seg ->
            val want = spokenDurationsMs[i].coerceAtLeast(seg.durationMs)
            val cap = (seg.durationMs * MAX_GROWTH_RATIO).toLong()
            val nextStart = segments.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
            val room = (nextStart - seg.startMs - MIN_GAP_MS).coerceAtLeast(seg.durationMs)
            val effectiveCap = minOf(cap, room)
            val finalDuration = want.coerceAtMost(effectiveCap)

            // Solo se marca comprimido si de verdad no cupo lo que se quería.
            if (finalDuration < want) compressed += i

            out += seg.copy(endMs = seg.startMs + finalDuration)
        }
        return Adjusted(out, compressed)
    }
}
