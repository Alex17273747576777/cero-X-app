package com.ceroX.app.modules.iptv

/**
 * Lectura de listas de canales M3U y M3U8.
 *
 * UNA LISTA DE CANALES NO ES UNA PLAYLIST HLS
 * -------------------------------------------
 * Las dos terminan en .m3u8 y las dos usan #EXTINF, pero no son lo mismo. Una lista
 * de canales tiene un #EXTINF por canal y detrás la URL de cada flujo; una playlist
 * HLS tiene un #EXTINF por *segmento* y directivas #EXT-X-, y describe un solo
 * flujo. Confundirlas es el error que hace que un reproductor enseñe setecientos
 * "canales" que son siete segundos de vídeo cada uno.
 *
 * Por eso [parsear] mira primero si hay directivas #EXT-X-: si las hay, devuelve
 * `esListaHls = true`, ningún canal y el motivo. No intenta adivinar.
 *
 * LO QUE DEVUELVE CUANDO ALGO FALLA
 * ---------------------------------
 * `saltadas` lleva una entrada por lo que no se pudo leer, con la línea y la razón.
 * `motivo` es null sólo si se leyó al menos un canal: una lista vacía no es un
 * éxito silencioso.
 */
object ListaM3U {

    /**
     * Atributos dentro de la cabecera de un #EXTINF.
     *
     * Con comillas y sin ellas: `tvg-id="la1.es"` es lo habitual, pero hay listas
     * generadas a mano que escriben `tvg-id=la1.es`, y un lector que sólo entiende
     * la forma entrecomillada se queda sin ese canal sin decir por qué. La rama
     * entrecomillada va primero para que una URL con signos iguales dentro no se
     * corte donde no debe.
     */
    private val ATRIBUTOS = Regex("([A-Za-z0-9_.-]+)=(\"[^\"]*\"|[^\\s,]+)")

    /** Nombre y valor de cada atributo, sin las comillas si las traía. */
    private fun atributos(cabecera: String): Map<String, String> =
        ATRIBUTOS.findAll(cabecera).associate {
            val crudo = it.groupValues[2]
            val valor = if (crudo.length >= 2 && crudo.startsWith("\"") && crudo.endsWith("\"")) {
                crudo.substring(1, crudo.length - 1)
            } else {
                crudo
            }
            it.groupValues[1].lowercase() to valor
        }

