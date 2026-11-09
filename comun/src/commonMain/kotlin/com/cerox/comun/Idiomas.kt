package com.cerox.comun

/**
 * Los idiomas de cero-X: interfaz, recursos traducidos y contenido.
 *
 * POR QUÉ ESTO YA NO ES SOLO ESPAÑOL
 * ----------------------------------
 * La regla de «español dominicano obligatorio» es del **contenido que publica La
 * Bandita** —lo que escribimos nosotros—, no de las apps que hacemos. Aplicarla a
 * la interfaz era un error de lectura: obligaba a un usuario de Yakarta a leer
 * «Descargas» en dominicano. Las apps se comparten con toda La Bandita, no solo
 * con la hispana, así que la interfaz tiene los idiomas que de verdad existen en
 * los repositorios de extensiones y no solo los que nos gustan.
 *
 * LAS TRES COSAS QUE SE CONFUNDEN Y AQUÍ ESTÁN SEPARADAS
 * -----------------------------------------------------
 *   1. **Idioma de la interfaz** ([IdiomaInterfaz]): en qué idioma se leen los
 *      botones de cero-X. Se elige y se aplica en toda la app.
 *   2. **Idioma de los recursos** ([IdiomaRecurso]): en qué idiomas hay textos
 *      traducidos de la app. Un idioma puede estar en la lista 1 sin tener
 *      traducción todavía: entonces la app cae a inglés y lo dice, no finge.
 *   3. **Idioma del contenido** ([IdiomaContenido]): lo que traen las
 *      extensiones. Son 20 según el índice real de Keiyoushi (1556 extensiones,
 *      medido el 2026-11-08).
 *
 * EL MODO NEUTRO Y EL MODO CON MODISMOS
 * -------------------------------------
 * No son dos idiomas: son dos **registros** del mismo idioma. Neutro evita
 * regionalismos para que se entienda en todo el ámbito hispano; el registro con
 * modismos usa la tabla de [Variante]. Un usuario de Santo Domingo lee «guagua»;
 * uno de Bogotá lee «autobús». Ninguno de los dos tiene razón y los dos deben
 * poder leer la app.
 */
object Idiomas {

    // ── 1 · Interfaz ──────────────────────────────────────────────────────

    /**
     * Un idioma en el que la app se puede leer.
     *
     * `nativo` es el nombre en ese idioma, no en español: quien busca el suyo en
     * una lista busca «日本語», no «japonés».
     */
    data class IdiomaInterfaz(
        val tag: String,
        val nativo: String,
        val enEspanol: String,
        val escrituraDeDerechaAIzquierda: Boolean = false,
        /** Con qué idioma se cae si no hay textos traducidos completos. */
        val caeA: String = "en",
    )

