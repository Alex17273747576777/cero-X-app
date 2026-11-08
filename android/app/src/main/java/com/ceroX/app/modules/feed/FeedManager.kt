package com.ceroX.app.modules.feed

import java.util.concurrent.atomic.AtomicInteger

/**
 * Gestiona el feed multi-entrada.
 *
 * REGLAS QUE APLICA
 * -----------------
 * 1. Cada entrada tiene su cursor. Una fuente lenta no frena a las demás.
 * 2. Una entrada que falla no rompe el inicio: se marca, se reintenta más tarde
 *    y las otras siguen. Esto es lo que separa un feed usable de uno que se
 *    queda en blanco porque un proveedor cambió su HTML.
 * 3. Tras tres fallos seguidos la entrada se aparca sola. Se puede reactivar a
 *    mano, pero deja de reintentarse en cada refresco, que es lo que gasta
 *    batería sin que el usuario sepa por qué.
 * 4. Nada se borra: desactivar no es eliminar.
 */
class FeedManager(
    /** De dónde salen los elementos de cada tipo de entrada. */
    private val proveedor: ProveedorDeFeeds,
) {

    /** Lo que el feed necesita que alguien le dé. Sin él no hay datos, hay estructura. */
    interface ProveedorDeFeeds {
        fun pagina(entrada: FeedEntry): FeedPage
    }

    /**
     * Estado de las entradas.
     *
     * POR QUÉ UN CANDADO Y NO UN MAPA CONCURRENTE
     * -------------------------------------------
     * `ConcurrentHashMap` hace atómica cada operación suelta, pero no protege una
     * secuencia. Aquí hay dos secuencias que sí importan:
     *   - `mover()` hace remove + toMutableList + clear + reinsertar: con un mapa
     *     concurrente, otro hilo puede ver el feed a medias.
     *   - `refrescar()` lee la lista, sale a red y escribe: entre leer y escribir,
     *     el usuario puede haber borrado la entrada, y `entradas[entrada.id] = ...`
     *     la resucitaría.
     * Un candado sobre todas las operaciones de estado es la única forma de que
     * esas dos secuencias sean atómicas.
     *
     * LO QUE NO VA BAJO EL CANDADO
     * ----------------------------
     * La llamada a `proveedor.pagina()` —que puede tardar segundos en red— se hace
     * FUERA. Si se hiciera dentro, refrescar el feed bloquearía la interfaz
     * entera: sería cambiar un fallo raro por uno seguro y peor.
     * Se toma una foto de las entradas, se sale a red sin candado, y se aplican
     * los resultados con una comprobación de que la entrada sigue existiendo.
     */
    private val candado = Any()
    private val entradas = LinkedHashMap<String, FeedEntry>()
    private val contador = AtomicInteger(0)

    /** Añade una entrada. Devuelve el id, o null si se llegó al máximo. */
    fun agregar(kind: FeedKind, titulo: String = kind.etiqueta, fuente: String? = null): String? =
        synchronized(candado) {
            if (entradas.size >= FeedLimits.ENTRADAS_MAXIMAS) return null
            val id = "feed-${contador.incrementAndGet()}"
            entradas[id] = FeedEntry(id = id, kind = kind, titulo = titulo, fuente = fuente)
            id
        }

    fun quitar(id: String): Boolean = synchronized(candado) { entradas.remove(id) != null }

    fun activar(id: String, activa: Boolean): Boolean = synchronized(candado) {
        val e = entradas[id] ?: return false
        entradas[id] = e.copy(activa = activa, erroresSeguidos = 0, ultimoError = null)
        true
    }

    fun mover(id: String, posicion: Int): Boolean = synchronized(candado) {
        val e = entradas.remove(id) ?: return false
        val lista = entradas.values.toMutableList()
        lista.add(posicion.coerceIn(0, lista.size), e)
        entradas.clear()
        lista.forEach { entradas[it.id] = it }
        true
    }

    fun lista(): List<FeedEntry> = synchronized(candado) { entradas.values.toList() }

    /**
     * Refresca todas las entradas activas y devuelve lo que hay que pintar.
     *
     * Los elementos se fusionan por id: la misma obra que aparece en
     * "biblioteca" y en "nuevos capítulos" se muestra una sola vez, con el motivo
     * de la entrada que la encontró primero. Sin esto, el inicio se llena de
     * duplicados y parece roto.
     */
    fun refrescar(): Resultado {
        val t0 = System.currentTimeMillis()
        val fusionados = LinkedHashMap<String, FeedItem>()
        val avisos = ArrayList<String>()

        // Foto del estado. A partir de aquí NO se toca `entradas` sin candado.
        for (entrada in lista()) {
            if (!entrada.activa) continue
            if (entrada.agotado) continue
            if (entrada.erroresSeguidos >= FeedLimits.FALLOS_ANTES_DE_APARCAR) {
                avisos.add("${entrada.titulo}: aparcada tras ${entrada.erroresSeguidos} fallos")
                continue
            }

            try {
                val pagina = proveedor.pagina(entrada)
                for (item in pagina.items) {
                    // Si ya está, se conserva el primero: el orden de las entradas
                    // es una decisión del usuario y manda.
                    if (!fusionados.containsKey(item.id)) fusionados[item.id] = item
                }
                synchronized(candado) {
                    // Si el usuario borró la entrada mientras se pedía la página,
                    // no se resucita. Sin esta comprobación, borrar una entrada
                    // durante un refresco la hacía reaparecer.
                    if (entradas.containsKey(entrada.id)) {
                        entradas[entrada.id] = entrada.copy(
                            cursor = pagina.siguienteCursor,
                            agotado = pagina.agotado,
                            erroresSeguidos = 0,
                            ultimoError = null,
                        )
                    }
                }
                pagina.aviso?.let { avisos.add("${entrada.titulo}: $it") }
            } catch (e: Exception) {
                val fallos = entrada.erroresSeguidos + 1
                synchronized(candado) {
                    if (entradas.containsKey(entrada.id)) {
                        entradas[entrada.id] = entrada.copy(
                            erroresSeguidos = fallos,
                            ultimoError = e.message ?: e.javaClass.simpleName,
                        )
                    }
                }
                avisos.add("${entrada.titulo}: fallo $fallos de ${FeedLimits.FALLOS_ANTES_DE_APARCAR}")
            }
        }

        return Resultado(
            items = fusionados.values.sortedByDescending { it.actualizadoEn },
            ms = System.currentTimeMillis() - t0,
            avisos = avisos,
            entradasActivas = synchronized(candado) { entradas.values.count { it.activa } },
        )
    }

    data class Resultado(
        val items: List<FeedItem>,
        val ms: Long,
        val avisos: List<String>,
        val entradasActivas: Int,
    ) {
        val vacio: Boolean get() = items.isEmpty()
    }
}
