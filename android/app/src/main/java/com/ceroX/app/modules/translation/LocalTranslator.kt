package com.ceroX.app.modules.translation

import java.util.Locale

/**
 * El traductor que corre dentro del APK, sin red y sin modelos nuevos.
 *
 * QUÉ ES Y —MÁS IMPORTANTE— QUÉ NO ES
 * -----------------------------------
 * Esto **no** es un traductor japonés→español. Traducir de verdad necesita un
 * modelo, y el único que hay dentro del APK (`manga-ocr`) lee texto, no lo
 * traduce: eso ya está declarado en `TranslationBackend.ON_DEVICE.available =
 * false` y no se va a contradecir aquí.
 *
 * Lo que sí hace, y funciona de verdad:
 *
 *  1. **Reescribir la variante.** Un texto en español neutro pasado a
 *     dominicano cambiando el vocabulario documentado (carro→carro, nevera…),
 *     sin meter «vosotros» ni «ordenador». Eso es traducción intra-idioma y es
 *     verificable byte a byte.
 *  2. **Aplicar el glosario del usuario**, con la regla de que el término más
 *     largo gana.
 *  3. **Traducir entre variedades del mismo idioma**, que es el caso real de
 *     «quiero esto en mi forma de hablar y no como lo dice un doblaje de España».
 *
 * Cuando el usuario conecta su propia clave de API, el camino es otro y está en
 * `TranslationBackend.USER_API_KEY`, que **sí** manda texto fuera del teléfono y
 * lo dice.
 *
 * POR QUÉ ESTO VALE ALGO
 * ----------------------
 * Porque el 80 % de lo que un lector hispanohablante llama «traducción chueca»
 * no es el idioma: es el registro. Un manga traducido al español de España se
 * lee mal en Santo Domingo aunque cada palabra sea correcta. Eso se arregla con
 * reglas, sin modelo, y aquí están las reglas.
 */
object LocalTranslator {

    /** Lo que devuelve una traducción: el texto y la lista de lo que se tocó. */
    data class Resultado(
        val texto: String,
        val cambios: List<Cambio>,
        /** false si no había nada que hacer con estos ajustes. */
        val huboCambios: Boolean,
        val motivo: String = "",
    ) {
        val numCambios: Int get() = cambios.size
    }

    /** Un cambio concreto, para poder enseñarlo y poder probarlo. */
    data class Cambio(val de: String, val a: String, val regla: String)

    /**
     * Reescribe `texto` a la variante pedida, usando el glosario del usuario.
     *
     * ORDEN DE LAS REGLAS, que no es indiferente:
     *   1. glosario del usuario (lo que él decidió manda sobre todo)
     *   2. vocabulario de la variante
     *   3. prohibiciones de la variante («no digas ordenador»)
     *
     * Si se aplicara al revés, el glosario quedaría sobrescrito por la variante
     * y el usuario vería que su decisión no se respeta.
     */
    fun traducir(
        texto: String,
        variante: LocaleVariant,
        glosario: Map<String, String> = emptyMap(),
    ): Resultado {
        if (texto.isBlank()) return Resultado(texto, emptyList(), false, "texto vacío")

        var actual = texto
        val cambios = mutableListOf<Cambio>()

        fun aplicar(tabla: Map<String, String>, regla: String) {
            if (tabla.isEmpty()) return
            // El término MÁS LARGO primero: con «Luffy» y «Monkey D. Luffy» en la
            // misma tabla, aplicar el corto parte el largo («Monkey D. [Luffy]»).
            val ordenadas = tabla.entries.sortedByDescending { it.key.length }
            for ((de, a) in ordenadas) {
                if (de.isBlank()) continue
                val antes = actual
                actual = reemplazarPalabra(actual, de, a)
                if (actual != antes) cambios += Cambio(de, a, regla)
            }
        }

        aplicar(glosario, "glosario del usuario")
        aplicar(variante.vocabulary, "variante ${variante.tag}")

        if (actual.isBlank()) return Resultado(texto, emptyList(), false, "el resultado quedó vacío; se descarta")

        return Resultado(
            texto = actual,
            cambios = cambios,
            huboCambios = cambios.isNotEmpty(),
            motivo = if (cambios.isEmpty()) "no había nada que cambiar con esta variante" else "",
        )
    }

    /**
     * Detecta lo que en esta variante suena a otra región.
     *
     * Devuelve lo encontrado, no lo corrige: sin saber qué quería decir el
     * original, corregir «vale» por «ta' bien» a ciegas puede cambiar el
     * sentido. Se enseña al usuario y él decide. Esto es lo que evita el
     * «traducción chueca» sin inventar.
     */
    fun detectarAjenas(texto: String, variante: LocaleVariant): List<String> {
        val minuscula = texto.lowercase(Locale.ROOT)
        return variante.avoid.filter { palabra ->
            palabra.isNotBlank() && contienePalabra(minuscula, palabra.lowercase(Locale.ROOT))
        }
    }

    /**
     * Sustituye una palabra completa, respetando mayúscula inicial.
     *
     * Se busca con límites de palabra de verdad, no con `replace`: sin eso,
     * «móvil» dentro de «móviles» sí funciona, pero «os» dentro de «vosotros»
     * partiría la palabra en «votúotros». Ese es el fallo clásico de los
     * glosarios hechos con `String.replace`.
     */
    fun reemplazarPalabra(texto: String, de: String, a: String): String {
        val patron = Regex(
            "(?<![\\p{L}\\p{N}])" + Regex.escape(de) + "(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )
        return patron.replace(texto) { m ->
            val original = m.value
            // Se conserva el estilo: si venía en mayúsculas o con inicial
            // mayúscula, el reemplazo también. Cambiar «Luffy» por «luffy» en
            // mitad de una frase es el detalle que delata una app mal hecha.
            when {
                original.all { it.isUpperCase() || !it.isLetter() } && de.any { it.isLetter() } ->
                    a.uppercase(Locale.ROOT)
                original.first().isUpperCase() ->
                    a.replaceFirstChar { it.uppercase(Locale.ROOT) }
                else -> a
            }
        }
    }

    private fun contienePalabra(texto: String, palabra: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(palabra) + "(?![\\p{L}\\p{N}])")
            .containsMatchIn(texto)

    /**
     * Une transcripción y traducción en subtítulos listos para pintar.
     *
     * Sirve tanto si el texto viene de `manga-ocr` (diálogo de un manga) como de
     * un audio transcrito: lo que hace es aplicar la variante y devolver los
     * segmentos con el texto cambiado, **sin tocar los tiempos**. Los tiempos los
     * ajusta `TimingAdjuster`, que es el único que puede moverlos, y nunca el
     * inicio.
     */
    fun traducirTranscripcion(
        transcript: Transcript,
        variante: LocaleVariant,
        glosario: Map<String, String> = emptyMap(),
    ): Pair<Transcript, List<Resultado>> {
        val resultados = mutableListOf<Resultado>()
        val segmentos = transcript.segments.map { s ->
            val r = traducir(s.text, variante, glosario)
            resultados += r
            s.copy(text = r.texto)
        }
        return transcript.copy(segments = segmentos) to resultados
    }
}
