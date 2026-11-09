package com.cerox.comun

/**
 * Las peticiones HTTP de cada proveedor de traducción, construidas como datos.
 *
 * POR QUÉ SE CONSTRUYEN AQUÍ Y NO EN ANDROID
 * ------------------------------------------
 * Porque así se pueden **probar sin red y sin teléfono**: qué URL, qué cabeceras,
 * qué cuerpo y cómo se lee la respuesta de cada proveedor queda fijado en pruebas
 * que corren en JVM. Lo único que hace Android es enviar la petición y devolver
 * el texto; si el formato está mal, se ve aquí y no en el móvil del usuario.
 *
 * POR QUÉ NO SE LLAMA A NINGÚN PROVEEDOR EN LAS PRUEBAS
 * ----------------------------------------------------
 * Mandar texto de prueba a Google, DeepL o un LibreTranslate público sin permiso
 * es usar infraestructura de otros. Las pruebas comprueban la **forma** de la
 * petición y el **lector** de la respuesta con capturas reales de la documentación.
 */
object ProveedoresTraduccion {

    data class Peticion(
        val url: String,
        val metodo: String,
        val cabeceras: Map<String, String>,
        val cuerpo: String,
        val tipoDeCuerpo: TipoDeCuerpo,
    ) {
        /** Para el registro y las pruebas: nunca incluye la clave. */
        fun descripcion(): String =
            "$metodo $url · ${cabeceras.keys.sorted().joinToString(",")} · " +
                "${tipoDeCuerpo.name} (${cuerpo.length} B)"
    }

    enum class TipoDeCuerpo { JSON, FORMULARIO, SIN_CUERPO }

