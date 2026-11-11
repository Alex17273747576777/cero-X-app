package com.ceroX.app.modules.iptv

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Horas de XMLTV.
 *
 * El formato es `AAAAMMDDHHMMSS` con un desfase opcional, pegado o separado por un
 * espacio, y a veces con decimales: `20261110213000 +0200`, `20261110193000`,
 * `20261110193000.500 -0300`. La spec también admite formas cortas sin minutos ni
 * segundos.
 *
 * Se devuelve null cuando no se entiende, nunca cero: un 0 es 1970 y colocaría el
 * programa en una guía de hace cincuenta años sin que nadie se entere.
 */
object HoraXmltv {

    private val PATRONES = mapOf(
        14 to "yyyyMMddHHmmss",
        12 to "yyyyMMddHHmm",
        10 to "yyyyMMddHH",
        8 to "yyyyMMdd",
    )

    /** Ocho a catorce dígitos, decimales opcionales y desfase opcional. */
    private val FORMA = Regex("^(\\d{8,14})(?:\\.\\d+)?\\s*([+-]\\d{4})?$")

    fun aMillis(texto: String?): Long? {
        if (texto == null) return null
        val limpio = texto.trim()
        if (limpio.isEmpty()) return null
        val m = FORMA.find(limpio) ?: return null
        val digitos = m.groupValues[1]
        val desfase = m.groupValues[2]
        val patron = PATRONES[digitos.length] ?: return null
        return try {
            val f = SimpleDateFormat(patron, Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            f.isLenient = false
            val fecha = f.parse(digitos) ?: return null
            var millis = fecha.time
            if (desfase.isNotEmpty()) {
                val signo = if (desfase[0] == '-') -1L else 1L
                val horas = desfase.substring(1, 3).toLong()
                val minutos = desfase.substring(3, 5).toLong()
                // La hora escrita es local, así que para pasarla a millis UTC hay
                // que restarle el desfase: las 21:30 +0200 son las 19:30 UTC.
                millis -= signo * (horas * 3600L + minutos * 60L) * 1000L
            }
            millis
        } catch (e: Exception) {
            null
        }
    }

    /** De vuelta al formato de XMLTV, en UTC, para escribir una guía. */
    fun aTexto(millis: Long): String {
        val f = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(millis)) + " +0000"
    }
}

/** Un canal tal como lo declara la guía: su id y los nombres con que lo llama. */
data class CanalGuia(
    val id: String,
    val nombres: List<String>,
    val icono: String?,
) {
    /** El nombre que se enseña: el primero, que es el que usa la fuente. */
    val nombre: String get() = nombres.firstOrNull() ?: id
}

/** Un programa de la guía. `fin` puede faltar: hay guías que no lo ponen. */
data class Programa(
    val canal: String,
    val inicio: Long,
    val fin: Long?,
    val titulo: String,
    val subtitulo: String?,
    val descripcion: String?,
    val categorias: List<String>,
    val icono: String?,
) {
    val duracionMinutos: Int? get() = fin?.let { ((it - inicio) / 60000L).toInt() }
}

/**
 * Qué se emite y qué viene después.
 *
 * Es lógica pura sobre listas de [Programa]: no toca Android, así que se prueba en
 * el JVM sin Robolectric.
 */
object Guia {

    /** Programas agrupados por canal y ordenados por hora de inicio. */
    fun porCanal(programas: List<Programa>): Map<String, List<Programa>> =
        programas.groupBy { it.canal }.mapValues { (_, v) -> v.sortedBy { it.inicio } }

    /**
     * Lo que se está emitiendo en un canal a una hora dada.
     *
     * Si varios dicen que se emiten a la vez —que pasa con guías mal construidas—
     * se toma el que empezó más tarde, que es el que tiene más papeletas de ser el
     * real. Un programa sin `fin` cuenta como en emisión desde su inicio.
     */
    fun enEmision(programas: List<Programa>, canal: String, ahora: Long): Programa? =
        programas.asSequence()
            .filter { it.canal == canal && it.inicio <= ahora }
            .filter { it.fin == null || ahora < it.fin }
            .maxByOrNull { it.inicio }

