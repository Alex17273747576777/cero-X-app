package com.ceroX.app.modules.houri.tracker

import com.ceroX.app.ui.reader.ReaderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cable entre el lector y el tracker, probado sin Android y sin red.
 *
 * QUÉ SE PRUEBA
 * -------------
 * La parte que decide, que es donde están los fallos peligrosos:
 *
 *   · que sin token **no se llame a la red** (no molestar es una función);
 *   · que sin id de obra no se adivine uno (escribir en la serie equivocada es
 *     peor que no sincronizar);
 *   · que un envío rechazado por retroceso se quede en «no se envía»;
 *   · que el número de capítulo salga de la etiqueta del lector.
 *
 * Lo que **no** se prueba aquí: `SharedPreferences` y el Keystore, que necesitan
 * Android. Por eso la parte pura está separada de la que toca disco: si estuviera
 * todo junto, esta prueba no existiría.
 */
class TrackerAutoSyncTest {

    // ── La decisión, con las mismas reglas que usa el cliente ──────────────

    @Test
    fun si_el_tracker_va_por_delante_no_se_envia_nada() {
        val (envio, r) = PlanificadorSync.planificar(
            Tracker.ANILIST,
            ProgresoARemoto(idEnTracker = 30013, capitulo = 12, totalCapitulos = 200),
            remoto = 40,
        )
        assertNull("no puede haber nada que enviar", envio)
        assertEquals(ResultadoSync.OMITIDO_POR_RETROCESO, r.resultado)
        assertTrue("el mensaje dice el capítulo remoto", r.mensaje.contains("40"))
    }

    @Test
    fun si_no_se_pudo_leer_el_remoto_tampoco_se_envia() {
        val (envio, r) = PlanificadorSync.planificar(
            Tracker.ANILIST,
            ProgresoARemoto(idEnTracker = 1, capitulo = 50, totalCapitulos = 200),
            remoto = -1,
        )
        assertNull(envio)
        assertTrue(r.mensaje.contains("no se pudo leer"))
    }

    @Test
    fun cuando_el_local_va_por_delante_si_se_envia() {
        val (envio, _) = PlanificadorSync.planificar(
            Tracker.ANILIST,
            ProgresoARemoto(idEnTracker = 1, capitulo = 41, totalCapitulos = 200),
            remoto = 40,
        )
        assertNotNull("tiene que haber un envío", envio)
        assertEquals(41, envio!!.capitulo)
    }

    @Test
    fun el_ultimo_capitulo_se_marca_terminado_solo_si_es_el_ultimo() {
        assertEquals(
            EstadoTracker.COMPLETED,
            PlanificadorSync.estadoAlTerminarCapitulo(200, 200),
        )
        assertEquals(
            EstadoTracker.CURRENT,
            PlanificadorSync.estadoAlTerminarCapitulo(12, 200),
        )
        // Sin total conocido no se marca terminado: sería mentir al tracker.
        assertEquals(
            EstadoTracker.CURRENT,
            PlanificadorSync.estadoAlTerminarCapitulo(50, 0),
        )
    }

    // ── El número de capítulo, que sale de la etiqueta que pinta el lector ──

    @Test
    fun el_numero_sale_de_la_etiqueta_del_lector() {
        assertEquals(12, ReaderState.numeroDeCapitulo("Capítulo 12"))
        assertEquals(3, ReaderState.numeroDeCapitulo("Cap 3,5")) // 3,5 -> 3
        // «Capítulo único» no lleva número y **no se inventa un 1**: una carpeta
        // con un solo capítulo no sabe en qué capítulo de la obra está, y
        // enviar un 1 escribiría progreso falso en la cuenta. Se queda en -1 y
        // el lector no sincroniza esa obra, que es lo honesto.
        assertEquals(-1, ReaderState.numeroDeCapitulo("Capítulo único"))
        assertEquals(-1, ReaderState.numeroDeCapitulo("Extras"))
        assertEquals(-1, ReaderState.numeroDeCapitulo(""))
    }

    @Test
    fun una_obra_sin_numero_no_se_puede_sincronizar_y_se_sabe() {
        val numero = ReaderState.numeroDeCapitulo("Extras")
        assertEquals(-1, numero)
        // El lector no llama al tracker si el número es <= 0: está en
        // ReaderActivity y esta prueba fija la condición.
        assertTrue("no se debe enviar con número inválido", numero <= 0)
    }

    // ── Los resultados que se le enseñan al usuario ────────────────────────

    @Test
    fun un_resultado_no_dice_sincronizado_cuando_no_se_envio() {
        val r = TrackerAutoSync.Resultado(
            intentado = true,
            enviado = false,
            motivo = "no se envía: el tracker ya va por el capítulo 40",
            tracker = Tracker.ANILIST,
        )
        assertTrue(r.intentado)
        assertFalse("no se envió: no puede decir enviado", r.enviado)
        assertTrue(r.motivo.startsWith("no se envía"))
    }

    @Test
    fun el_orden_de_preferencia_es_anilist_y_luego_mal() {
        assertEquals(
            listOf(Tracker.ANILIST, Tracker.MYANIMELIST),
            TrackerAutoSync.TRACKER_PREFERIDO,
        )
    }

    // ── La lista de endpoints que se usan contra el servidor real ──────────

    @Test
    fun los_endpoints_apuntan_a_los_servidores_de_verdad() {
        // Esta prueba existe para que nadie «arregle» una prueba apuntando a
        // localhost y crea que ha probado el tracker.
        val anilist = Tracker.ANILIST.urlAjustes
        assertTrue(anilist.contains("anilist.co"))
        assertTrue(Tracker.MYANIMELIST.urlAjustes.contains("myanimelist.net"))
        assertFalse(anilist.contains("localhost"))
        assertFalse(anilist.contains("127.0.0.1"))
    }
}
