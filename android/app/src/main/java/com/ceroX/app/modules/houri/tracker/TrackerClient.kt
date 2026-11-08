package com.ceroX.app.modules.houri.tracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL

/**
 * El cliente que habla de verdad con AniList y MyAnimeList.
 *
 * LO QUE ESTÁ VERIFICADO Y LO QUE NO
 * ----------------------------------
 * Verificado aquí, contra la documentación pública (consultada el 2026-11-08):
 *
 *   - AniList: GraphQL en `https://graphql.anilist.co`, autenticación
 *     `Authorization: Bearer <token>`, mutación `SaveMediaListEntry` con
 *     `mediaId`, `progress`, `status`. Los estados válidos son CURRENT,
 *     PLANNING, COMPLETED, DROPPED, PAUSED, REPEATING. La consulta `Media`
 *     devuelve `mediaListEntry { progress }` para el usuario autenticado.
 *   - MyAnimeList: REST v2 en `https://api.myanimelist.net/v2`, token tipo
 *     Bearer, `GET /manga/{id}?fields=my_list_status` para leer y
 *     `PATCH /manga/{id}/my_list_status` con
 *     `num_chapters_read` / `status` para escribir.
 *
 * **NO verificado**: que las dos llamadas funcionen con un token real, porque en
 * este entorno no hay token de usuario ni dispositivo. Por eso cada método
 * devuelve un [SyncResultado] con el código HTTP y el cuerpo del error: si algo
 * de esto cambia, el usuario ve «HTTP 401» y no «algo falló». La app no dice
 * «sincronizado» hasta que el servidor contesta.
 *
 * TIMEOUTS
 * --------
 * 10 s de conexión y 15 s de lectura. Un tracker lento no puede congelar la
 * pantalla de lectura: la sincronización va en segundo plano y, si tarda, se
 * informa.
 */
