package com.ceroX.app.modules.houri.tracker

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * El cable que faltaba entre el lector y AniList/MyAnimeList.
 *
 * EL HUECO QUE TAPA
 * -----------------
 * Había dos piezas terminadas y probadas que no se hablaban: `TrackerClient`
 * sabía leer el progreso remoto y escribir el nuevo, y `ReaderActivity` sabía en
 * qué página y en qué capítulo iba el usuario. Entre las dos, nada. La app
 * sincronizaba si alguien tecleaba el id de la obra a mano en una pantalla
 * aparte, y eso no es sincronizar: es un formulario.
 *
 * QUÉ HACE Y EN QUÉ ORDEN
 * -----------------------
 *   1. Si no hay token guardado, **no hace nada y no molesta**. Sincronizar es
 *      opcional y el lector tiene que funcionar sin cuenta.
 *   2. Si el id de la obra en el tracker no se conoce, no se adivina. Se pide
 *      una vez y se guarda por obra. Suponerlo escribiría el progreso en la
 *      serie de otra persona.
 *   3. Antes de escribir, se lee el progreso remoto y se aplica `PlanDeTracker`
 *      —la misma regla del núcleo compartido, probada en JVM y en JS—: si el
 *      tracker va por delante, **no se envía nada**. Esa regla es la que impide
 *      que la app borre lo que el usuario marcó a mano.
 *   4. Se envía sólo el capítulo, y el estado «terminado» sólo si el capítulo es
 *      el último conocido.
 *
 * LO QUE NO HACE
 * --------------
 * No reintenta en bucle: un envío por capítulo terminado. No sincroniza en
 * segundo plano ni con la app cerrada. No toca la lista del usuario más allá del
 * capítulo que acaba de leer.
 */
class TrackerAutoSync(
    private val contexto: Context,
    private val cliente: TrackerClient = TrackerClient(),
    private val credenciales: TrackerCredentials = TrackerCredentials(contexto),
    private val mapa: MapaDeObras = MapaDeObras(contexto),
) {

    /** Qué pasó, para poder enseñarlo. Nunca se inventa un «sincronizado». */
    data class Resultado(
        val intentado: Boolean,
        val enviado: Boolean,
        val motivo: String,
        val tracker: Tracker? = null,
    )

    /**
     * Se llama cuando el usuario termina un capítulo (llega a la última página).
     *
     * Es `suspend` y se espera: quien llama decide si lo pinta o no, pero el
     * trabajo se hace en `Dispatchers.IO`.
     */
    suspend fun capituloTerminado(
        obra: String,
        capitulo: Int,
        totalCapitulos: Int = 0,
    ): Resultado = withContext(Dispatchers.IO) {
        if (capitulo <= 0) return@withContext Resultado(false, false, "capítulo desconocido")
        if (obra.isBlank()) return@withContext Resultado(false, false, "obra sin nombre")

        val tracker = TRACKER_PREFERIDO.firstOrNull { credenciales.token(it) != null }
            ?: return@withContext Resultado(false, false, "no hay token guardado; no se sincroniza")

        val token = credenciales.token(tracker)!!
        val idObra = mapa.idDe(obra, tracker)
            ?: return@withContext Resultado(
                false, false,
                "no se sabe el id de «$obra» en ${tracker.id}; se pide en Sincronización",
                tracker,
            )

        val local = ProgresoARemoto(
            idEnTracker = idObra,
            capitulo = capitulo,
            totalCapitulos = totalCapitulos,
            estado = PlanificadorSync.estadoAlTerminarCapitulo(capitulo, totalCapitulos),
        )

        // Se lee el remoto **antes** de escribir y la decisión la toma
        // `PlanificadorSync`, que es la pieza con reglas y la que tiene pruebas
        // propias (`PlanificadorSyncTest`). Aquí no se reimplementa nada: si el
        // tracker va por delante, no se envía. Esa es la diferencia entre
        // sincronizar y borrar el progreso de alguien.
        val remoto = runCatching { cliente.leerProgreso(tracker, token, idObra) }.getOrDefault(-1)
        if (remoto >= 0 && local.capitulo <= remoto) {
            val motivo = if (local.capitulo == remoto) {
                "ya estaba al día en el capítulo $remoto"
            } else {
                "el tracker ya va por el capítulo $remoto; el local por ${local.capitulo}"
            }
            return@withContext Resultado(true, false, "no se envía: $motivo", tracker)
        }

        // `sincronizar` vuelve a leer y planificar por su cuenta: la doble
        // lectura es a propósito. La de arriba sirve para no abrir una
        // escritura que se va a rechazar, y la de dentro es la que manda.
        val r = cliente.sincronizar(tracker = tracker, token = token, local = local)
        Resultado(true, r.exito, r.mensaje, tracker)
    }

    /** Se llama al salir del lector: es opcional y no bloquea el cierre. */
    fun enSegundoPlano(
        alcance: CoroutineScope,
        obra: String,
        capitulo: Int,
        totalCapitulos: Int = 0,
        alTerminar: (Resultado) -> Unit = {},
    ) {
        alcance.launch(SupervisorJob() + Dispatchers.IO) {
            val r = runCatching { capituloTerminado(obra, capitulo, totalCapitulos) }
                .getOrElse { Resultado(true, false, "fallo: ${it.javaClass.simpleName}") }
            withContext(Dispatchers.Main) { alTerminar(r) }
        }
    }

    companion object {
        /**
         * Orden de preferencia. AniList primero porque su API no exige registrar
         * una aplicación para leer el progreso, y MAL segundo.
         */
        val TRACKER_PREFERIDO: List<Tracker> = listOf(Tracker.ANILIST, Tracker.MYANIMELIST)
    }
}

/**
 * La relación entre el nombre de una obra en el lector y su id en cada tracker.
 *
 * POR QUÉ NO SE BUSCA POR NOMBRE AUTOMÁTICAMENTE
 * ---------------------------------------------
 * Buscar «One Piece» en AniList y quedarse con el primer resultado funciona
 * hasta que hay dos obras con el mismo nombre, un spin-off, o una versión en otro
 * idioma. Escribir el progreso en la serie equivocada es peor que no
 * sincronizar: el usuario no lo nota hasta que su lista está mal.
 *
 * Se guarda por obra y por tracker, en `SharedPreferences` normal porque **no es
 * un secreto**: el id de una serie es público. El token sí va cifrado, en
 * [TrackerCredentials].
 */
class MapaDeObras(contexto: Context) {

    private val prefs = contexto.getSharedPreferences("cerox_mapa_obras", Context.MODE_PRIVATE)

    fun idDe(obra: String, tracker: Tracker): Long? =
        prefs.getLong(clave(obra, tracker), -1L).takeIf { it > 0 }

    fun guardar(obra: String, tracker: Tracker, idEnTracker: Long) {
        if (idEnTracker <= 0) return
        prefs.edit().putLong(clave(obra, tracker), idEnTracker).apply()
    }

    fun borrar(obra: String, tracker: Tracker) {
        prefs.edit().remove(clave(obra, tracker)).apply()
    }

    /** Todas las obras mapeadas para un tracker. Para la pantalla de ajustes. */
    fun obras(tracker: Tracker): Map<String, Long> = prefs.all.entries
        .filter { it.key.startsWith("${tracker.id}|") && it.value is Long && (it.value as Long) > 0 }
        .associate { it.key.substringAfter('|') to it.value as Long }

    private fun clave(obra: String, tracker: Tracker) =
        "${tracker.id}|${obra.trim().lowercase()}"
}
