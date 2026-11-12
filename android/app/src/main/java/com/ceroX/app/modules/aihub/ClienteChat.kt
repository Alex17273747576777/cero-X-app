package com.ceroX.app.modules.aihub

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/** Configuración de un endpoint compatible con `/v1/chat/completions`. */
data class ConfiguracionChat(
    val baseUrl: String,
    val modelo: String,
    val apiKey: String = "",
)

data class MensajeChat(val rol: String, val contenido: String)

data class ResultadoChat(
    val texto: String,
    val modelo: String,
    val codigoHttp: Int,
    val milisegundos: Long,
)

/** La red se puede sustituir en pruebas: ningún test necesita una clave real. */
fun interface TransporteChat {
    fun enviar(url: String, apiKey: String, cuerpo: String): RespuestaHttpChat
}

data class RespuestaHttpChat(val codigo: Int, val cuerpo: String)

/** Validación separada para poder probar HTTPS, loopback y rutas sin Android. */
object EndpointChat {
    fun normalizar(base: String): String {
        val bruta = base.trim().trimEnd('/')
        require(bruta.isNotEmpty()) { "Escribe la URL del proveedor" }
        val uri = try { URI(bruta) } catch (e: Exception) {
            throw IllegalArgumentException("La URL del proveedor no es válida")
        }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("La URL no tiene servidor")
        val loopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
        require(uri.scheme == "https" || (uri.scheme == "http" && loopback)) {
            "Usa HTTPS; HTTP sólo se permite para Ollama en este mismo dispositivo"
        }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "La URL base no puede llevar credenciales, consulta ni fragmento"
        }
        val ruta = uri.path.orEmpty().trimEnd('/')
        return when {
            ruta.endsWith("/chat/completions") -> bruta
            ruta.endsWith("/v1") -> "$bruta/chat/completions"
            else -> "$bruta/v1/chat/completions"
        }
    }
}

/**
 * Cliente OpenAI-compatible real. No simula proveedores ni respuestas.
 * La llamada es bloqueante: la UI la ejecuta en Dispatchers.IO.
 */
class ClienteChat(
    private val transporte: TransporteChat = TransporteChat { url, key, cuerpo ->
        HttpTransporteChat.enviar(url, key, cuerpo)
    },
) {
    fun conversar(
        config: ConfiguracionChat,
        historial: List<MensajeChat>,
    ): ResultadoChat {
        val url = EndpointChat.normalizar(config.baseUrl)
        require(config.modelo.isNotBlank()) { "Escribe el nombre exacto del modelo" }
        require(historial.isNotEmpty()) { "Escribe un mensaje" }
        require(historial.last().rol == "user") { "El último mensaje debe ser del usuario" }
        val inicio = System.currentTimeMillis()
        val json = JsonObject().apply {
            addProperty("model", config.modelo.trim())
            addProperty("stream", false)
            add("messages", JsonArray().apply {
                historial.takeLast(20).forEach { mensaje ->
                    add(JsonObject().apply {
                        addProperty("role", mensaje.rol)
                        addProperty("content", mensaje.contenido)
                    })
                }
            })
        }.toString()
        val respuesta = transporte.enviar(url, config.apiKey, json)
        if (respuesta.codigo !in 200..299) {
            throw IOException("HTTP ${respuesta.codigo}: ${mensajeError(respuesta.cuerpo, config.apiKey)}")
        }
        val raiz = try { JsonParser.parseString(respuesta.cuerpo).asJsonObject } catch (e: Exception) {
            throw IOException("El proveedor devolvió JSON inválido")
        }
        val texto = try {
            raiz.getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message").get("content").asString
        } catch (e: Exception) {
            throw IOException("La respuesta no contiene choices[0].message.content")
        }
        require(texto.isNotBlank()) { "El proveedor devolvió una respuesta vacía" }
        return ResultadoChat(
            texto = texto,
            modelo = raiz.get("model")?.asString ?: config.modelo.trim(),
            codigoHttp = respuesta.codigo,
            milisegundos = System.currentTimeMillis() - inicio,
        )
    }

    private fun mensajeError(cuerpo: String, apiKey: String): String {
        val json = runCatching {
            JsonParser.parseString(cuerpo).asJsonObject.getAsJsonObject("error").get("message").asString
        }.getOrNull()
        val mensaje = json ?: cuerpo.ifBlank { "sin detalle" }
        return (if (apiKey.isBlank()) mensaje else mensaje.replace(apiKey, "[clave oculta]"))
            .take(500)
    }
}

private object HttpTransporteChat {
    fun enviar(url: String, apiKey: String, cuerpo: String): RespuestaHttpChat {
        val conexion = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "cero-X/1.0")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conexion.outputStream.use { it.write(cuerpo.toByteArray(StandardCharsets.UTF_8)) }
            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader(StandardCharsets.UTF_8)?.use { lector ->
                val salida = StringBuilder()
                val bloque = CharArray(16_384)
                while (salida.length <= 1_048_576) {
                    val n = lector.read(bloque)
                    if (n < 0) break
                    salida.append(bloque, 0, n)
                }
                require(salida.length <= 1_048_576) { "La respuesta supera 1 MiB" }
                salida.toString()
            }.orEmpty()
            return RespuestaHttpChat(codigo, texto)
        } finally {
            conexion.disconnect()
        }
    }
}
