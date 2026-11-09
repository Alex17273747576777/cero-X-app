package com.cerox.comun

/**
 * Cómo se traducen los recursos de la app, y qué sale del dispositivo.
 *
 * LA REGLA QUE MANDA AQUÍ
 * -----------------------
 * «Nada de datos sale del dispositivo salvo que el usuario ponga una clave de IA,
 * y eso hay que decirlo.»
 *
 * Los cinco proveedores no son iguales ante esa regla, y la diferencia importa:
 *
 *   · [Proveedor.LIBRE] — LibreTranslate en un servidor público. **Sale el texto**
 *     hacia un tercero, sin clave y sin cuenta. Por eso NO puede ser el que se
 *     active solo: el usuario lo elige sabiendo que el texto viaja.
 *   · [Proveedor.GOOGLE], [Proveedor.DEEPL], [Proveedor.MICROSOFT],
 *     [Proveedor.YOUDAO] — necesitan clave del usuario. La clave se guarda
 *     cifrada junto a los tokens y el texto va al proveedor.
 *   · [Proveedor.LOCAL] — el traductor que ya tiene la app
 *     (`LocalTranslator` + glosario + variante). **No sale nada.** Es el único
 *     que cumple la regla sin condiciones, y por eso es el de fábrica.
 *
 * Un menú de privacidad que dice «todo local» y tiene LibreTranslate activado por
 * defecto sería exactamente la clase de mentira que se está intentando evitar.
 */
object Traduccion {

    /** Un proveedor que sabe traducir texto de la app o del contenido. */
    enum class Proveedor(
        val id: String,
        val nombre: String,
        /** Si manda el texto fuera del dispositivo. Se dice en la pantalla. */
        val enviaTextoFuera: Boolean,
        /** Si necesita que el usuario aporte una clave. */
        val necesitaClave: Boolean,
        val nota: String,
    ) {
        LOCAL(
            "local", "Local (sin conexión)", enviaTextoFuera = false, necesitaClave = false,
            "Usa el traductor de reglas, el glosario del usuario y la variante elegida. " +
                "No sale nada del dispositivo. Es el que viene activado.",
        ),
        LIBRE(
            "libre", "LibreTranslate", enviaTextoFuera = true, necesitaClave = false,
            "Servidor público (por defecto translate.disroot.org, se puede cambiar la URL). " +
                "El texto que se traduce SALE del dispositivo. No pide cuenta, y por eso " +
                "hay que elegirlo a propósito.",
        ),
        GOOGLE(
            "google", "Google Translate", enviaTextoFuera = true, necesitaClave = true,
            "Necesita clave de API del usuario. El texto sale hacia Google.",
        ),
        DEEPL(
            "deepl", "DeepL", enviaTextoFuera = true, necesitaClave = true,
            "Necesita clave. Las claves que terminan en «:fx» son de la API gratuita y " +
                "van a api-free.deepl.com; el resto a api.deepl.com.",
        ),
        MICROSOFT(
            "microsoft", "Microsoft Translator", enviaTextoFuera = true, necesitaClave = true,
            "Necesita clave de Azure. El texto sale hacia Microsoft.",
        ),
        YOUDAO(
            "youdao", "Youdao", enviaTextoFuera = true, necesitaClave = true,
            "Necesita clave. Servicio pensado para chino; útil para zh y ja.",
        ),
        ;

        companion object {
            /** El de fábrica. Local, porque es el único que no manda nada fuera. */
            val POR_DEFECTO: Proveedor = LOCAL

            fun porId(id: String?): Proveedor =
                entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: POR_DEFECTO

            /** Los que se pueden usar sin clave del usuario. */
            val sinClave: List<Proveedor> get() = entries.filter { !it.necesitaClave }
        }
    }