    /**
     * Los 20 idiomas que aparecen de verdad en el índice de Keiyoushi, más los
     * que ya tiene la app.
     *
     * Los 20 son los medidos: en 455 extensiones, ja 141, pt 128, es 112, id 96,
     * vi 84, tr 80, fr 63, zh 63, ar 62, th 48, ru 31, it 19, ko 9, uk 7, ca 2,
     * bg 1, cs 1, de 1, y «all» 153. Un idioma que no aparece en ningún
     * repositorio de extensiones no se pone en la interfaz «por completitud»:
     * sería una promesa que nadie puede usar.
     */
    val INTERFAZ: List<IdiomaInterfaz> = listOf(
        IdiomaInterfaz("en", "English", "inglés"),
        IdiomaInterfaz("es", "Español", "español"),
        IdiomaInterfaz("ja", "日本語", "japonés"),
        IdiomaInterfaz("ko", "한국어", "coreano"),
        IdiomaInterfaz("zh", "中文", "chino simplificado"),
        IdiomaInterfaz("zh-TW", "繁體中文", "chino tradicional"),
        IdiomaInterfaz("pt", "Português", "portugués"),
        IdiomaInterfaz("pt-BR", "Português (Brasil)", "portugués de Brasil"),
        IdiomaInterfaz("fr", "Français", "francés"),
        IdiomaInterfaz("it", "Italiano", "italiano"),
        IdiomaInterfaz("de", "Deutsch", "alemán"),
        IdiomaInterfaz("ru", "Русский", "ruso"),
        IdiomaInterfaz("uk", "Українська", "ucraniano"),
        IdiomaInterfaz("ca", "Català", "catalán"),
        IdiomaInterfaz("cs", "Čeština", "checo"),
        IdiomaInterfaz("bg", "Български", "búlgaro"),
        IdiomaInterfaz("id", "Bahasa Indonesia", "indonesio"),
        IdiomaInterfaz("vi", "Tiếng Việt", "vietnamita"),
        IdiomaInterfaz("th", "ไทย", "tailandés"),
        IdiomaInterfaz("tr", "Türkçe", "turco"),
        IdiomaInterfaz("ar", "العربية", "árabe", escrituraDeDerechaAIzquierda = true),
        IdiomaInterfaz("hi", "हिन्दी", "hindi"),
        IdiomaInterfaz("bn", "বাংলা", "bengalí"),
        IdiomaInterfaz("fa", "فارسی", "persa", escrituraDeDerechaAIzquierda = true),
        IdiomaInterfaz("he", "עברית", "hebreo", escrituraDeDerechaAIzquierda = true),
        IdiomaInterfaz("pl", "Polski", "polaco"),
        IdiomaInterfaz("nl", "Nederlands", "neerlandés"),
        IdiomaInterfaz("sv", "Svenska", "sueco"),
        IdiomaInterfaz("fi", "Suomi", "finés"),
        IdiomaInterfaz("da", "Dansk", "danés"),
        IdiomaInterfaz("nb", "Norsk Bokmål", "noruego"),
        IdiomaInterfaz("el", "Ελληνικά", "griego"),
        IdiomaInterfaz("ro", "Română", "rumano"),
        IdiomaInterfaz("hu", "Magyar", "húngaro"),
        IdiomaInterfaz("ms", "Bahasa Melayu", "malayo"),
        IdiomaInterfaz("tl", "Filipino", "filipino"),
        IdiomaInterfaz("ta", "தமிழ்", "tamil"),
        IdiomaInterfaz("te", "తెలుగు", "telugu"),
        IdiomaInterfaz("vi-VN", "Tiếng Việt (Việt Nam)", "vietnamita de Vietnam"),
    )

    /**
     * Los que de verdad tienen textos completos. Se amplía cuando se traduce el
     * archivo de recursos; no antes.
     */
    val TRADUCIDOS: Set<String> = setOf("en", "es")

    /** El idioma pedido si está traducido; si no, inglés, y se avisa. */
    fun resolverConCaida(tag: String?): Pair<String, Boolean> {
        val limpio = normalizar(tag)
        if (limpio == null) return "en" to false
        if (limpio in TRADUCIDOS) return limpio to false
        // pt-BR puede leerse con los textos de pt y al contrario.
        val base = limpio.substringBefore('-')
        if (base in TRADUCIDOS) return base to false
        return "en" to true
    }

    /** Un tag de idioma como lo devuelve Android: `es-DO`, `pt-BR`, `zh-Hans`. */
    fun normalizar(tag: String?): String? {
        val t = tag?.trim()?.replace('_', '-') ?: return null
        if (t.isBlank()) return null
        val partes = t.split('-')
        val idioma = partes[0].lowercase()
        if (idioma.length !in 2..3) return null
        if (partes.size == 1) return idioma
        val resto = partes[1]
        // Región de dos letras en mayúsculas, escritura en Titlecase.
        val normalizado = if (resto.length == 2) resto.uppercase() else resto.lowercase()
            .replaceFirstChar { it.uppercase() }
        return "$idioma-$normalizado"
    }

    fun interfaz(tag: String?): IdiomaInterfaz? =
        INTERFAZ.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

    fun esDeDerechaAIzquierda(tag: String?): Boolean =
        interfaz(normalizar(tag))?.escrituraDeDerechaAIzquierda
            ?: (normalizar(tag)?.substringBefore('-') in listOf("ar", "fa", "he", "ur"))

    // ── 2 · Recursos ──────────────────────────────────────────────────────

    /**
     * Los idiomas a los que la app puede **traducir sus propios textos** con el
     * traductor que el usuario elija. Son los que soportan los proveedores, no
     * los que nos gustaría.
     */
    val RECURSO: List<String> = listOf(
        "ar", "bn", "bg", "ca", "cs", "da", "de", "el", "en", "es", "fa", "fi", "fr",
        "he", "hi", "hu", "id", "it", "ja", "ko", "ms", "nl", "no", "pl", "pt", "pt-BR",
        "ro", "ru", "sv", "th", "tl", "tr", "uk", "vi", "zh", "zh-TW",
    )