    fun parsear(texto: String, origen: String = "lista"): ResultadoM3U {
        val lineas = texto.split("\n")

        val hls = lineas.any { it.trim().startsWith("#EXT-X-") }
        if (hls) {
            val maestra = lineas.any { it.trim().startsWith("#EXT-X-STREAM-INF") }
            return ResultadoM3U(
                canales = emptyList(),
                saltadas = emptyList(),
                esListaHls = true,
                motivo = "$origen es una playlist HLS" + (if (maestra) " maestra" else "") +
                    ": lleva directivas #EXT-X-, y sus #EXTINF describen segmentos de " +
                    "un solo flujo, no canales. Se usa como la URL de un canal, no como " +
                    "origen de canales.",
            )
        }

        val canales = mutableListOf<Canal>()
        val saltadas = mutableListOf<String>()
        var grupoHeredado: String? = null
        var pendiente: Pendiente? = null

        for ((i, cruda) in lineas.withIndex()) {
            val n = i + 1
            val linea = cruda.trim()

            when {
                linea.isEmpty() -> Unit

                linea.startsWith("#EXTM3U") -> Unit

                linea.startsWith("#EXTGRP:") -> {
                    val grupo = linea.substringAfter(":").trim()
                    if (grupo.isEmpty()) {
                        saltadas.add("linea $n: #EXTGRP sin nombre detrás de los dos puntos")
                    } else {
                        grupoHeredado = grupo
                    }
                }

                linea.startsWith("#EXTINF:") -> {
                    val anterior = pendiente
                    if (anterior != null) {
                        saltadas.add(
                            "linea $n: el #EXTINF de la linea ${anterior.linea} se quedó sin URL detrás"
                        )
                    }
                    pendiente = leerExtinf(linea, n, grupoHeredado, saltadas)
                }

                linea.startsWith("#EXTVLCOPT:") || linea.startsWith("#KODIPROP:") -> {
                    val directiva = linea.substringBefore(":")
                    val cuerpo = linea.substringAfter(":")
                    val p = pendiente
                    if (p == null) {
                        saltadas.add("linea $n: $directiva antes de cualquier #EXTINF, no hay canal al que atarlo")
                        continue
                    }
                    val clave = cuerpo.substringBefore("=", "").trim()
                    if (clave.isEmpty()) {
                        saltadas.add("linea $n: $directiva sin clave=valor: $cuerpo")
                    } else {
                        p.opciones[clave] = cuerpo.substringAfter("=", "")
                    }
                }

                // Cualquier otra directiva o comentario: no aporta canales.
                linea.startsWith("#") -> Unit

                else -> {
                    val rechazo = urlAceptable(linea)
                    if (rechazo != null) {
                        saltadas.add("linea $n: $rechazo")
                        continue
                    }
                    val p = pendiente
                    if (p == null) {
                        // Lista pelada: una URL por línea, sin metadatos.
                        canales.add(
                            Canal(
                                titulo = tituloDesdeUrl(linea, n),
                                url = linea,
                                tvgId = null,
                                tvgNombre = null,
                                logo = null,
                                grupo = grupoHeredado,
                                duracionSegundos = -1,
                                opciones = emptyMap(),
                                linea = n,
                            )
                        )
                    } else {
                        canales.add(p.aCanal(linea))
                        pendiente = null
                    }
                }
            }
        }

        val sinCerrar = pendiente
        if (sinCerrar != null) {
            saltadas.add("linea ${sinCerrar.linea}: #EXTINF sin URL detrás, se acaba el fichero")
        }

        val motivo = if (canales.isNotEmpty()) {
            null
        } else if (saltadas.isNotEmpty()) {
            "No se leyó ningún canal de $origen. Primer problema: " + saltadas.first()
        } else {
            "$origen no trae nada: no hay ni cabeceras #EXTINF ni URLs"
        }

        return ResultadoM3U(canales, saltadas, esListaHls = false, motivo = motivo)
    }

    /**
     * Cabecera de #EXTINF: duración, atributos entre comillas y título tras la coma.
     *
     * La duración de un canal en directo es -1 por convención, y hay listas que
     * ponen cualquier otra cosa: si no es un entero se toma -1 y se anota.
     */
    private fun leerExtinf(
        linea: String,
        n: Int,
        grupoHeredado: String?,
        saltadas: MutableList<String>,
    ): Pendiente {
        val cuerpo = linea.substringAfter("#EXTINF:")
        val coma = cuerpo.indexOf(',')
        val cabecera = if (coma < 0) cuerpo else cuerpo.substring(0, coma)
        val tituloCrudo = if (coma < 0) "" else cuerpo.substring(coma + 1).trim()

        val duracionTexto = cabecera.trim().substringBefore(' ').substringBefore(',')
        val duracion = duracionTexto.toIntOrNull()
        if (duracion == null && duracionTexto.isNotEmpty()) {
            saltadas.add("linea $n: la duración '$duracionTexto' no es un entero, se toma -1 (directo)")
        }

        val attrs = atributos(cabecera)

        val titulo = when {
            tituloCrudo.isNotEmpty() -> tituloCrudo
            !attrs["tvg-name"].isNullOrBlank() -> attrs["tvg-name"] as String
            else -> {
                saltadas.add("linea $n: #EXTINF sin título ni tvg-name")
                "Canal sin título (línea $n)"
            }
        }

        return Pendiente(
            linea = n,
            titulo = titulo,
            tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
            tvgNombre = attrs["tvg-name"]?.takeIf { it.isNotBlank() },
            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            // Si group-title está presente manda, aunque venga vacío: un canal que
            // dice explícitamente que no tiene grupo no debería heredar el del
            // anterior. Sólo se hereda #EXTGRP cuando el atributo no aparece.
            grupo = if (attrs.containsKey("group-title")) {
                attrs["group-title"]?.takeIf { it.isNotBlank() }
            } else {
                grupoHeredado
            },
            duracionSegundos = duracion ?: -1,
        )
    }

