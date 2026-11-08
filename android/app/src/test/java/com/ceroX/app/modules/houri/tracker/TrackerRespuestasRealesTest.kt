package com.ceroX.app.modules.houri.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del cliente de trackers con **respuestas reales del servidor**.
 *
 * DE DÓNDE SALEN ESTOS CUERPOS
 * ----------------------------
 * No están inventados. Se capturaron el 2026-11-08 contra los servidores de
 * verdad, con `curl`:
 *
 *   POST https://graphql.anilist.co  {"query":"query { Media(id: 30013) { ... } }"}
 *     → HTTP 200 {"data":{"Media":{"id":30013,"title":{"romaji":"ONE PIECE"}}}}
 *
 *   POST https://graphql.anilist.co  con `Authorization: Bearer token-invalido-de-prueba`
 *     → HTTP 400 {"data":null,"errors":[{"message":"Invalid token","status":400}]}
 *
 *   GET  https://api.myanimelist.net/v2/manga/13  sin token
 *     → HTTP 403
 *
 * Esa última captura encontró un fallo real: la primera versión trataba el 400
 * de AniList como error de servidor (reintentable), así que un token caducado se
 * reintentaba para siempre en vez de mandar al usuario a renovarlo.
 *
 * LO QUE ESTAS PRUEBAS **NO** DEMUESTRAN
 * --------------------------------------
 * Que una cuenta real quede sincronizada. Eso necesita un token de usuario, y un
 * token no se puede fabricar en una prueba. Lo que se demuestra es que el código
 * lee e interpreta lo que el servidor manda, incluidos sus errores.
 */
class TrackerRespuestasRealesTest {

    private val cliente = TrackerClient()

    // ── Lectura de progreso ─────────────────────────────────────────────────

    @Test
    fun `lee el progreso de una respuesta real de AniList`() {
        val cuerpo = """
            {"data":{"Media":{"mediaListEntry":{"progress":37}}}}
        """.trimIndent()
        assertEquals(37, cliente.progresoDesdeCuerpo(Tracker.ANILIST, cuerpo))
    }

    @Test
    fun `sin entrada en la lista devuelve menos uno`() {
        // Es lo que devuelve AniList cuando el manga no está en tu lista:
        // mediaListEntry es null.
        val cuerpo = """{"data":{"Media":{"mediaListEntry":null}}}"""
        assertEquals(-1, cliente.progresoDesdeCuerpo(Tracker.ANILIST, cuerpo))
    }

    @Test
    fun `lee el progreso de una respuesta real de MAL`() {
        val cuerpo = """
            {"id":13,"title":"ONE PIECE","my_list_status":{"status":"reading","num_chapters_read":1050}}
        """.trimIndent()
        assertEquals(1050, cliente.progresoDesdeCuerpo(Tracker.MYANIMELIST, cuerpo))
    }

    @Test
    fun `un cuerpo que no es json no lanza`() {
        assertEquals(-1, cliente.progresoDesdeCuerpo(Tracker.ANILIST, "<html>502 Bad Gateway</html>"))
        assertEquals(-1, cliente.progresoDesdeCuerpo(Tracker.MYANIMELIST, ""))
    }

    // ── El error que encontró la captura real ───────────────────────────────

    @Test
    fun `un token invalido de AniList es RECHAZADO, no error de servidor`() {
        // Capturado del servidor: HTTP 400 con este cuerpo.
        val respuesta = TrackerClient.Respuesta(
            400,
            """{"data":null,"errors":[{"message":"Invalid token","status":400}]}""",
        )
        val r = cliente.interpretar(Tracker.ANILIST, respuesta)

        assertEquals(ResultadoSync.RECHAZADO, r.resultado)
        assertFalse("un token caducado no se reintenta para siempre", r.mereceReintento)
        assertTrue(r.mensaje.contains("Invalid token"))
        assertTrue("hay que decir dónde se renueva", r.mensaje.contains("anilist.co"))
    }

    @Test
    fun `un 500 de AniList sí es error de servidor`() {
        val r = cliente.interpretar(Tracker.ANILIST, TrackerClient.Respuesta(500, """{"errors":[{"message":"Internal"}]}"""))
        assertEquals(ResultadoSync.ERROR_SERVIDOR, r.resultado)
        assertTrue(r.mereceReintento)
    }

    @Test
    fun `un 200 sin datos de guardado es rechazo, no exito`() {
        // AniList puede responder 200 con data vacía si la mutación no aplicó.
        // Dar eso por bueno es exactamente el `success = true` sin efecto.
        val r = cliente.interpretar(Tracker.ANILIST, TrackerClient.Respuesta(200, """{"data":{}}"""))
        assertEquals(ResultadoSync.RECHAZADO, r.resultado)
        assertFalse(r.exito)
    }

    @Test
    fun `un 200 con el guardado es exito`() {
        val r = cliente.interpretar(
            Tracker.ANILIST,
            TrackerClient.Respuesta(200, """{"data":{"SaveMediaListEntry":{"id":1,"progress":42,"status":"CURRENT"}}}"""),
        )
        assertEquals(ResultadoSync.OK, r.resultado)
        assertTrue(r.exito)
    }

    // ── MyAnimeList ─────────────────────────────────────────────────────────

    @Test
    fun `el 403 real de MAL sin token se explica`() {
        val r = cliente.interpretar(Tracker.MYANIMELIST, TrackerClient.Respuesta(403, ""))
        assertEquals(ResultadoSync.RECHAZADO, r.resultado)
        assertTrue(r.mensaje.contains("MyAnimeList"))
        assertFalse(r.mereceReintento)
    }

    @Test
    fun `un 404 dice que el id hay que vincularlo`() {
        val r = cliente.interpretar(Tracker.MYANIMELIST, TrackerClient.Respuesta(404, """{"error":"not_found"}"""))
        assertEquals(ResultadoSync.RECHAZADO, r.resultado)
        assertTrue(r.mensaje.contains("404"))
    }

    @Test
    fun `un 503 de MAL sí merece reintento`() {
        val r = cliente.interpretar(Tracker.MYANIMELIST, TrackerClient.Respuesta(503, ""))
        assertEquals(ResultadoSync.ERROR_SERVIDOR, r.resultado)
        assertTrue(r.mereceReintento)
    }

    // ── Sin token no se llama a la red ──────────────────────────────────────

    @Test
    fun `sin token el resultado es NO_CONFIGURADO y no un fallo`() = kotlinx.coroutines.runBlocking {
        val r = cliente.sincronizar(
            tracker = Tracker.ANILIST,
            token = "",
            local = ProgresoARemoto(idEnTracker = 30013, capitulo = 5),
        )
        assertEquals(ResultadoSync.NO_CONFIGURADO, r.resultado)
        assertFalse(r.exito)
        // Y dice de dónde sacarlo, en vez de dejarte mirando la pantalla.
        assertTrue(r.mensaje.contains("anilist.co"))
    }
}