    /** Los siguientes programas a partir de una hora, en orden. */
    fun aContinuacion(programas: List<Programa>, canal: String, ahora: Long, cuantos: Int): List<Programa> {
        if (cuantos <= 0) return emptyList()
        return programas.asSequence()
            .filter { it.canal == canal && it.inicio >= ahora }
            .sortedBy { it.inicio }
            .take(cuantos)
            .toList()
    }

    /** Programas que se solapan con una franja, para pintar una parrilla. */
    fun franja(programas: List<Programa>, canal: String, desde: Long, hasta: Long): List<Programa> =
        programas.filter {
            it.canal == canal && it.inicio < hasta && (it.fin == null || it.fin > desde)
        }.sortedBy { it.inicio }
}

/**
 * Lector de guías XMLTV.
 *
 * Usa el `XmlPullParser` del sistema, que es lo que hay en Android para XML y no
 * añade ninguna dependencia. Un programa con la hora de inicio ilegible se descarta
 * con su razón en [ResultadoGuia.descartados] y no arrastra a los demás: las guías
 * grandes siempre traen alguna entrada rota, y dejar de leer toda la guía por una
 * línea es peor que señalarla.
 */
object LectorXmltv {

    /**
     * Tope de programas por guía.
     *
     * Una guía de siete días de doscientos canales pasa de cien mil programas y se
     * come la memoria de un móvil sin que nadie lo pida. Se corta y se dice.
     */
    const val TOPE_PROGRAMAS = 20000

