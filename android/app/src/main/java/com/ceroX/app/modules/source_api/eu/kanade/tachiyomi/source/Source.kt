package eu.kanade.tachiyomi.source

/**
 * Contrato base de una fuente de contenido del ecosistema Mihon/Tachiyomi.
 *
 * POR QUÉ ESTE PAQUETE TIENE ESE NOMBRE
 * -------------------------------------
 * Una extensión de Mihon se compila como un APK aparte y declara una clase que
 * implementa ESTA interfaz, con este nombre exacto de paquete. Cuando la app
 * anfitriona carga ese APK, el ClassLoader de la extensión resuelve
 * `eu.kanade.tachiyomi.source.Source` y lo busca en el classpath del anfitrión.
 * Si el anfitrión no tiene esa interfaz —con ese nombre exacto, en ese paquete
 * exacto— la extensión no carga, y no hay forma de adaptarla sin recompilarla.
 *
 * Ese es todo el secreto de la compatibilidad universal de extensiones: copiar
 * el contrato con su nombre original. No es un intérprete genérico ni un motor
 * de plugins inventado. Lo tiene houri (fork de Komikku, fork de Mihon) y es lo
 * que cero-X no tenía: cero menciones de `eu.kanade` en sus 102 archivos.
 *
 * Las firmas de abajo siguen el contrato de Mihon v0.21.x. No se inventan
 * métodos: si un método no está en el contrato original, no está aquí, porque
 * añadirlo rompería las extensiones que no lo implementan.
 */
interface Source {

    /** Identificador numérico único de la fuente. En el ecosistema: el id del repo. */
    val id: Long

    /** Nombre visible de la fuente, el que ve el usuario. */
    val name: String

    /** Idioma de la fuente, en formato ISO 639-1 ("es", "en", "ja"). */
    val lang: String

    /** Versión del contrato que la fuente implementa. Se usa para detectar incompatibilidades. */
    val versionId: Long
        get() = 0L

    /** URL base del sitio de la fuente, para mostrarla y para construir peticiones. */
    val baseUrl: String
        get() = ""

    /**
     * Devuelve la URL de la portada de una serie.
     * Se deja con implementación por defecto porque muchas fuentes la resuelven
     * en el momento y no quieren exponerla como estado.
     */
    fun getCoverUrl(serieUrl: String): String? = null

    companion object {
        /** Marca de origen de las fuentes de la familia Tachiyomi/Mihon. */
        const val FAMILY: String = "eu.kanade.tachiyomi"

        /** Versión del contrato que implementa este archivo. */
        const val CONTRACT_VERSION: Int = 1
    }
}
