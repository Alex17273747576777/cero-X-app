package com.cerox.comun

/**
 * Las reglas de cero-X que no dependen de Android.
 *
 * POR QUÉ ESTÁN AQUÍ Y NO EN LA APP
 * ---------------------------------
 * Porque son las que deciden cosas y se pueden probar sin un teléfono. Cada una
 * viene de un fallo real que ya pasó en este proyecto:
 *
 *   · [OrdenNatural]: el capítulo 10 salía antes que el 2.
 *   · [PresupuestoDeCola]: siete descargas simultáneas tumbaron la red.
 *   · [PlanDeTracker]: la app quería subir el capítulo 12 con el 40 en la cuenta.
 *   · [Variante]: «traducciones chuecas» que solo entiende una región.
 *
 * Tenerlas en `commonMain` significa que la app de Android, la de escritorio y la
 * de iOS usan **la misma** regla, no tres copias que se van separando.
 */

/**
 * Comparador natural: «2» antes que «10».
 *
 * Es el bug más repetido de todos los lectores de manga: ordenar nombres de
 * archivo como cadenas pone el 10 antes que el 2. Se compara tramo a tramo: si
 * los dos caracteres son dígitos, se comparan como número; si no, como texto.
 */
object OrdenNatural {

    fun comparar(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ia = i
                var jb = j
                while (ia < a.length && a[ia].isDigit()) ia++
                while (jb < b.length && b[jb].isDigit()) jb++
                val na = a.substring(i, ia).trimStart('0').ifEmpty { "0" }
                val nb = b.substring(j, jb).trimStart('0').ifEmpty { "0" }
                val cmp = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
                if (cmp != 0) return cmp
                i = ia
                j = jb
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    fun ordenar(nombres: List<String>): List<String> =
        nombres.sortedWith { x, y -> comparar(x, y).let { if (it != 0) it else x.compareTo(y) } }

    /** El número de un nombre de capítulo; -1 si no hay ninguno. */
    fun numeroDe(nombre: String): Float {
        val m = Regex("""(\d+(?:[.,]\d+)?)""").find(nombre) ?: return -1f
        return m.groupValues[1].replace(',', '.').toFloatOrNull() ?: -1f
    }
}

/**
 * Cuántas descargas a la vez, y con qué frenos.
 *
 * EL FALLO QUE LO MOTIVÓ
 * ----------------------
 * Siete descargas simultáneas contra el mismo servidor: el servidor respondió
 * 429 a todas y el usuario se quedó sin nada. El tope no es una precaución
 * teórica; es lo que evita que la app se bloquee sola.
 */
object PresupuestoDeCola {

    const val MAX_RECURSOS = 100_000
    const val MAX_BYTES_TOTALES = 2L * 1024 * 1024 * 1024 * 1024 // 2 TiB
    const val MAX_INTENTOS = 3
    const val MAX_POR_HOST = 2

    /** Tope por tipo de archivo. Un `.exe` desconocido no hereda los 8 GiB de un vídeo. */
    fun topePorTipo(tipo: TipoRecurso): Long = when (tipo) {
        TipoRecurso.MANGA -> 512L * 1024 * 1024
        TipoRecurso.VIDEO -> 8L * 1024 * 1024 * 1024
        TipoRecurso.LIBRO -> 256L * 1024 * 1024
        TipoRecurso.AUDIO -> 1024L * 1024 * 1024
        TipoRecurso.OTRO -> 64L * 1024 * 1024
    }

    /**
     * Clasifica por extensión.
     *
     * `epub` está a propósito en LIBRO aunque también sea un archivo de historieta:
     * en la versión anterior estaba en dos tipos a la vez y el resultado dependía
     * del orden de declaración del enum. Un mapa explícito y una prueba de
     * exclusividad lo impiden.
     */
    fun clasificar(nombre: String): TipoRecurso {
        val ext = nombre.substringAfterLast('.', "").lowercase()
        return AMBIGUOS[ext] ?: EXTENSIONES[ext] ?: TipoRecurso.OTRO
    }

    private val AMBIGUOS = mapOf("epub" to TipoRecurso.LIBRO)

    private val EXTENSIONES: Map<String, TipoRecurso> = buildMap {
        listOf("cbz", "cbr", "cb7", "zip", "rar", "7z").forEach { put(it, TipoRecurso.MANGA) }
        listOf("jpg", "jpeg", "png", "webp", "avif", "gif").forEach { put(it, TipoRecurso.MANGA) }
        listOf("mp4", "mkv", "webm", "avi", "mov").forEach { put(it, TipoRecurso.VIDEO) }
        listOf("pdf", "mobi", "azw3", "txt", "md").forEach { put(it, TipoRecurso.LIBRO) }
        listOf("mp3", "m4a", "flac", "ogg", "opus", "wav").forEach { put(it, TipoRecurso.AUDIO) }
    }

    enum class TipoRecurso { MANGA, VIDEO, LIBRO, AUDIO, OTRO }

    /** Cuántas a la vez según el perfil de rendimiento. Nunca más de dos. */
    fun concurrencia(perfil: String): Int = when (perfil.lowercase()) {
        "ahorro", "power_saver" -> 1
        else -> 2
    }