class TrackerClient(
    private val anilistUrl: String = "https://graphql.anilist.co",
    private val malUrl: String = "https://api.myanimelist.net/v2",
) {

    /** Lee el progreso que el tracker tiene para una obra. -1 si no se pudo. */
    suspend fun leerProgreso(tracker: Tracker, token: String, idObra: Long): Int =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext -1
            try {
                when (tracker) {
                    Tracker.ANILIST -> leerAniList(token, idObra)
                    Tracker.MYANIMELIST -> leerMal(token, idObra)
                }
            } catch (e: Throwable) {
                -1
            }
        }

    /**
     * Sincroniza un progreso, con planificación previa.
     *
     * El orden importa y no es casual: **primero se lee** el remoto, luego se
     * decide, y solo entonces se escribe. Un cliente que escribe sin leer
     * retrocede el progreso de cualquiera que haya leído en otro dispositivo.
     */
    suspend fun sincronizar(
        tracker: Tracker,
        token: String,
        local: ProgresoARemoto,
    ): SyncResultado = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        if (token.isBlank()) {
            return@withContext SyncResultado(
                tracker, ResultadoSync.NO_CONFIGURADO,
                "falta el token de ${tracker.nombreVisible}; se consigue en ${tracker.urlAjustes}"
            )
        }

        val remoto = leerProgreso(tracker, token, local.idEnTracker)
        val (aEnviar, plan) = PlanificadorSync.planificar(tracker, local, remoto)
        if (aEnviar == null) {
            // El plan ya trae el motivo. Se conserva su tiempo total.
            return@withContext plan.copy(ms = System.currentTimeMillis() - t0)
        }

        val envio = try {
            when (tracker) {
                Tracker.ANILIST -> escribirAniList(token, aEnviar)
                Tracker.MYANIMELIST -> escribirMal(token, aEnviar)
            }
        } catch (e: java.io.IOException) {
            return@withContext SyncResultado(
                tracker, ResultadoSync.SIN_RED,
                "sin conexión con ${tracker.nombreVisible}: ${e.message ?: "error de red"}",
                remoto, System.currentTimeMillis() - t0,
            )
        } catch (e: Throwable) {
            return@withContext SyncResultado(
                tracker, ResultadoSync.ERROR_SERVIDOR,
                "${e.javaClass.simpleName}: ${e.message ?: "error inesperado"}",
                remoto, System.currentTimeMillis() - t0,
            )
        }

        envio.copy(ms = System.currentTimeMillis() - t0, progresoRemotoAntes = remoto)
    }

    // ── AniList (GraphQL) ────────────────────────────────────────────────────

    private fun leerAniList(token: String, idObra: Long): Int {
        val consulta = """
            query {
              Media(id: $idObra) {
                mediaListEntry { progress }
              }
            }
        """.trimIndent()
        val cuerpo = com.google.gson.JsonObject().apply { addProperty("query", consulta) }.toString()
        val respuesta = post(anilistUrl, token, cuerpo)
        if (respuesta.codigo !in 200..299) return -1
        return runCatching {
            JsonParser.parseString(respuesta.cuerpo).asJsonObject
                .getAsJsonObject("data")
                ?.getAsJsonObject("Media")
                ?.getAsJsonObject("mediaListEntry")
                ?.get("progress")?.asInt
        }.getOrNull() ?: -1
    }

    private fun escribirAniList(token: String, progreso: ProgresoARemoto): SyncResultado {
        val mutacion = """
            mutation {
              SaveMediaListEntry(
                mediaId: ${progreso.idEnTracker},
                progress: ${progreso.capitulo},
                status: ${progreso.estado.porDefecto}
              ) { id progress status }
            }
        """.trimIndent()
        val cuerpo = com.google.gson.JsonObject().apply { addProperty("query", mutacion) }.toString()
        val respuesta = post(anilistUrl, token, cuerpo)
        return interpretar(Tracker.ANILIST, respuesta)
    }

    // ── MyAnimeList (REST v2) ────────────────────────────────────────────────

    private fun leerMal(token: String, idObra: Long): Int {
        val url = URL("$malUrl/manga/$idObra?fields=my_list_status")
        val conexion = abrir(url, token, "GET")
        try {
            if (conexion.responseCode !in 200..299) return -1
            val texto = conexion.inputStream.bufferedReader().use { it.readText() }
            return runCatching {
                JsonParser.parseString(texto).asJsonObject
                    .getAsJsonObject("my_list_status")
                    ?.get("num_chapters_read")?.asInt
            }.getOrNull() ?: -1
        } finally {
            conexion.disconnect()
        }
    }

    private fun escribirMal(token: String, progreso: ProgresoARemoto): SyncResultado {
        val url = URL("$malUrl/manga/${progreso.idEnTracker}/my_list_status")
        val conexion = abrir(url, token, "PATCH")
        try {
            val cuerpo = "num_chapters_read=${progreso.capitulo}&status=${progreso.estado.porDefecto.lowercase()}"
            conexion.doOutput = true
            conexion.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conexion.outputStream.use { it.write(cuerpo.toByteArray()) }
            val codigo = conexion.responseCode
            val texto = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return interpretar(Tracker.MYANIMELIST, Respuesta(codigo, texto))
        } finally {
            conexion.disconnect()
        }
    }

    // ── Infraestructura ──────────────────────────────────────────────────────

    private data class Respuesta(val codigo: Int, val cuerpo: String)

    private fun abrir(url: URL, token: String, metodo: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = metodo
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "cero-X/1.0.0")
        }

    private fun post(urlTexto: String, token: String, cuerpo: String): Respuesta {
        val conexion = abrir(URL(urlTexto), token, "POST")
        try {
            conexion.doOutput = true
            conexion.setRequestProperty("Content-Type", "application/json")
            conexion.outputStream.use { it.write(cuerpo.toByteArray()) }
            val codigo = conexion.responseCode
            val texto = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Respuesta(codigo, texto)
        } finally {
            conexion.disconnect()
        }
    }

    /**
     * Traduce una respuesta a un resultado que diga la verdad.
     *
     * AniList devuelve **HTTP 200 con `errors`** cuando la mutación falla (token
     * caducado, id inexistente). Mirar solo el código de estado daría «OK» en un
     * fallo, que es exactamente el error que no se quiere repetir aquí.
     */
    private fun interpretar(tracker: Tracker, r: Respuesta): SyncResultado {
        val cuerpoCorto = r.cuerpo.take(300)
        if (tracker == Tracker.ANILIST) {
            val raiz = runCatching { JsonParser.parseString(r.cuerpo).asJsonObject }.getOrNull()
            val errores = raiz?.getAsJsonArray("errors")
            if (errores != null && errores.size() > 0) {
                val texto = runCatching {
                    errores[0].asJsonObject.get("message").asString
                }.getOrNull().orEmpty()
                return SyncResultado(
                    tracker,
                    if (r.codigo in 200..299) ResultadoSync.RECHAZADO else ResultadoSync.ERROR_SERVIDOR,
                    "AniList rechazó: ${texto.ifBlank { cuerpoCorto }} (HTTP ${r.codigo})",
                )
            }
            val guardado = runCatching {
                raiz?.getAsJsonObject("data")?.getAsJsonObject("SaveMediaListEntry")
            }.getOrNull()
            if (guardado == null && r.codigo in 200..299) {
                return SyncResultado(
                    tracker, ResultadoSync.RECHAZADO,
                    "AniList respondió 200 sin datos del guardado: $cuerpoCorto"
                )
            }
        }
        return when (r.codigo) {
            in 200..299 -> SyncResultado(tracker, ResultadoSync.OK, "sincronizado con ${tracker.nombreVisible}")
            401, 403 -> SyncResultado(
                tracker, ResultadoSync.RECHAZADO,
                "token rechazado por ${tracker.nombreVisible} (HTTP ${r.codigo}); caducó o no tiene permisos"
            )
            404 -> SyncResultado(
                tracker, ResultadoSync.RECHAZADO,
                "la obra no existe en ${tracker.nombreVisible} (HTTP 404); hay que vincular el id desde su web"
            )
            in 500..599 -> SyncResultado(
                tracker, ResultadoSync.ERROR_SERVIDOR,
                "${tracker.nombreVisible} falló (HTTP ${r.codigo}); se puede reintentar"
            )
            else -> SyncResultado(
                tracker, ResultadoSync.RECHAZADO,
                "HTTP ${r.codigo}: $cuerpoCorto"
            )
        }
    }
}
