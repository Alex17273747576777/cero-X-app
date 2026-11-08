package com.ceroX.app.ui.reader

/**
 * El estado de un lector: en qué página estás y hacia dónde se pasa.
 *
 * POR QUÉ ESTO ES UNA CLASE SIN ANDROID
 * -------------------------------------
 * Es la única parte del lector que se puede probar sin un teléfono, y la que de
 * verdad decide cosas: el sentido de las flechas, dónde acaba un capítulo y
 * cuándo hay que pedir el siguiente. Toda la lógica de navegación vive aquí; la
 * Activity solo pinta y llama. Un lector cuyo estado está dentro de la Activity
 * no se puede probar, y es exactamente el motivo por el que los lectores de
 * manga acaban con 40 bugs de «la última página salta dos veces».
 *
 * LOS TRES MODOS, Y POR QUÉ SON TRES
 * ----------------------------------
 *  - `LTR`: página a la derecha (webtoon occidental, novela, cómic europeo).
 *  - `RTL`: página a la izquierda (manga japonés: se pasa con el dedo hacia la
 *    derecha para avanzar). Confundir esto es el bug clásico.
 *  - `WEBTOON`: desplazamiento continuo; no hay «página anterior», hay scroll.
 *    Tratarlo como RTL hace que el capítulo abra por el final.
 */
class ReaderState(
    val total: Int,
    inicial: Int = 0,
    val modo: Modo = Modo.LTR,
) {
    enum class Modo {
        LTR,
        RTL,
        WEBTOON;

        /** En qué sentido avanza el dedo o la flecha. */
        val avanzarHaciaLaDerecha: Boolean get() = this == RTL
    }

    init {
        require(total >= 0) { "total negativo: $total" }
    }

    var indice: Int = inicial.coerceIn(0, maxOf(0, total - 1))
        private set

    val vacio: Boolean get() = total == 0
    val esPrimera: Boolean get() = indice <= 0
    val esUltima: Boolean get() = total == 0 || indice >= total - 1

    /** Progreso 0..1. Con una sola página es 1, no 0: ya la estás viendo. */
    val progreso: Float
        get() = if (total <= 0) 0f else (indice + 1).toFloat() / total

    /** Texto para la barra: «7 / 32». Vacío si no hay páginas que contar. */
    fun etiqueta(): String = if (total == 0) "sin páginas" else "${indice + 1} / $total"

    /**
     * Avanza una página. Devuelve `true` si **había** a dónde avanzar.
     *
     * Cuando devuelve `false` se acabó el capítulo, y el llamador decide si
     * carga el siguiente o si solo avisa. No pasar de capítulo sola es
     * deliberado: hay gente que se queda en la última página para dejar el
     * marcapáginas donde está, y arrastrarla al capítulo siguiente se lo rompe.
     */
    fun siguiente(): Boolean {
        if (vacio || esUltima) return false
        indice++
        return true
    }

    /** Retrocede una página. `true` si había a dónde volver. */
    fun anterior(): Boolean {
        if (vacio || esPrimera) return false
        indice--
        return true
    }

    /** Salta a una página concreta, acotando en vez de lanzar. */
    fun irA(pagina: Int) {
        if (vacio) return
        indice = pagina.coerceIn(0, total - 1)
    }

    /**
     * Interpreta un gesto de desplazamiento horizontal.
     *
     * `dxPositivo` significa «el dedo fue hacia la derecha». En RTL eso es
     * avanzar; en LTR es retroceder. Un lector que ignora esto parece roto solo
     * en los manga. Devuelve lo mismo que [siguiente] o [anterior].
     */
    fun porGesto(dxPositivo: Boolean): Boolean {
        if (modo == Modo.WEBTOON) return false
        val avanza = dxPositivo == modo.avanzarHaciaLaDerecha
        return if (avanza) siguiente() else anterior()
    }

    /**
     * Intercambio de páginas dobles.
     *
     * En un móvil en horizontal caben dos páginas, y en manga el par (2,3) va
     * junto: la 1 sola, luego 2-3, 4-5. Ofrecerlo como función y no como promesa
     * permite probar que el índice no se descoloca al activarlo.
     */
    fun paginasVisibles(doble: Boolean): List<Int> {
        if (vacio) return emptyList()
        if (!doble || modo == Modo.WEBTOON) return listOf(indice)
        // La primera página del tomo va sola —es la portada del capítulo— y a
        // partir de ahí las parejas empiezan en índice impar: (1,2), (3,4)…
        // La versión anterior emparejaba (0,1) y dejaba la última suelta, que es
        // justo al revés de cómo se encuaderna un manga. Lo encontró la prueba.
        if (indice == 0) return listOf(0)
        val inicio = if (indice % 2 == 1) indice else indice - 1
        return listOfNotNull(inicio, (inicio + 1).takeIf { it < total })
    }

    /** Copia para un capítulo nuevo, conservando el modo de lectura. */
    fun paraCapitulo(nuevoTotal: Int, inicial: Int = 0): ReaderState =
        ReaderState(nuevoTotal, inicial, modo)

    companion object {
        /**
         * El estado que corresponde a un capítulo ya cargado.
         *
         * POR QUÉ ESTO ES UN MÉTODO Y NO UNA LÍNEA EN LA ACTIVITY
         * ------------------------------------------------------
         * Porque es lógica y tenía dos fallos posibles que nadie estaba
         * comprobando: con cero páginas reventaba el `coerceIn` (rango
         * `0..-1`) y con un marcapáginas mayor que el capítulo abría en la
         * última página sin decirlo. Aquí se corrige una vez y se prueba de
         * punta a punta en `LectorExtremoAExtremoTest`.
         */
        /**
         * El número de capítulo a partir de la etiqueta que muestra el lector
         * («Capítulo 12»). Devuelve -1 si no hay número: una obra con capítulos
         * sin numerar no se puede sincronizar, y es mejor saberlo que enviar un
         * 1 inventado.
         */
        fun numeroDeCapitulo(etiqueta: String): Int {
            val m = Regex("""(\d+(?:[.,]\d+)?)""").find(etiqueta) ?: return -1
            return m.groupValues[1].replace(',', '.').toFloatOrNull()?.toInt() ?: -1
        }

        fun desdePaginas(paginas: List<String>, inicial: Int = 0, modo: Modo = Modo.LTR): ReaderState {
            if (paginas.isEmpty()) return ReaderState(0, 0, modo)
            return ReaderState(paginas.size, inicial.coerceIn(0, paginas.size - 1), modo)
        }
    }
}
