package com.ceroX.app.modules.iptv

/**
 * De las opciones de un canal a lo que entiende un reproductor.
 *
 * Las listas traen `#EXTVLCOPT` y `#KODIPROP` con claves que VLC y Kodi entienden y
 * media3 no. Hay que traducirlas, y la traducción tiene que ser visible: este fichero
 * no toca Android, así que se puede probar en el JVM, que es donde se descubre si la
 * tabla de equivalencias está bien antes de que lo descubra el usuario con un canal
 * que no arranca.
 *
 * Lo que no se sabe traducir no se convierte en una cabecera HTTP por las buenas.
 * `network-caching=1000` no es una cabecera, y mandarla como si lo fuera es la clase
 * de invento que hace que un fallo de red sea imposible de diagnosticar. Las claves
 * desconocidas van a [Solicitud.sinTraducir] para que se puedan enseñar.
 */
object OpcionesReproduccion {

    /** Claves de #EXTVLCOPT que son el agente de usuario, escritas de las dos formas. */
    private val AGENTES = listOf("http-user-agent", "user-agent")

    /** Y las que son el referente, que aparece con cuatro grafías distintas. */
    private val REFERENTES = listOf("http-referrer", "http-referer", "referrer", "referer")

    /** Prefijo de las opciones de DRM que vienen de los #KODIPROP. */
    private val PREFIJO_DRM = "inputstream.adaptive."

    fun de(canal: Canal): Solicitud {
        val cabeceras = LinkedHashMap<String, String>()
        val drm = LinkedHashMap<String, String>()
        val sinTraducir = LinkedHashMap<String, String>()
        var agente: String? = null

        for ((clave, valor) in canal.opciones) {
            val k = clave.trim().lowercase()
            when {
                k in AGENTES -> if (valor.isNotBlank()) agente = valor

                k in REFERENTES -> if (valor.isNotBlank()) cabeceras["Referer"] = valor

                k.startsWith(PREFIJO_DRM) -> {
                    val nombre = k.removePrefix(PREFIJO_DRM)
                    if (nombre.isNotBlank() && valor.isNotBlank()) drm[nombre] = valor
                }

                else -> sinTraducir[clave] = valor
            }
        }

        return Solicitud(
            url = canal.url,
            esHls = esHls(canal.url),
            userAgent = agente,
            cabeceras = cabeceras.toMap(),
            drm = drm.toMap(),
            sinTraducir = sinTraducir.toMap(),
        )
    }

    /**
     * Si el flujo es HLS.
     *
     * Se mira la ruta de la URL, sin parámetros ni fragmento, y no el nombre del
     * canal: hay canales que se llaman "algo.m3u8" y sirven un .mp4, y al revés.
     * Que esto se equivoque tampoco es fatal —media3 reconoce HLS por el tipo de
     * contenido que responde el servidor—, pero acertar evita un primer intento
     * fallido en los flujos cuya URL no lleva extensión.
     */
    fun esHls(url: String): Boolean {
        val ruta = url.substringBefore('?').substringBefore('#')
        return ruta.lowercase().endsWith(".m3u8")
    }
}

/** Lo que hace falta para pedir un canal, ya traducido. */
data class Solicitud(
    val url: String,
    val esHls: Boolean,
    /** Va aparte y no en las cabeceras: el reproductor lo pone donde debe. */
    val userAgent: String?,
    val cabeceras: Map<String, String>,
    /** Claves de `inputstream.adaptive.*` sin el prefijo. */
    val drm: Map<String, String>,
    /** Opciones de la lista que no se saben traducir. Se enseñan, no se inventan. */
    val sinTraducir: Map<String, String>,
)