    /** Espera antes del intento n: 1 s, 2 s, 4 s… con techo de 30 s. */
    fun esperaAntesDelIntento(intento: Int): Long {
        if (intento <= 0) return 0
        val base = 1_000L shl (intento - 1).coerceAtMost(5)
        return base.coerceAtMost(30_000L)
    }

    /**
     * Progreso honesto.
     *
     * Sin tamaño esperado no hay progreso: devuelve 0, no 100. Un «completo»
     * calculado sobre un total desconocido es falso.
     */
    fun progreso(bytesHechos: Long, bytesEsperados: Long): Float {
        if (bytesEsperados <= 0) return 0f
        return (bytesHechos.toDouble() / bytesEsperados.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }
}

/**
 * La regla que evita borrar el progreso de una cuenta.
 *
 * Un tracker que sube el capítulo 12 cuando la cuenta tiene el 40 no está
 * sincronizando: está destruyendo lo que el usuario marcó a mano.
 */
object PlanDeTracker {

    enum class Resultado { ENVIAR, OMITIR_POR_RETROCESO, OMITIR_POR_DATOS, YA_AL_DIA }

    data class Plan(val resultado: Resultado, val capituloAEnviar: Int, val motivo: String)

    fun planificar(capituloLocal: Int, capituloRemoto: Int, totalConocido: Int): Plan {
        if (capituloRemoto < 0) {
            return Plan(Resultado.OMITIR_POR_DATOS, 0, "no se pudo leer el progreso remoto")
        }
        if (capituloLocal < capituloRemoto) {
            return Plan(
                Resultado.OMITIR_POR_RETROCESO, 0,
                "el tracker ya tiene el capítulo $capituloRemoto; el local va por $capituloLocal",
            )
        }
        if (capituloLocal == capituloRemoto) {
            return Plan(Resultado.YA_AL_DIA, 0, "ya estaba al día")
        }
        val terminado = totalConocido > 0 && capituloLocal >= totalConocido
        return Plan(
            Resultado.ENVIAR, capituloLocal,
            if (terminado) "último capítulo: se marca como terminado"
            else if (totalConocido <= 0) "sin total conocido: se queda en curso"
            else "capítulo $capituloLocal de $totalConocido",
        )
    }

    /** ¿Se puede marcar como terminado? Solo si el progreso es el total. */
    fun sePuedeTerminar(capitulo: Int, total: Int): Boolean = total > 0 && capitulo >= total
}

/**
 * La variante del idioma, con reglas y no con un prompt.
 *
 * «Que se adapte a los modismos» no se consigue pidiéndolo: se consigue con una
 * tabla de diferencias y unas prohibiciones. Estas son las documentadas.
 */
object Variante {

    data class Definicion(
        val codigo: String,
        val nombre: String,
        val vocabulario: Map<String, String>,
        val evitar: List<String>,
    )

    val DOMINICANA = Definicion(
        codigo = "es-DO",
        nombre = "Español dominicano",
        vocabulario = mapOf(
            "autobús" to "guagua",
            "banana" to "guineo",
            "esta bien" to "ta' bien",
            "móvil" to "celular",
            "ordenador" to "computadora",
        ),
        evitar = listOf("vosotros", "ordenador", "móvil", "vale", "guay"),
    )

    val RIOPLATENSE = Definicion(
        codigo = "es-AR",
        nombre = "Español rioplatense",
        vocabulario = mapOf("tú" to "vos", "móvil" to "celular", "ordenador" to "computadora"),
        evitar = listOf("vosotros", "ordenador", "móvil"),
    )

    val NEUTRA = Definicion("es", "Español neutro", emptyMap(), emptyList())

    val todas: List<Definicion> = listOf(DOMINICANA, RIOPLATENSE, NEUTRA)

    fun porCodigo(codigo: String): Definicion =
        todas.firstOrNull { it.codigo.equals(codigo, ignoreCase = true) } ?: NEUTRA

    /**
     * Sustituye una palabra completa, conservando el estilo.
     *
     * `String.replace` parte palabras: «os» dentro de «vosotros» se convierte en
     * «ustedesotros». Los límites de palabra tienen que ser explícitos.
     */
    fun reemplazar(texto: String, de: String, a: String): String {
        val patron = Regex(
            "(?<![\\p{L}\\p{N}])" + Regex.escape(de) + "(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )
        return patron.replace(texto) { m ->
            val original = m.value
            when {
                original.all { it.isUpperCase() || !it.isLetter() } && de.any { it.isLetter() } ->
                    a.uppercase()
                original.first().isUpperCase() -> a.replaceFirstChar { it.uppercase() }
                else -> a
            }
        }
    }

    /** Aplica el glosario del usuario primero y luego el de la variante. */
    fun aplicar(texto: String, definicion: Definicion, glosario: Map<String, String> = emptyMap()): String {
        var actual = texto
        for (tabla in listOf(glosario, definicion.vocabulario)) {
            tabla.entries.sortedByDescending { it.key.length }.forEach { (de, a) ->
                if (de.isNotBlank()) actual = reemplazar(actual, de, a)
            }
        }
        return actual
    }
}
