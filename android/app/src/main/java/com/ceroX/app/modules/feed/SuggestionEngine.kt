package com.ceroX.app.modules.feed

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Sugerencias calculadas en el teléfono. Cero red.
 *
 * POR QUÉ EN EL TELÉFONO
 * ----------------------
 * La forma habitual de hacer recomendaciones es mandar el historial a un
 * servidor. Aquí no: lo que lees no sale del aparato. Eso obliga a que el
 * algoritmo sea barato, y por eso es deliberadamente simple —perfil de autor y
 * etiquetas, distancia de coseno— en vez de un modelo grande.
 *
 * DE DÓNDE SALE LA IDEA
 * ---------------------
 * Kisara tiene `data/suggestions/SuggestionsWorker.kt` y `SuggestionsReport.kt`
 * haciendo algo parecido con WorkManager. Kototoro tiene el motor de sugerencias
 * más grande del ecosistema (35 archivos) pero atado a su base de datos.
 * Esto no copia ninguno de los dos: es TF-IDF con coseno, escrito de cero, sin
 * dependencias, que cabe en un archivo y se puede probar sin Android.
 */
class SuggestionEngine {

    /** Lo que el motor necesita saber de una obra para poder recomendarla. */
    data class Obra(
        val id: String,
        val titulo: String,
        val autores: List<String> = emptyList(),
        val etiquetas: List<String> = emptyList(),
        val fuente: String = "",
        val valoracion: Float = 0f,
    )

    /** Un resultado con la afinidad y el motivo legible. */
    data class Sugerencia(
        val obra: Obra,
        val afinidad: Float,
        val motivo: String,
    )

    /** Historial del usuario: obra y cuánto la ha leído (capítulos o minutos). */
    data class Consumo(val obra: Obra, val peso: Float)

    /**
     * Construye el perfil del usuario.
     *
     * El peso de cada obra se amortigua con logaritmo: leer 300 capítulos de una
     * sola obra no debe tapar todo lo demás. Es el mismo motivo por el que los
     * buscadores usan logaritmos en TF-IDF: sin eso, el término más frecuente
     * domina el vector y la recomendación se vuelve "más de lo mismo".
     */
    fun perfil(historial: List<Consumo>): Map<String, Float> {
        val acumulado = HashMap<String, Float>()
        for (c in historial) {
            if (c.peso <= 0f) continue
            val peso = (1f + ln(c.peso.toDouble())).toFloat().coerceAtLeast(0.1f)
            for (t in terminos(c.obra)) {
                acumulado[t] = (acumulado[t] ?: 0f) + peso
            }
        }
        return normalizar(acumulado)
    }

    /**
     * Recomienda obras del catálogo que no estén ya en el historial.
     *
     * `afinidad` es el coseno entre el vector del usuario y el de la obra, de 0
     * a 1. El motivo se construye con los términos que más pesaron: un
     * porcentaje sin explicación no le dice nada a nadie.
     */
    fun recomendar(
        catalogo: List<Obra>,
        historial: List<Consumo>,
        limite: Int = 20,
    ): List<Sugerencia> {
        if (historial.isEmpty()) return emptyList()

        val p = perfil(historial)
        if (p.isEmpty()) return emptyList()
        val yaLeidas = historial.map { it.obra.id }.toHashSet()

        val resultados = ArrayList<Sugerencia>()
        for (obra in catalogo) {
            if (obra.id in yaLeidas) continue
            val v = normalizar(terminos(obra).associateWith { 1f })
            if (v.isEmpty()) continue

            val afinidad = coseno(p, v)
            if (afinidad < FeedLimits.AFINIDAD_MINIMA) continue

            // Los términos compartidos que más pesan en el perfil del usuario.
            val comunes = v.keys
                .filter { p.containsKey(it) }
                .sortedByDescending { p[it] ?: 0f }
                .take(3)

            val motivo = if (comunes.isEmpty()) {
                "Encaja con lo que lees"
            } else {
                "Porque lees ${comunes.joinToString(", ") { limpiarTermino(it) }}"
            }

            resultados.add(Sugerencia(obra, afinidad, motivo))
        }

        return resultados
            .sortedWith(compareByDescending<Sugerencia> { it.afinidad }.thenByDescending { it.obra.valoracion })
            .take(limite)
    }

    /**
     * Términos que representan una obra: sus autores y sus etiquetas.
     *
     * No se usan las palabras del título porque en manga los títulos son largos
     * y llenos de nombres propios, y dos obras del mismo autor no comparten
     * ninguna. El autor es la señal más fuerte que existe.
     */
    private fun terminos(obra: Obra): Set<String> {
        val t = HashSet<String>()
        for (a in obra.autores) if (a.isNotBlank()) t.add("autor:" + a.trim().lowercase())
        for (e in obra.etiquetas) if (e.isNotBlank()) t.add("tag:" + e.trim().lowercase())
        return t
    }

    /** Normaliza a longitud 1 para que el coseno no premie a las obras con más etiquetas. */
    private fun normalizar(v: Map<String, Float>): Map<String, Float> {
        var suma = 0.0
        for (x in v.values) suma += x.toDouble() * x
        val norma = sqrt(suma)
        if (norma == 0.0) return emptyMap()
        return v.mapValues { (it.value / norma).toFloat() }
    }

    private fun coseno(a: Map<String, Float>, b: Map<String, Float>): Float {
        var suma = 0f
        // Se recorre el más pequeño: es la mitad de trabajo con el mismo resultado.
        val (chico, grande) = if (a.size <= b.size) a to b else b to a
        for ((k, v) in chico) {
            val otro = grande[k] ?: continue
            suma += v * otro
        }
        return suma.coerceIn(0f, 1f)
    }

    /** "tag:ciencia ficción" -> "ciencia ficción", para que el motivo se lea. */
    private fun limpiarTermino(t: String): String = t.substringAfter(':', t)
}