    /**
     * Qué se va a traducir.
     *
     * Son tres cosas distintas y no se configuran igual: los textos de la app se
     * traducen una vez y se guardan; una ficha de una app se traduce al abrirla;
     * una página de manga se traduce en cada página y cuesta segundos.
     */
    enum class Objetivo(val id: String, val nombre: String) {
        INTERFAZ("interfaz", "Textos de la app"),
        FICHA("ficha", "Fichas y descripciones del catálogo"),
        CONTENIDO("contenido", "Páginas y capítulos"),
        ;
    }

    /**
     * Si un objetivo puede traducirse con un proveedor, y si no, por qué.
     *
     * Esto no es una tabla decorativa: cada negativa es una función que la
     * interfaz no debe ofrecer. Ofrecer «traducir páginas con el traductor local»
     * cuando el local solo tiene reglas de variante sería un botón que no hace
     * nada útil.
     */
    fun puedeTraducir(proveedor: Proveedor, objetivo: Objetivo): Resultado {
        if (proveedor == Proveedor.LOCAL && objetivo == Objetivo.CONTENIDO) {
            return Resultado(
                false,
                "el traductor local no traduce páginas: solo aplica el glosario y la variante " +
                    "sobre un texto que ya viene en español. Para páginas hace falta un " +
                    "proveedor con clave o LibreTranslate, y entonces el texto sale del dispositivo",
            )
        }
        return Resultado(true, "")
    }

    data class Resultado(val posible: Boolean, val motivo: String)

    /**
     * Un idioma de destino tiene que existir en el proveedor. LibreTranslate y
     * DeepL no soportan los mismos: ofrecer uno que no existe da un error del
     * servidor que el usuario no puede arreglar.
     */
    fun soporta(proveedor: Proveedor, destino: String): Boolean {
        val d = destino.substringBefore('-').lowercase()
        return when (proveedor) {
            Proveedor.LOCAL -> d in Idiomas.RECURSO.map { it.substringBefore('-') }
            Proveedor.LIBRE, Proveedor.GOOGLE, Proveedor.MICROSOFT, Proveedor.YOUDAO ->
                d in Idiomas.RECURSO.map { it.substringBefore('-') }
            Proveedor.DEEPL -> d in setOf(
                "ar", "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "hu",
                "id", "it", "ja", "ko", "lt", "lv", "nb", "no", "nl", "pl", "pt", "ro",
                "ru", "sk", "sl", "sv", "tr", "uk", "zh",
            )
        }
    }

    /**
     * Lo que la pantalla de ajustes tiene que decir, en una línea, sin adornos.
     */
    fun avisoDePrivacidad(proveedor: Proveedor): String = when {
        !proveedor.enviaTextoFuera ->
            "Traducción local: nada sale del dispositivo."
        proveedor.necesitaClave ->
            "Se enviará el texto a ${proveedor.nombre} con tu clave. Sin tu clave no se traduce."
        else ->
            "Se enviará el texto a ${proveedor.nombre}, un servidor público. " +
                "No hace falta cuenta, y por eso no viene activado."
    }

    /**
     * La URL de un proveedor que admite cambiarla. Solo LibreTranslate la admite:
     * los demás son servicios con una dirección fija.
     */
    fun urlDe(proveedor: Proveedor, urlDelUsuario: String?, clave: String? = null): String? =
        when (proveedor) {
            Proveedor.LIBRE -> urlDelUsuario?.takeIf { it.isNotBlank() }
                ?: "https://translate.disroot.org"
            Proveedor.GOOGLE -> "https://translation.googleapis.com/language/translate/v2"
            Proveedor.DEEPL -> if (clave?.endsWith(":fx") == true) {
                "https://api-free.deepl.com/v2/translate"
            } else {
                "https://api.deepl.com/v2/translate"
            }
            Proveedor.MICROSOFT -> "https://api.cognitive.microsofttranslator.com/translate"
            Proveedor.YOUDAO -> "https://openapi.youdao.com/api"
            Proveedor.LOCAL -> null
        }
}