    /** Una línea que no es directiva tiene que ser una URL o una ruta. */
    private fun urlAceptable(linea: String): String? = when {
        linea.isEmpty() -> "hay una línea vacía donde se esperaba una URL"
        linea.contains(' ') -> "'$linea' tiene espacios, no es una URL"
        !linea.contains("://") && !linea.startsWith("/") ->
            "'$linea' no es una URL (falta el esquema) ni una ruta absoluta"
        else -> null
    }

    /** Último tramo de la ruta, sin parámetros: es lo más parecido a un nombre. */
    private fun tituloDesdeUrl(url: String, n: Int): String {
        val sinParametros = url.substringBefore('?').substringBefore('#')
        val tramo = sinParametros.trimEnd('/').substringAfterLast('/')
        return if (tramo.isBlank() || tramo == sinParametros) "Canal sin título (línea $n)" else tramo
    }

    /** Grupos presentes, ordenados y sin repetidos. */
    fun grupos(canales: List<Canal>): List<String> =
        canales.mapNotNull { it.grupo }.distinct().sorted()

    /**
     * Filtra por título o grupo, sin distinguir mayúsculas.
     *
     * Con la cadena vacía devuelve todo: buscar nada no es buscar algo que no
     * existe, y devolver una lista vacía aquí sería mentir sobre el contenido.
     */
    fun buscar(canales: List<Canal>, texto: String): List<Canal> {
        val q = texto.trim().lowercase()
        if (q.isEmpty()) return canales
        return canales.filter {
            it.titulo.lowercase().contains(q) ||
                it.tvgNombre?.lowercase()?.contains(q) == true ||
                it.grupo?.lowercase()?.contains(q) == true
        }
    }

    /** Un #EXTINF leído, a la espera de su URL. */
    private class Pendiente(
        val linea: Int,
        val titulo: String,
        val tvgId: String?,
        val tvgNombre: String?,
        val logo: String?,
        val grupo: String?,
        val duracionSegundos: Int,
        val opciones: MutableMap<String, String> = LinkedHashMap(),
    ) {
        fun aCanal(url: String) = Canal(
            titulo = titulo,
            url = url,
            tvgId = tvgId,
            tvgNombre = tvgNombre,
            logo = logo,
            grupo = grupo,
            duracionSegundos = duracionSegundos,
            opciones = opciones.toMap(),
            linea = linea,
        )
    }
}

/** Un canal de una lista M3U, con lo que la lista decía de él. */
data class Canal(
    val titulo: String,
    val url: String,
    val tvgId: String?,
    val tvgNombre: String?,
    val logo: String?,
    val grupo: String?,
    val duracionSegundos: Int,
    /** #EXTVLCOPT y #KODIPROP: usuario, referente, tipo de licencia. Las pide el reproductor. */
    val opciones: Map<String, String>,
    /** Línea del fichero donde empieza, para poder señalar el problema. */
    val linea: Int,
)

/**
 * Lo que se leyó de una lista.
 *
 * `motivo` es null sólo si hay canales. `esListaHls` dice si el fichero era otra
 * cosa: una playlist de segmentos, que se reproduce pero no se lista.
 */
data class ResultadoM3U(
    val canales: List<Canal>,
    val saltadas: List<String>,
    val esListaHls: Boolean,
    val motivo: String?,
) {
    val correcta: Boolean get() = canales.isNotEmpty() && motivo == null
}
