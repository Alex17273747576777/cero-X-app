package com.ceroX.app.modules.houri.tracker

/**
 * El modelo de un tracker que **sí se sincroniza**, no uno que dice que sí.
 *
 * EL PROBLEMA QUE HABÍA
 * --------------------
 * `UniversalTracker` tenía seis plataformas «habilitadas» y su `syncProgress`
 * devolvía `success = true` sin hacer una sola petición de red. Ese es
 * exactamente el patrón de las 14 mentiras que ya se corrigieron una vez: un
 * `true` que no significa nada. Aquí `success` se gana.
 *
 * LAS TRES REGLAS QUE HACEN QUE ESTO NO ROMPA NADA
 * ------------------------------------------------
 *  1. **Nunca se retrocede el progreso.** Si el usuario está en el capítulo 40
 *     en AniList y la app quiere subir 12 (porque releyó), subir 12 sería un
 *     retroceso. La regla no es un `if` de cortesía: es lo que evita que el
 *     tracker pierda lo que el usuario ya marcó a mano. Se sube el máximo.
 *  2. **`COMPLETED` exige que el progreso sea el total.** Marcar como terminado
 *     un manga de 200 capítulos cuando vas por el 12 es un dato corrupto en la
 *     cuenta del usuario, y no se arregla solo. Si no hay total conocido, no se
 *     marca como terminado: se deja `CURRENT` y se dice por qué.
 *  3. **Sin token no hay sincronización y se dice.** No hay un «modo demo» que
 *     devuelva éxito: devuelve `NO_CONFIGURADO` con el motivo.
 *
 * LO QUE SE ENVÍA Y LO QUE NO (privacidad)
 * ----------------------------------------
 * Se envía lo mínimo que el tracker necesita: id de la obra, capítulo y estado.
 * **No** se envía el título del capítulo, ni la fuente, ni la URL, ni la marca
 * de tiempo de lectura. Aquí eso no es una promesa: `ProgresoARemoto` no tiene
 * campos donde meterlos. Hay una prueba que falla si alguien los añade.
 */
enum class Tracker(
    val id: String,
    val nombreVisible: String,
    /** Dónde se pega el token. Se muestra en la pantalla de ajustes. */
    val urlAjustes: String,
    /** Si el usuario puede darle un token propio. Los dos lo permiten. */
    val tokenPropio: Boolean = true,
) {
    ANILIST(
        id = "anilist",
        nombreVisible = "AniList",
        urlAjustes = "https://anilist.co/settings/developer",
    ),
    MYANIMELIST(
        id = "myanimelist",
        nombreVisible = "MyAnimeList",
        urlAjustes = "https://myanimelist.net/apiconfig",
    );

    companion object {
        fun porId(id: String): Tracker? = entries.firstOrNull { it.id == id.lowercase() }
    }
}

/**
 * Lo único que se manda. Tres campos y el estado.
 *
 * Si algún día hace falta más, se añade aquí y la prueba de privacidad lo
 * obliga a justificarse.
 */
data class ProgresoARemoto(
    /** Id de la obra EN EL TRACKER, no en la fuente. Son cosas distintas. */
    val idEnTracker: Long,
    val capitulo: Int,
    val totalCapitulos: Int = 0,
    val estado: EstadoTracker = EstadoTracker.CURRENT,
) {
    init {
        require(capitulo >= 0) { "capítulo negativo" }
        require(totalCapitulos >= 0) { "total negativo" }
    }
}

/** Los estados de Mihon/Tachiyomi, que son los que ambos trackers entienden. */
enum class EstadoTracker(val porDefecto: String) {
    CURRENT("CURRENT"),
    COMPLETED("COMPLETED"),
    REPEATING("REPEATING"),
    PAUSED("PAUSED"),
    DROPPED("DROPPED"),
    PLANNING("PLANNING"),
}

/** Cómo acabó un intento de sincronizar. Cada valor dice algo distinto. */
enum class ResultadoSync {
    /** Se habló con el tracker y aceptó. */
    OK,

    /** Se habló con el tracker y rechazó: token mal, id mal, etc. */
    RECHAZADO,

    /** No se habló: no hay token. */
    NO_CONFIGURADO,

    /** No se habló: no hay red. */
    SIN_RED,

    /** Se habló y el servidor falló (5xx, timeout). Merece reintento. */
    ERROR_SERVIDOR,

    /**
     * No se envió nada porque habría sido un retroceso.
     * NO es un fallo: es la app protegiendo el dato del usuario.
     */
    OMITIDO_POR_RETROCESO,