    fun leer(xml: String, origen: String = "guia"): ResultadoGuia {
        val canales = LinkedHashMap<String, MutableList<String>>()
        val iconosDeCanal = HashMap<String, String>()
        val programas = mutableListOf<Programa>()
        val descartados = mutableListOf<String>()

        try {
            val factoria = XmlPullParserFactory.newInstance()
            factoria.isNamespaceAware = false
            val p = factoria.newPullParser()
            p.setInput(StringReader(xml))

            var canalActual: String? = null
            var nombresActuales: MutableList<String>? = null
            var iconoDeCanal: String? = null
            var enPrograma: ProgCrudo? = null
            var saltarPrograma = false
            var elemento: String? = null
            val texto = StringBuilder()

            var evento = p.eventType
            while (evento != XmlPullParser.END_DOCUMENT) {
                when (evento) {
                    XmlPullParser.START_TAG -> {
                        val nombre = p.name
                        when (nombre) {
                            "channel" -> {
                                val id = p.getAttributeValue(null, "id")
                                if (id.isNullOrBlank()) {
                                    descartados.add("hay un <channel> sin atributo id")
                                    canalActual = null
                                    nombresActuales = null
                                } else {
                                    canalActual = id
                                    nombresActuales = canales.getOrPut(id) { mutableListOf() }
                                    iconoDeCanal = null
                                }
                            }

                            "programme" -> {
                                val idCanal = p.getAttributeValue(null, "channel")
                                val inicio = HoraXmltv.aMillis(p.getAttributeValue(null, "start"))
                                if (idCanal.isNullOrBlank() || inicio == null) {
                                    val razon = when {
                                        idCanal.isNullOrBlank() -> "sin atributo channel"
                                        else -> "con start ilegible: '${p.getAttributeValue(null, "start")}'"
                                    }
                                    descartados.add("programme $razon")
                                    saltarPrograma = true
                                    enPrograma = null
                                } else if (programas.size >= TOPE_PROGRAMAS) {
                                    saltarPrograma = true
                                } else {
                                    saltarPrograma = false
                                    enPrograma = ProgCrudo(
                                        canal = idCanal,
                                        inicio = inicio,
                                        fin = HoraXmltv.aMillis(p.getAttributeValue(null, "stop")),
                                    )
                                }
                            }

                            "title", "sub-title", "desc", "category", "display-name" -> {
                                elemento = nombre
                                texto.setLength(0)
                            }

                            "icon" -> {
                                val src = p.getAttributeValue(null, "src")?.takeIf { it.isNotBlank() }
                                val prog = enPrograma
                                if (prog != null && !saltarPrograma) {
                                    if (prog.icono == null) prog.icono = src
                                } else if (nombresActuales != null && iconoDeCanal == null) {
                                    iconoDeCanal = src
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        if (elemento != null) texto.append(p.text)
                    }

                    XmlPullParser.END_TAG -> {
                        val nombre = p.name
                        when (nombre) {
                            "display-name" -> {
                                val valor = texto.toString().trim()
                                if (valor.isNotEmpty()) nombresActuales?.add(valor)
                            }

                            "channel" -> {
                                val id = canalActual
                                if (id != null && iconoDeCanal != null) iconosDeCanal[id] = iconoDeCanal
                                canalActual = null
                                nombresActuales = null
                                iconoDeCanal = null
                            }

                            "title", "sub-title", "desc", "category" -> {
                                val prog = enPrograma
                                if (prog != null && !saltarPrograma) {
                                    val valor = texto.toString().trim()
                                    when (nombre) {
                                        "title" -> if (prog.titulo == null) prog.titulo = valor
                                        "sub-title" -> if (prog.subtitulo == null) prog.subtitulo = valor
                                        "desc" -> if (prog.descripcion == null) prog.descripcion = valor
                                        "category" -> if (valor.isNotEmpty()) prog.categorias.add(valor)
                                    }
                                }
                            }

                            "programme" -> {
                                val prog = enPrograma
                                if (prog != null && !saltarPrograma) {
                                    val titulo = prog.titulo
                                    if (titulo.isNullOrBlank()) {
                                        descartados.add("programme de ${prog.canal} sin <title>")
                                    } else {
                                        programas.add(
                                            Programa(
                                                canal = prog.canal,
                                                inicio = prog.inicio,
                                                fin = prog.fin,
                                                titulo = titulo,
                                                subtitulo = prog.subtitulo?.takeIf { it.isNotBlank() },
                                                descripcion = prog.descripcion?.takeIf { it.isNotBlank() },
                                                categorias = prog.categorias.toList(),
                                                icono = prog.icono,
                                            )
                                        )
                                    }
                                }
                                enPrograma = null
                                saltarPrograma = false
                            }
                        }
                        if (nombre == "title" || nombre == "sub-title" || nombre == "desc" ||
                            nombre == "category" || nombre == "display-name"
                        ) {
                            elemento = null
                            texto.setLength(0)
                        }
                    }
                }
                evento = p.next()
            }
        } catch (e: Exception) {
            return ResultadoGuia(
                canales = emptyList(),
                programas = emptyList(),
                descartados = descartados,
                motivo = "No se pudo leer $origen como XMLTV: " + (e.message ?: e.javaClass.simpleName),
            )
        }

        val listaCanales = canales.map { (id, nombres) ->
            CanalGuia(id = id, nombres = nombres.toList(), icono = iconosDeCanal[id])
        }

        val motivo = when {
            listaCanales.isEmpty() && programas.isEmpty() ->
                "$origen no trae ni canales ni programas, o no es XMLTV"
            programas.size >= TOPE_PROGRAMAS ->
                "Se cortó en $TOPE_PROGRAMAS programas: la guía trae más y no cabe en memoria"
            else -> null
        }

        return ResultadoGuia(listaCanales, programas, descartados, motivo)
    }

    /** Programa mientras se lee, antes de saber si está completo. */
    private class ProgCrudo(
        val canal: String,
        val inicio: Long,
        val fin: Long?,
    ) {
        var titulo: String? = null
        var subtitulo: String? = null
        var descripcion: String? = null
        var icono: String? = null
        val categorias: MutableList<String> = mutableListOf()
    }
}

/**
 * Lo que dio la lectura de una guía.
 *
 * `descartados` no es un contador: son las razones, una por entrada, para que se
 * pueda enseñar qué guía está rota y por dónde.
 */
data class ResultadoGuia(
    val canales: List<CanalGuia>,
    val programas: List<Programa>,
    val descartados: List<String>,
    val motivo: String?,
) {
    val correcta: Boolean get() = (canales.isNotEmpty() || programas.isNotEmpty()) && motivo == null
}