    // ── 3 · Contenido ─────────────────────────────────────────────────────

    /** Un idioma que traen las extensiones, con cuántas lo traen. */
    data class IdiomaContenido(val codigo: String, val extensiones: Int)

    /**
     * Los 20 idiomas medidos en el índice de Keiyoushi el 2026-11-08, con el
     * número de extensiones de cada uno. `all` es «multiidioma o sin idioma».
     */
    val CONTENIDO: List<IdiomaContenido> = listOf(
        IdiomaContenido("en", 455),
        IdiomaContenido("all", 153),
        IdiomaContenido("ja", 141),
        IdiomaContenido("pt", 128),
        IdiomaContenido("es", 112),
        IdiomaContenido("id", 96),
        IdiomaContenido("vi", 84),
        IdiomaContenido("tr", 80),
        IdiomaContenido("fr", 63),
        IdiomaContenido("zh", 63),
        IdiomaContenido("ar", 62),
        IdiomaContenido("th", 48),
        IdiomaContenido("ru", 31),
        IdiomaContenido("it", 19),
        IdiomaContenido("ko", 9),
        IdiomaContenido("uk", 7),
        IdiomaContenido("ca", 2),
        IdiomaContenido("bg", 1),
        IdiomaContenido("cs", 1),
        IdiomaContenido("de", 1),
    )

    /**
     * Qué idiomas de contenido puede filtrar la app.
     *
     * Se incluye siempre `all` (extensiones multiidioma) porque dejarlo fuera
     * esconde fuentes: filtrar por «español» y perder las que sirven cualquier
     * idioma es un filtro que miente.
     */
    fun filtrosDeContenido(incluirMultiidioma: Boolean = true): List<String> {
        val codigos = CONTENIDO.map { it.codigo }.filter { it != "all" }
        return if (incluirMultiidioma) codigos + "all" else codigos
    }

    /** ¿Hay alguna extensión en este idioma? Se pregunta antes de ofrecerlo. */
    fun hayContenidoEn(codigo: String): Boolean {
        val c = codigo.trim().lowercase()
        if (c == "all") return true
        return CONTENIDO.any { it.codigo == c && it.extensiones > 0 } ||
            CONTENIDO.any { it.codigo == c.substringBefore('-') && it.extensiones > 0 }
    }

    // ── 4 · Registro: neutro o con modismos ───────────────────────────────

    /**
     * Cómo se escribe la app y las traducciones.
     *
     * `NEUTRO` y `MODISMOS` no son idiomas distintos. NEUTRO evita palabras que
     * solo se entienden en un país; MODISMOS aplica la tabla de la variante
     * elegida. La misma frase en los dos:
     *
     *     NEUTRO   «Toma el autobús y paga con el móvil»
     *     MODISMOS «Coge la guagua y paga con el celular»   (es-DO)
     *
     * Un modo neutral no es «un español pobre»: es el que entiende desde México
     * hasta Argentina sin que nadie se tropiece.
     */
    enum class Registro(val id: String, val nombre: String, val explicacion: String) {
        NEUTRO(
            "neutro",
            "Neutro",
            "Sin regionalismos: se entiende en todo el ámbito hispano. Recomendado por defecto.",
        ),
        MODISMOS(
            "modismos",
            "Con modismos",
            "Usa la variante elegida (dominicana, rioplatense, mexicana…). Más cercano, " +
                "menos universal.",
        ),
    }

    /**
     * Aplica un registro a un texto.
     *
     * Con [Registro.NEUTRO] solo se aplica el glosario del usuario: quitar
     * regionalismos línea a línea necesitaría un diccionario por región de cada
     * palabra, y fingir que se tiene sería peor que decir que el modo neutro
     * significa «usar la variante neutra y tu glosario».
     */
    fun aplicarRegistro(
        texto: String,
        registro: Registro,
        variante: Variante.Definicion = Variante.NEUTRA,
        glosario: Map<String, String> = emptyMap(),
    ): String = when (registro) {
        Registro.NEUTRO -> Variante.aplicar(texto, Variante.NEUTRA, glosario)
        Registro.MODISMOS -> Variante.aplicar(texto, variante, glosario)
    }
}