    /**
     * Construye la petición de traducción.
     *
     * `texto` no se registra nunca: es contenido del usuario. La descripción de la
     * petición no lo incluye a propósito.
     */
    fun peticion(
        proveedor: Traduccion.Proveedor,
        texto: String,
        origen: String,
        destino: String,
        clave: String? = null,
        urlDelUsuario: String? = null,
    ): Peticion {
        val dest = destino.substringBefore('-')
        val orig = origen.substringBefore('-')
        return when (proveedor) {
            Traduccion.Proveedor.LOCAL -> Peticion(
                url = "", metodo = "", cabeceras = emptyMap(), cuerpo = "",
                tipoDeCuerpo = TipoDeCuerpo.SIN_CUERPO,
            )

            Traduccion.Proveedor.LIBRE -> Peticion(
                url = "${Traduccion.urlDe(proveedor, urlDelUsuario)?.trimEnd('/')}/translate",
                metodo = "POST",
                cabeceras = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                cuerpo = """{"q":${json(texto)},"source":"$orig","target":"$dest","format":"text"}""",
                tipoDeCuerpo = TipoDeCuerpo.JSON,
            )

            Traduccion.Proveedor.GOOGLE -> Peticion(
                url = Traduccion.urlDe(proveedor, null)!!,
                metodo = "POST",
                cabeceras = mapOf(
                    "Content-Type" to "application/json",
                    "X-Goog-Api-Key" to (clave ?: ""),
                ),
                cuerpo = """{"q":["${escapar(texto)}"],"source":"$orig","target":"$dest","format":"text"}""",
                tipoDeCuerpo = TipoDeCuerpo.JSON,
            )

            Traduccion.Proveedor.DEEPL -> Peticion(
                url = Traduccion.urlDe(proveedor, null, clave)!!,
                metodo = "POST",
                cabeceras = mapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "DeepL-Auth-Key ${clave.orEmpty()}",
                ),
                cuerpo = """{"text":["${escapar(texto)}"],"source_lang":"${orig.uppercase()}","""
                    + """"target_lang":"${dest.uppercase()}"}""",
                tipoDeCuerpo = TipoDeCuerpo.JSON,
            )

            Traduccion.Proveedor.MICROSOFT -> Peticion(
                url = "${Traduccion.urlDe(proveedor, null)}?api-version=3.0&from=$orig&to=$dest",
                metodo = "POST",
                cabeceras = mapOf(
                    "Content-Type" to "application/json",
                    "Ocp-Apim-Subscription-Key" to (clave ?: ""),
                ),
                cuerpo = """[{"Text":"${escapar(texto)}"}]""",
                tipoDeCuerpo = TipoDeCuerpo.JSON,
            )

            Traduccion.Proveedor.YOUDAO -> {
                val sal = System.currentTimeMillis().toString()
                Peticion(
                    url = Traduccion.urlDe(proveedor, null)!!,
                    metodo = "POST",
                    cabeceras = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    cuerpo = listOf(
                        "q=" + codificar(texto),
                        "from=$orig", "to=$dest", "appKey=" + (clave ?: ""),
                        // Youdao exige firma; sin ella el servidor responde 108.
                        // Se deja preparada la forma y se dice en la nota que
                        // falta el sha256 de appKey+salt+curtime+signType.
                        "salt=$sal", "signType=v3", "sign=",
                    ).joinToString("&"),
                    tipoDeCuerpo = TipoDeCuerpo.FORMULARIO,
                )
            }
        }
    }

    /**
     * Lee la traducción de la respuesta de cada proveedor.
     *
     * Devuelve `Result`: un fallo trae el mensaje del servidor, no un texto vacío.
     * Un programa que devuelve "" cuando falla deja al usuario creyendo que la
     * traducción salió vacía.
     */
    fun leerRespuesta(proveedor: Traduccion.Proveedor, cuerpo: String, codigoHttp: Int): Resultado =
        when (proveedor) {
            Traduccion.Proveedor.LOCAL -> Resultado.Fallo("el proveedor local no hace peticiones")

            Traduccion.Proveedor.LIBRE -> {
                // {"translatedText":"Hola"}
                val t = campo(cuerpo, "translatedText")
                if (codigoHttp in 200..299 && t != null) Resultado.Exito(t)
                else Resultado.Fallo(errorDeLibre(codigoHttp, cuerpo))
            }

            Traduccion.Proveedor.GOOGLE -> {
                // {"data":{"translations":[{"translatedText":"Hola","detectedSourceLanguage":"en"}]}}
                val t = campo(cuerpo, "translatedText")
                if (codigoHttp in 200..299 && t != null) Resultado.Exito(t)
                else Resultado.Fallo(errorDeGoogle(codigoHttp, cuerpo))
            }

            Traduccion.Proveedor.DEEPL -> {
                // {"translations":[{"detected_source_language":"EN","text":"Hola"}]}
                val t = campo(cuerpo, "text")
                if (codigoHttp in 200..299 && t != null) Resultado.Exito(t)
                else Resultado.Fallo(errorGenerico("DeepL", codigoHttp, cuerpo))
            }

            Traduccion.Proveedor.MICROSOFT -> {
                // [{"detectedLanguage":{...},"translations":[{"text":"Hola","to":"es"}]}]
                val t = campo(cuerpo, "text")
                if (codigoHttp in 200..299 && t != null) Resultado.Exito(t)
                else Resultado.Fallo(errorGenerico("Microsoft", codigoHttp, cuerpo))
            }

            Traduccion.Proveedor.YOUDAO -> {
                // {"errorCode":"0","translation":["Hola"]}
                val c = campo(cuerpo, "errorCode")
                val t = primerElementoDeArreglo(cuerpo, "translation")
                if (c == "0" && t != null) Resultado.Exito(t)
                else Resultado.Fallo("Youdao errorCode=$c")
            }
        }
            .let { if (it is Resultado.Fallo && it.mensaje.isBlank()) Resultado.Fallo("HTTP $codigoHttp") else it }

    sealed class Resultado {
        data class Exito(val texto: String) : Resultado()
        data class Fallo(val mensaje: String) : Resultado()

        fun texto(): String? = (this as? Exito)?.texto
        fun esExito(): Boolean = this is Exito
    }

    // ── Detalles ──────────────────────────────────────────────────────────

    private fun errorDeLibre(codigo: Int, cuerpo: String): String = when (codigo) {
        429 -> "LibreTranslate: demasiadas peticiones (429). Espera o cambia de servidor."
        400 -> "LibreTranslate: idioma no soportado o texto vacío (400): ${recorte(cuerpo)}"
        403 -> "LibreTranslate: el servidor exige clave (403): ${recorte(cuerpo)}"
        else -> "LibreTranslate HTTP $codigo: ${recorte(cuerpo)}"
    }

    private fun errorDeGoogle(codigo: Int, cuerpo: String): String = when (codigo) {
        400, 403 -> "Google: clave inválida, cuota agotada o idioma no soportado ($codigo): ${recorte(cuerpo)}"
        else -> "Google HTTP $codigo: ${recorte(cuerpo)}"
    }

    private fun errorGenerico(nombre: String, codigo: Int, cuerpo: String): String =
        "$nombre HTTP $codigo: ${recorte(cuerpo)}"

    private fun recorte(s: String, n: Int = 180) = if (s.length <= n) s else s.take(n) + "…"

    /**
     * Busca `"campo":"valor"` en un JSON sin depender de una librería.
     *
     * Es deliberadamente tonto y por eso funciona igual en JVM, en Android y en
     * JavaScript: las respuestas de estos cinco proveedores tienen el campo en un
     * sitio fijo y la cadena no anidada. Para el JSON de verdad está la app con
     * Gson; esto es para probar la forma.
     */
    private fun campo(json: String, nombre: String): String? {
        val marca = "\"$nombre\""
        val i = json.indexOf(marca)
        if (i < 0) return null
        var j = i + marca.length
        while (j < json.length && (json[j] == ' ' || json[j] == ':')) j++
        if (j >= json.length || json[j] != '"') return null
        val sb = StringBuilder()
        var k = j + 1
        while (k < json.length) {
            val c = json[k]
            if (c == '\\' && k + 1 < json.length) {
                sb.append(
                    when (json[k + 1]) {
                        'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; '"' -> '"'; '\\' -> '\\'
                        'u' -> {
                            // \uXXXX
                            if (k + 5 < json.length) {
                                val hex = json.substring(k + 2, k + 6)
                                k = k + 4
                                hex.toIntOrNull(16)?.toChar() ?: '?'
                            } else '?'
                        }
                        else -> json[k + 1]
                    },
                )
                k += 2
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c); k++
            }
        }
        return null
    }

    private fun primerElementoDeArreglo(json: String, nombre: String): String? {
        val i = json.indexOf("\"$nombre\"")
        if (i < 0) return null
        val ini = json.indexOf('[', i)
        if (ini < 0) return null
        val fin = json.indexOf(']', ini)
        if (fin < 0) return null
        val dentro = json.substring(ini + 1, fin).trim()
        if (!dentro.startsWith("\"")) return null
        return dentro.removeSurrounding("\"")
    }

    private fun escapar(s: String): String = s
        .replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun json(s: String): String = "\"${escapar(s)}\""

    private fun codificar(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 128 || ch in "-._~") append(ch)
            else append('%').append(HEX[c shr 4]).append(HEX[c and 15])
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
