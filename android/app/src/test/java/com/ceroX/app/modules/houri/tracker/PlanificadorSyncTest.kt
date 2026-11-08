package com.ceroX.app.modules.houri.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de las reglas del tracker, sin red y sin tokens.
 *
 * El bug que esto evita no es teórico: un tracker que sube el capítulo 12 cuando
 * tu cuenta tiene el 40 borra lo que marcaste a mano. Y un `success = true` sin
 * petición es exactamente lo que hacía `UniversalTracker` antes.
 */
class PlanificadorSyncTest {

    private fun local(cap: Int, total: Int = 0, estado: EstadoTracker = EstadoTracker.CURRENT) =
        ProgresoARemoto(idEnTracker = 30013, capitulo = cap, totalCapitulos = total, estado = estado)

    // ── Retroceso ───────────────────────────────────────────────────────────

    @Test
    fun `no se envía un progreso menor que el remoto`() {
        val (envio, r) = PlanificadorSync.planificar(Tracker.ANILIST, local(12), remoto = 40)
        assertNull(envio)
        assertEquals(ResultadoSync.OMITIDO_POR_RETROCESO, r.resultado)
        assertFalse(r.exito)  // omitido no es éxito
        assertTrue(r.mensaje.contains("40"))
    }

    @Test
    fun `un progreso igual al remoto no gasta una petición`() {
        val (envio, r) = PlanificadorSync.planificar(Tracker.ANILIST, local(40), remoto = 40)
        assertNull(envio)
        assertEquals(ResultadoSync.OK, r.resultado)
        assertTrue(r.exito)
        assertTrue(r.mensaje.contains("al día"))
    }

    @Test
    fun `un progreso mayor sí se envía`() {
        val (envio, r) = PlanificadorSync.planificar(Tracker.ANILIST, local(41), remoto = 40)
        assertEquals(41, envio!!.capitulo)
        assertEquals(ResultadoSync.OK, r.resultado)
    }

    @Test
    fun `si no se pudo leer el remoto no se envía nada`() {
        // Sin saber el remoto no se puede evitar el retroceso, y retroceder el
        // progreso de alguien es peor que no sincronizar.
        val (envio, r) = PlanificadorSync.planificar(Tracker.ANILIST, local(41), remoto = -1)
        assertNull(envio)
        assertEquals(ResultadoSync.ERROR_SERVIDOR, r.resultado)
        assertTrue(r.mereceReintento)
    }

    @Test
    fun `un remoto en cero deja pasar el progreso local`() {
        val (envio, _) = PlanificadorSync.planificar(Tracker.ANILIST, local(1), remoto = 0)
        assertEquals(1, envio!!.capitulo)
    }

    // ── COMPLETED ───────────────────────────────────────────────────────────

    @Test
    fun `no se marca terminado si el progreso no llega al total`() {
        val (envio, r) = PlanificadorSync.planificar(
            Tracker.MYANIMELIST, local(12, total = 200, estado = EstadoTracker.COMPLETED), remoto = 10
        )
        assertNull(envio)
        assertEquals(ResultadoSync.OMITIDO_POR_DATOS_INSUFICIENTES, r.resultado)
        assertTrue(r.mensaje.contains("200"))
    }

    @Test
    fun `no se marca terminado si no se conoce el total`() {
        val (envio, r) = PlanificadorSync.planificar(
            Tracker.ANILIST, local(50, total = 0, estado = EstadoTracker.COMPLETED), remoto = 49
        )
        assertNull(envio)
        assertEquals(ResultadoSync.OMITIDO_POR_DATOS_INSUFICIENTES, r.resultado)
    }

    @Test
    fun `se marca terminado solo si el progreso es el total`() {
        val (envio, r) = PlanificadorSync.planificar(
            Tracker.ANILIST, local(200, total = 200, estado = EstadoTracker.COMPLETED), remoto = 199
        )
        assertEquals(EstadoTracker.COMPLETED, envio!!.estado)
        assertEquals(ResultadoSync.OK, r.resultado)
    }

    @Test
    fun `el estado al terminar un capitulo del medio sigue siendo CURRENT`() {
        assertEquals(EstadoTracker.CURRENT, PlanificadorSync.estadoAlTerminarCapitulo(12, 200))
    }

    @Test
    fun `el estado al terminar el ultimo es COMPLETED`() {
        assertEquals(EstadoTracker.COMPLETED, PlanificadorSync.estadoAlTerminarCapitulo(200, 200))
    }

    @Test
    fun `sin total conocido el estado es CURRENT, no COMPLETED`() {
        assertEquals(EstadoTracker.CURRENT, PlanificadorSync.estadoAlTerminarCapitulo(50, 0))
    }

    // ── Lo que se manda ─────────────────────────────────────────────────────

    @Test
    fun `la peticion no lleva titulo, ni fuente, ni url`() {
        // Se comprueba por reflexión: si alguien añade un campo con estos
        // nombres, la prueba falla antes de que el dato salga del teléfono.
        val prohibidos = listOf("titulo", "title", "fuente", "source", "url", "path", "ruta", "token")
        val campos = ProgresoARemoto::class.java.declaredFields.map { it.name.lowercase() }
        val encontrados = prohibidos.filter { p -> campos.any { it.contains(p) } }
        assertEquals("campos que no deberían estar: $encontrados", emptyList<String>(), encontrados)
    }

    @Test
    fun `progreso negativo es un error`() {
        try {
            ProgresoARemoto(idEnTracker = 1, capitulo = -1)
            throw AssertionError("debería haber lanzado")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("negativo"))
        }
    }

    // ── Tracker ─────────────────────────────────────────────────────────────

    @Test
    fun `los dos trackers se pueden buscar por id sin distinguir mayusculas`() {
        assertEquals(Tracker.ANILIST, Tracker.porId("AniList"))
        assertEquals(Tracker.MYANIMELIST, Tracker.porId("myanimelist"))
        assertNull(Tracker.porId("kitsu"))
    }

    @Test
    fun `los dos trackers dicen donde sacar el token`() {
        // Sin URL de ajustes, «falta el token» es un callejón sin salida.
        Tracker.entries.forEach {
            assertTrue(it.urlAjustes.startsWith("https://"))
            assertTrue(it.tokenPropio)
        }
    }

    @Test
    fun `los estados que se envían son los que aceptan AniList y MAL`() {
        val permitidos = setOf("CURRENT", "COMPLETED", "REPEATING", "PAUSED", "DROPPED", "PLANNING")
        EstadoTracker.entries.forEach { assertTrue(it.name in permitidos) }
    }
}