    /** No se envió porque el estado pedido no era seguro con los datos que hay. */
    OMITIDO_POR_DATOS_INSUFICIENTES,
}

/** El resultado completo, con lo justo para pintarlo en la pantalla. */
data class SyncResultado(
    val tracker: Tracker,
    val resultado: ResultadoSync,
    val mensaje: String,
    /** Lo que el tracker tenía antes, si se llegó a consultar. -1 = no se sabe. */
    val progresoRemotoAntes: Int = -1,
    val ms: Long = 0,
) {
    val exito: Boolean get() = resultado == ResultadoSync.OK
    val mereceReintento: Boolean
        get() = resultado == ResultadoSync.ERROR_SERVIDOR || resultado == ResultadoSync.SIN_RED
}

/**
 * Decide qué hay que mandar, sin red y sin Android.
 *
 * Está separado del cliente HTTP porque es la mitad que tiene reglas, y las
 * reglas se pueden probar todas sin un servidor. Además, si mañana AniList
 * cambia su API, esta clase no cambia.
 */
object PlanificadorSync {

    /**
     * Calcula el envío para un progreso local contra lo que el tracker tiene.
     *
     * @param remoto capítulo que el tracker ya tiene; -1 si no se pudo consultar
     *   (en ese caso no se envía, porque sin saber el remoto no se puede evitar
     *   el retroceso, y retroceder el progreso de alguien es peor que no
     *   sincronizar).
     */
    fun planificar(
        tracker: Tracker,
        local: ProgresoARemoto,
        remoto: Int,
    ): Pair<ProgresoARemoto?, SyncResultado> {
        val t0 = System.currentTimeMillis()

        if (remoto < 0) {
            return null to SyncResultado(
                tracker, ResultadoSync.ERROR_SERVIDOR,
                "no se pudo leer el progreso remoto; no se envía nada para no retrocederlo",
                remoto, System.currentTimeMillis() - t0,
            )
        }

        if (local.capitulo < remoto) {
            return null to SyncResultado(
                tracker, ResultadoSync.OMITIDO_POR_RETROCESO,
                "el tracker ya tiene el capítulo $remoto; el local va por ${local.capitulo}",
                remoto, System.currentTimeMillis() - t0,
            )
        }

        // COMPLETED con progreso por debajo del total es un dato corrupto.
        val estado = if (local.estado == EstadoTracker.COMPLETED) {
            when {
                local.totalCapitulos <= 0 -> {
                    return null to SyncResultado(
                        tracker, ResultadoSync.OMITIDO_POR_DATOS_INSUFICIENTES,
                        "no se conoce el total de capítulos; no se marca como terminado",
                        remoto, System.currentTimeMillis() - t0,
                    )
                }
                local.capitulo < local.totalCapitulos -> {
                    return null to SyncResultado(
                        tracker, ResultadoSync.OMITIDO_POR_DATOS_INSUFICIENTES,
                        "vas por el ${local.capitulo} de ${local.totalCapitulos}; " +
                            "marcarlo terminado sería mentir en tu cuenta",
                        remoto, System.currentTimeMillis() - t0,
                    )
                }
                else -> EstadoTracker.COMPLETED
            }
        } else local.estado

        // Igual que el remoto y ya estaba en CURRENT: no hay nada que hacer y no
        // se gasta una petición. Se informa como OK sin llamar a la red.
        if (local.capitulo == remoto && estado == EstadoTracker.CURRENT) {
            return null to SyncResultado(
                tracker, ResultadoSync.OK,
                "ya estaba al día en el capítulo $remoto",
                remoto, System.currentTimeMillis() - t0,
            )
        }

        return local.copy(estado = estado) to SyncResultado(
            tracker, ResultadoSync.OK,
            "se envía el capítulo ${local.capitulo}" + if (remoto > 0) " (el tracker tenía $remoto)" else "",
            remoto, System.currentTimeMillis() - t0,
        )
    }

    /**
     * El estado que hay que mandar cuando el usuario termina un capítulo.
     *
     * Es la traducción de «llegué a la última página» a estado de tracker, y
     * tiene la misma regla que arriba: terminado solo si de verdad lo es.
     */
    fun estadoAlTerminarCapitulo(capituloLeido: Int, totalCapitulos: Int): EstadoTracker = when {
        totalCapitulos <= 0 -> EstadoTracker.CURRENT
        capituloLeido >= totalCapitulos -> EstadoTracker.COMPLETED
        else -> EstadoTracker.CURRENT
    }
}
