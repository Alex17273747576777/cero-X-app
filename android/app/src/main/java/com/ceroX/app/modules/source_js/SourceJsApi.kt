package com.ceroX.app.modules.source_js

/**
 * Superficie JavaScript que exponemos a una extensión.
 *
 * DISEÑO
 * ------
 * Una extensión JS de cero-X no tiene acceso a Android, a Kotlin ni al sistema
 * de archivos. Recibe exactamente estos cuatro objetos y nada más:
 *
 *   http        peticiones de red, con cabeceras controladas por el anfitrión
 *   dom         consultas sobre el HTML ya descargado
 *   prefs       preferencias de la extensión, aisladas por id
 *   log         registro, con tope de líneas
 *
 * Ese «y nada más» es el punto. Una extensión que puede leer archivos o abrir
 * sockets propios ya no es una extensión: es una app con los permisos de cero-X.
 *
 * REFERENCIA DE DISEÑO
 * --------------------
 * Mangayomi (Apache-2.0) tiene esta misma idea en lib/eval/javascript/: separa
 * dom_selector, extractors, http y preferences en archivos distintos. Aquí se
 * copia la SEPARACIÓN, no el código: su implementación es Dart y la nuestra es
 * Kotlin sobre un motor JS de la JVM, así que compartir líneas es imposible.
 *
 * El contrato que ve la extensión, en JavaScript:
 *
 *   const res = await http.get(url, { Referer: base })
 *   const items = dom.select(res.body, 'div.manga-item')
 *   const title = dom.text(item, 'h3.title')
 *   await prefs.set('token', '...')
 *   log.info('encontrados ' + items.length)
 */
interface SourceJsApi {

    /** Cliente HTTP controlado por el anfitrión. */
    val http: Http

    /** Selector de DOM sobre HTML ya descargado. */
    val dom: Dom

    /** Preferencias aisladas por extensión. */
    val prefs: Prefs

    /** Registro con tope, para que una extensión no llene el disco de logs. */
    val log: Log

    interface Http {
        /** GET con cabeceras opcionales. Devuelve cuerpo y código. */
        fun get(url: String, headers: Map<String, String>): HttpResponse

        /** POST con cuerpo de texto. */
        fun post(url: String, body: String, headers: Map<String, String>): HttpResponse

        /** Solo cabeceras, para comprobar si un recurso sigue vivo. */
        fun head(url: String, headers: Map<String, String>): HttpResponse
    }

    interface Dom {
        /** Devuelve los elementos que casan con el selector, como HTML. */
        fun select(html: String, selector: String): List<String>

        /** Texto del primer elemento que casa con el selector. */
        fun text(html: String, selector: String): String?

        /** Valor de un atributo del primer elemento que casa. */
        fun attr(html: String, selector: String, attribute: String): String?

        /** Valor de un atributo de todos los elementos que casan. */
        fun attrAll(html: String, selector: String, attribute: String): List<String>
    }

    interface Prefs {
        fun get(key: String): String?
        fun set(key: String, value: String)
        fun remove(key: String)
        fun all(): Map<String, String>
    }

    interface Log {
        fun info(message: String)
        fun warn(message: String)
        fun error(message: String)
    }
}

/** Respuesta HTTP tal como la ve una extensión. */
data class HttpResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: String,
    val url: String,
) {
    val isSuccess: Boolean get() = code in 200..299
}

/**
 * Límites del entorno de ejecución.
 *
 * Un motor JS sin límites es una forma elegante de colgar el teléfono: un
 * `while(true)` en una extensión mala bloquea el hilo y el usuario cierra la
 * app culpando a cero-X. Estos topes son el equivalente al `ulimit` de un shell.
 */
object SourceJsLimits {
    /** Tiempo máximo de una sola llamada a la extensión. */
    const val CALL_TIMEOUT_MS = 15_000L

    /** Memoria máxima del montón del motor. */
    const val MAX_HEAP_BYTES = 32L * 1024 * 1024

    /** Peticiones de red por llamada. Una extensión que pide 500 páginas miente. */
    const val MAX_REQUESTS_PER_CALL = 20

    /** Tamaño máximo de una respuesta que aceptamos. */
    const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024

    /** Longitud máxima de una línea de registro. */
    const val MAX_LOG_LINE = 2_000
}
