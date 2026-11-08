package com.ceroX.app.modules.translation

/**
 * Adaptación al usuario **sin enviar datos a ningún sitio**.
 *
 * El usuario lo pidió así: aprendizaje local del remoto de los usuarios y sus
 * etapas, sin envío de datos, salvo si se usó una API key de IA.
 *
 * Lo que se guarda aquí son **preferencias de estilo**, no contenido:
 *
 *   • qué variante eligió
 *   • qué registro usa en cada tipo de obra
 *   • cuántas veces corrigió una traducción y en qué dirección
 *   • qué términos añadió al glosario
 *
 * Lo que **no** se guarda nunca: el texto traducido, el texto original, la voz
 * del usuario, ni las rutas de sus archivos. Un perfil que no contiene contenido
 * no puede filtrarlo.
 */
data class StylePreference(
    /** Veces que el usuario eligió esta variante. */
    val variantChosen: Int = 0,
    /** Veces que cambió de esta variante a otra: señal de que no le gusta. */
    val variantAbandoned: Int = 0,
    /** Registro elegido por tipo de obra. */
    val registerByKind: Map<String, SpeechRegister> = emptyMap(),
    /** Correcciones aplicadas, sin guardar el texto. */
    val corrections: Int = 0,
    /** Términos del glosario añadidos a mano. */
    val glossaryTerms: Int = 0,
) {
    /** Puntuación sencilla: elegido menos abandonado. Puede ser negativa. */
    fun score(): Int = variantChosen - variantAbandoned
}

/**
 * Etapa de aprendizaje. Sirve para decidir **cuándo se deja de preguntar**.
 *
 * La lógica: con pocas muestras, la app pregunta; con muchas, ya no molesta.
 * Preguntar para siempre es tan malo como no preguntar nunca.
 */
enum class LearningStage(val id: String, val samplesFor: Int) {
    /** Sin datos: se usan los valores por defecto y se pregunta siempre. */
    COLD("frio", 0),
    /** Pocas muestras: se sugiere, pero se pregunta. */
    WARM("tibio", 3),
    /** Suficientes: se aplica solo, y se permite deshacer. */
    SETTLED("asentado", 10),
    /** Muchas: se aplica solo y no se pregunta más. */
    LOCKED("fijado", 25);

    companion object {
        fun forSamples(n: Int): LearningStage =
            entries.lastOrNull { n >= it.samplesFor } ?: COLD

        /** A partir de aquí se deja de preguntar. */
        const val NO_MORE_ASKING = 25
    }
}

/**
 * El perfil de estilo completo, por dispositivo y por usuario de la app.
 *
 * No hay servidor, no hay sincronización implícita. Si el usuario quiere llevarlo
 * a otro teléfono, exporta el archivo — y ese archivo es suyo.
 */
class StyleProfile(
    private val preferences: Map<String, StylePreference> = emptyMap(),
) {
    fun samples(): Int = preferences.values.sumOf { it.variantChosen + it.variantAbandoned }

    fun stage(): LearningStage = LearningStage.forSamples(samples())

    /** La variante con mejor puntuación, o null si no hay datos. */
    fun preferredVariant(): String? =
        preferences.entries
            .filter { it.value.score() > 0 }
            .maxByOrNull { it.value.score() }
            ?.key

    fun preferenceFor(variantTag: String): StylePreference =
        preferences[variantTag] ?: StylePreference()

    /** ¿Hay que preguntar al usuario la próxima vez? */
    fun shouldAsk(): Boolean = samples() < LearningStage.NO_MORE_ASKING

    fun withChoice(variantTag: String): StyleProfile {
        val p = preferenceFor(variantTag)
        return StyleProfile(preferences + (variantTag to p.copy(variantChosen = p.variantChosen + 1)))
    }

    fun withAbandon(variantTag: String): StyleProfile {
        val p = preferenceFor(variantTag)
        return StyleProfile(preferences + (variantTag to p.copy(variantAbandoned = p.variantAbandoned + 1)))
    }

    fun withRegister(kind: String, register: SpeechRegister): StyleProfile {
        val tag = preferredVariant() ?: LocaleCatalog.NEUTRAL_ES.tag
        val p = preferenceFor(tag)
        return StyleProfile(preferences + (tag to p.copy(registerByKind = p.registerByKind + (kind to register))))
    }
}

/**
 * De dónde sale la traducción, y con qué consecuencias de privacidad.
 *
 * Esto se le enseña al usuario **antes** de traducir, no en un README. La
 * diferencia entre "todo local" y "manda tu texto a un servidor" no es un
 * detalle de configuración: es la diferencia entre que su biblioteca sea suya o
 * no.
 */
enum class TranslationBackend(
    val id: String,
    val displayName: String,
    /** Si el texto sale del dispositivo. */
    val sendsTextOffDevice: Boolean,
    /** Si necesita una clave que el usuario pone. */
    val needsApiKey: Boolean,
    /** Qué se le dice al usuario, en una línea, sin letra pequeña. */
    val privacyNote: String,
) {
    /**
     * Modelo dentro del APK. **Estado hoy: no hay ninguno de traducción.**
     *
     * El APK lleva `manga-ocr` (lee) y cinco modelos de imagen. Traducir no lo
     * hace ninguno. Esta opción existe en el enum para que el hueco se vea, y
     * `available = false` lo dice sin que nadie tenga que leer el código.
     */
    ON_DEVICE(
        id = "local",
        displayName = "Local (en el dispositivo)",
        sendsTextOffDevice = false,
        needsApiKey = false,
        privacyNote = "nada sale del teléfono",
    ) {
        override val available: Boolean get() = false
        override val unavailableReason: String
            get() = "no hay modelo de traducción dentro del APK todavía; " +
                "el que hay (manga-ocr) lee texto, no lo traduce"
    },

    /** Endpoint compatible con OpenAI que el usuario configura. */
    USER_API_KEY(
        id = "api_key",
        displayName = "API propia del usuario",
        sendsTextOffDevice = true,
        needsApiKey = true,
        privacyNote = "el texto se envía al proveedor que tú elijas, con tu clave",
    ) {
        override val available: Boolean get() = true
        override val unavailableReason: String get() = ""
    },

    /** Sin clave: usar el glosario y las reglas de variante, sin modelo. */
    RULES_ONLY(
        id = "reglas",
        displayName = "Solo reglas y glosario",
        sendsTextOffDevice = false,
        needsApiKey = false,
        privacyNote = "no traduce: sustituye términos del glosario y aplica reglas de variante",
    ) {
        override val available: Boolean get() = true
        override val unavailableReason: String get() = ""
    };

    abstract val available: Boolean
    abstract val unavailableReason: String

    companion object {
        fun fromId(id: String): TranslationBackend =
            entries.firstOrNull { it.id == id } ?: RULES_ONLY
    }
}
