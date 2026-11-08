package com.ceroX.app.modules.feed

/**
 * Perfiles de feed: varias configuraciones del inicio que se turnan.
 *
 * DE DÓNDE VIENE ESTO
 * -------------------
 * De dos issues abiertos en `komikku-app/komikku` desde hace meses:
 *
 *   #1314 «Multi-feed profiles»: en vez de seguir añadiendo fuentes, permitir
 *   varios perfiles de feed. Cada uno con su horario de actualización y su
 *   rotación, para intereses distintos: uno de manhua chino de acción, otro de
 *   seinen, otro de lo que sigues habitualmente.
 *
 *   #1033 «Feed classifier/computer»: ordenar las fuentes del feed por ranking —
 *   alfabético, por frecuencia de uso, por fecha de instalación, por volumen de
 *   novedades.
 *
 * Son la misma idea mirada desde dos lados: el feed no debería ser una sola
 * lista infinita, sino varias listas con criterio que se van alternando.
 *
 * EL PROBLEMA REAL QUE RESUELVE
 * -----------------------------
 * Con seis entradas y doscientas fuentes, el inicio se llena de lo que menos te
 * interesa porque la fuente que más publica siempre gana. Un perfil deja decir
 * «los lunes quiero manhua, el resto seinen» sin tener que desactivar nada.
 *
 * POR QUÉ LA ROTACIÓN ES POR TIEMPO Y NO AL AZAR
 * ----------------------------------------------
 * Rotar al azar entre perfiles hace que el inicio cambie sin motivo y el usuario
 * no encuentra nada dos veces. Rotar por tiempo es predecible: sabes que el lunes
 * toca manhua porque el lunes tocó manhua la semana pasada.
 */
class FeedProfile(
    val id: String,
    val nombre: String,
    /** Las entradas que pertenecen a este perfil, en orden de prioridad. */
    val entradas: List<String> = emptyList(),
    /** Cada cuántos días toca este perfil. 0 = solo cuando se elige a mano. */
    val cadaDias: Int = 0,
    /** Cómo ordenar las entradas dentro del perfil. */
    val ranking: Ranking = Ranking.PRIORIDAD,
) {

    /** Cómo se ordenan las fuentes dentro de un perfil. De #1033. */
    enum class Ranking(val etiqueta: String) {
        /** El orden en que el usuario las puso. Manda él. */
        PRIORIDAD("Orden que elegiste"),

        /** Las que más usas primero. */
        USO("Las que más usas"),

        /** Las que más novedades traen. Evita perfiles vacíos. */
        NOVEDADES("Las que más publican"),

        /** Las más nuevas primero: útil al empezar. */
        RECIENTES("Añadidas hace poco"),

        /** Alfabético: predecible y fácil de recorrer con la vista. */
        ALFABETICO("A la A-Z"),
    }

    /** Lo que hace falta saber de una entrada para poder ordenarla. */
    data class Metricas(
        val vecesUsada: Int = 0,
        val novedadesPorActualizacion: Float = 0f,
        val añadidaEn: Long = 0L,
    )

    /**
     * Ordena las entradas de este perfil según su ranking.
     *
     * `titulos` es necesario para el orden alfabético; si falta una entrada, se
     * queda al final en vez de desaparecer: perder entradas al ordenar es peor
     * que ordenarlas mal.
     */
    fun ordenar(
        titulos: Map<String, String>,
        metricas: Map<String, Metricas> = emptyMap(),
    ): List<String> = when (ranking) {
        Ranking.PRIORIDAD -> entradas

        Ranking.USO -> entradas.sortedByDescending { metricas[it]?.vecesUsada ?: 0 }

        Ranking.NOVEDADES -> entradas.sortedByDescending {
            metricas[it]?.novedadesPorActualizacion ?: 0f
        }

        Ranking.RECIENTES -> entradas.sortedByDescending { metricas[it]?.añadidaEn ?: 0L }

        Ranking.ALFABETICO -> entradas.sortedBy {
            titulos[it]?.lowercase() ?: "\uFFFF"   // sin título, al final
        }
    }

    /**
     * ¿Toca este perfil hoy?
     *
     * Se calcula por días desde una fecha base fija (el 1 de enero de 2026) para
     * que el reparto sea el mismo en todos los aparatos y no dependa de cuándo se
     * instaló la app. Si dependiera de la instalación, dos móviles del mismo
     * usuario rotarían a días distintos y la sincronización sería un caos.
     */
    fun tocaHoy(momento: Long = System.currentTimeMillis()): Boolean {
        if (cadaDias <= 0) return false
        val dia = (momento / 86_400_000L) - BASE_DIAS
        return (dia % cadaDias).toInt() == 0
    }

    /**
     * De todos los perfiles, cuál toca hoy.
     *
     * Si varios tocan el mismo día (por ejemplo uno cada 2 días y otro cada 4), se
     * queda el de periodo más largo: es el más específico y el que menos
     * oportunidades tiene.
     */
    companion object {
        /** Días desde epoch del 1 de enero de 2026. */
        private const val BASE_DIAS = 20_454L

        fun perfilDeHoy(
            perfiles: List<FeedProfile>,
            momento: Long = System.currentTimeMillis(),
        ): FeedProfile? = perfiles
            .filter { it.tocaHoy(momento) }
            .maxByOrNull { it.cadaDias }
    }
}
