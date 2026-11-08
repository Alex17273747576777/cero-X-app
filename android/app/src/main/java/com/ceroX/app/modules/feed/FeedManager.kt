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

    private val entradas = LinkedHashMap<String, FeedEntry>()
    private val contador = AtomicInteger(0)

    /** Añade una entrada. Devuelve el id, o null si se llegó al máximo. */
    fun agregar(kind: FeedKind, titulo: String = kind.etiqueta, fuente: String? = null): String? {
        if (entradas.size >= FeedLimits.ENTRADAS_MAXIMAS) return null
        val id = "feed-${contador.incrementAndGet()}"
        entradas[id] = FeedEntry(id = id, kind = kind, titulo = titulo, fuente = fuente)
        return id
    }

    fun quitar(id: String): Boolean = entradas.remove(id) != null

    fun activar(id: String, activa: Boolean): Boolean {
        val e = entradas[id] ?: return false
        entradas[id] = e.copy(activa = activa, erroresSeguidos = 0, ultimoError = null)
        return true
    }

    fun mover(id: String, posicion: Int): Boolean {
        val e = entradas.remove(id) ?: return false
        val lista = entradas.values.toMutableList()
        lista.add(posicion.coerceIn(0, lista.size), e)
        entradas.clear()
        lista.forEach { entradas[it.id] = it }
        return true
    }

    fun lista(): List<FeedEntry> = entradas.values.toList()

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

        for (entrada in entradas.values.toList()) {
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
                entradas[entrada.id] = entrada.copy(
                    cursor = pagina.siguienteCursor,
                    agotado = pagina.agotado,
                    erroresSeguidos = 0,
                    ultimoError = null,
                )
                pagina.aviso?.let { avisos.add("${entrada.titulo}: $it") }
            } catch (e: Exception) {
                val fallos = entrada.erroresSeguidos + 1
                entradas[entrada.id] = entrada.copy(
                    erroresSeguidos = fallos,
                    ultimoError = e.message ?: e.javaClass.simpleName,
                )
                avisos.add("${entrada.titulo}: fallo $fallos de ${FeedLimits.FALLOS_ANTES_DE_APARCAR}")
            }
        }

        return Resultado(
            items = fusionados.values.sortedByDescending { it.actualizadoEn },
            ms = System.currentTimeMillis() - t0,
            avisos = avisos,
            entradasActivas = entradas.values.count { it.activa },
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
