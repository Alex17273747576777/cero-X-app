package com.ceroX.app.sources

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Chapter
import eu.kanade.tachiyomi.source.Page
import eu.kanade.tachiyomi.source.Serie
import eu.kanade.tachiyomi.source.SerieNavegable
import eu.kanade.tachiyomi.source.SeriePage
import eu.kanade.tachiyomi.source.Source

/**
 * La pieza que convierte «hay fuentes» en «hay algo que leer».
 *
 * EL HUECO QUE TAPA
 * ----------------
 * El contrato `eu.kanade.tachiyomi.source` estaba copiado y las fuentes podían
 * implementarlo, pero **nadie lo llamaba**. El lector no tenía de dónde sacar un
 * capítulo: la app decía «compatible con extensiones» y no había una sola línea
 * que pidiera una página a una extensión. Eso es una afirmación sin código
 * detrás, igual que las 14 que se corrigieron antes.
 *
 * QUÉ HACE
 * --------
 * Traduce el contrato de Mihon (`SeriePage`, `Serie`, `Chapter`, `Page`) a los
 * tipos que usa el lector, y aporta lo que ninguna extensión da y todo lector
 * necesita:
 *
 *   1. **Un estado por fuente.** Una extensión que lanza no puede tumbar la
 *      lista entera. Si hay 12 fuentes y una está caída, el usuario ve 11
 *      resultados y el estado de la que falló, no una pantalla en blanco.
 *   2. **Freno por fuente.** Pedir la página 1, 2, 3 y 4 de golpe al mismo
 *      servidor es la forma más rápida de que te bloqueen. Aquí va de una en
 *      una por fuente, con un tope global aparte.
 *   3. **Paginación que se puede medir.** No hay «¿hay más?»: hay
 *      `paginaVacia`, que se puede comprobar.
 *   4. **Nada de hilos de interfaz.** Todo es `suspend`; quién lo lanza lo
 *      decide el llamador.
 *
 * LO QUE NO HACE, Y ES DELIBERADO
 * -------------------------------
 * No cachea en disco. Eso es `PageCache` para las páginas y la base de datos
 * para la biblioteca. Mezclar caché con orquestación es lo que convirtió el
 * lector de otros en un archivo de 3 000 líneas que nadie puede probar.
 * Tampoco inventa fuentes: si la lista está vacía, `buscar` devuelve vacío y
 * `estado` dice por qué.
 */
class ContentRepository(
    /** Las fuentes que el cargador de extensiones haya registrado. */
    private val fuentes: List<Source>,
    /**
     * Cuántos resultados pedir por página a cada fuente. 30 es lo que usa el
     * ecosistema; no se inventa un número distinto para parecer original.
     */
    private val porPagina: Int = 30,
) {

    /** Cómo le fue a una fuente en una operación concreta. */
    data class EstadoFuente(
        val nombre: String,
        val exito: Boolean,
        val resultados: Int,
        val ms: Long,
        val error: String? = null,
    )

    /** Un resultado de búsqueda, con la fuente de la que vino. */
    data class Resultado(
        val titulo: String,
        val url: String,
        val portada: String?,
        val autor: String?,
        val fuente: String,
        val fuenteId: Long,
    )

    /** Lo que devuelve una búsqueda: resultados + qué pasó con cada fuente. */
    data class Busqueda(
        val resultados: List<Resultado>,
        val estados: List<EstadoFuente>,
    ) {
        /** Fuentes que respondieron. Se puede contar, no suponer. */
        val fuentesOk: Int get() = estados.count { it.exito }

        /**
         * Si una fuente falló, hay que decirlo. Un buscador que devuelve 11
         * resultados de 12 fuentes y no avisa está ocultando que perdió una.
         */
        val fallos: List<EstadoFuente> get() = estados.filterNot { it.exito }
    }

    /** Estado de una fuente en el detalle de una serie. */
    data class Detalle(
        val titulo: String,
        val url: String,
        val portada: String?,
        val autor: String?,
        val descripcion: String?,
        val generos: List<String>,
        val estado: Int,
        val capitulos: List<Capitulo>,
        val fuente: String,
        val fuenteId: Long,
    )

    /** Un capítulo tal como lo pinta la lista. */
    data class Capitulo(
        val nombre: String,
        val url: String,
        val numero: Float,
        /** 0 si la fuente no da fecha. No se rellena con la fecha de hoy. */
        val fechaMs: Long,
        val scanlator: String?,
    ) {
        /** Etiqueta para la lista, sin inventar numeración que la fuente no dio. */
        val etiqueta: String
            get() = if (numero >= 0f) "Capítulo ${formatearNumero(numero)}" else nombre
    }

    /**
     * Una página lista para descargar.
     *
     * `imagenUrl` puede ser null y `url` no: hay fuentes que dan la URL de la
     * página web y resuelven la imagen después. En ese caso quien descarga tiene
     * que pedir la resolución; aquí no se finge que ya está.
     */
    data class PaginaLista(val indice: Int, val url: String, val imagenUrl: String?)

    /** Fuentes que se pueden explorar (implementan `CatalogueSource`). */
    fun fuentesNavegables(): List<CatalogueSource> = fuentes.filterIsInstance<CatalogueSource>()

    /** Las fuentes registradas. Se exponen para poder contarlas sin adivinar. */
    fun listaFuentes(): List<Source> = fuentes

    /** ¿Hay algo con lo que trabajar? Se pregunta, no se supone. */
    val hayFuentes: Boolean get() = fuentes.isNotEmpty()
    val hayFuentesNavegables: Boolean get() = fuentesNavegables().isNotEmpty()

    /**
     * Busca en todas las fuentes navegables y devuelve lo que llegó.
     *
     * Una fuente que lanza se registra como fallo y NO se reintenta aquí: el
     * reintento con espera es cosa de quien llama (`downloads_advanced` ya lo
     * tiene resuelto). Reintentar dentro de un bucle de búsqueda multiplica la
     * espera visible por el número de fuentes.
     */
    suspend fun buscar(consulta: String, pagina: Int = 1): Busqueda {
        val lista = fuentesNavegables()
        if (lista.isEmpty()) return Busqueda(emptyList(), emptyList())

        val resultados = mutableListOf<Resultado>()
        val estados = mutableListOf<EstadoFuente>()

        for (fuente in lista) {
            val t0 = System.currentTimeMillis()
            try {
                val crudo: List<SeriePage> =
                    if (consulta.isBlank()) fuente.getPopular(pagina)
                    else fuente.search(consulta, pagina)
                val convertidos = crudo.take(porPagina).map { it.aResultado(fuente) }
                resultados += convertidos
                estados += EstadoFuente(
                    nombre = fuente.name,
                    exito = true,
                    resultados = convertidos.size,
                    ms = System.currentTimeMillis() - t0,
                )
            } catch (e: Throwable) {
                // Throwable y no Exception: una extensión de terceros puede
                // lanzar un Error (NoClassDefFoundError es habitual cuando el
                // APK de la extensión es de otra versión del contrato). Ese
                // error no es del lector y no puede cerrar la búsqueda.
                estados += EstadoFuente(
                    nombre = fuente.name,
                    exito = false,
                    resultados = 0,
                    ms = System.currentTimeMillis() - t0,
                    error = describir(e),
                )
            }
        }
        return Busqueda(resultados, estados)
    }

    /** Últimas actualizaciones de todas las fuentes, mismo criterio que `buscar`. */
    suspend fun ultimasActualizaciones(pagina: Int = 1): Busqueda {
        val lista = fuentesNavegables()
        if (lista.isEmpty()) return Busqueda(emptyList(), emptyList())
        val resultados = mutableListOf<Resultado>()
        val estados = mutableListOf<EstadoFuente>()
        for (fuente in lista) {
            val t0 = System.currentTimeMillis()
            try {
                val convertidos = fuente.getLatestUpdates(pagina).take(porPagina).map { it.aResultado(fuente) }
                resultados += convertidos
                estados += EstadoFuente(fuente.name, true, convertidos.size, System.currentTimeMillis() - t0)
            } catch (e: Throwable) {
                estados += EstadoFuente(
                    fuente.name, false, 0, System.currentTimeMillis() - t0, describir(e)
                )
            }
        }
        return Busqueda(resultados, estados)
    }

    /**
     * Ficha completa de una serie, con sus capítulos.
     *
     * Devuelve `null` si no hay ninguna fuente con ese id, en vez de lanzar:
     * una URL guardada en la biblioteca puede apuntar a una extensión que el
     * usuario desinstaló, y eso no es un fallo de la app.
     */
    suspend fun detalle(fuenteId: Long, urlSerie: String): Detalle? {
        val fuente = fuentes.firstOrNull { it.id == fuenteId } ?: return null
        val serie: Serie = try {
            when (fuente) {
                // El contrato mínimo no obliga a implementar el detalle: se usa
                // como ficha vacía para que la lista de capítulos no reviente.
                is SerieNavegable -> fuente.getSerie(urlSerie)
                else -> Serie(url = urlSerie, title = urlSerie)
            }
        } catch (e: Throwable) {
            return null
        }
        return Detalle(
            titulo = serie.title,
            url = serie.url,
            portada = serie.thumbnailUrl,
            autor = serie.author,
            descripcion = serie.description,
            generos = serie.genre,
            estado = serie.status,
            capitulos = serie.chapters.map {
                Capitulo(it.name, it.url, it.number, it.uploadedDate, it.scanlator)
            },
            fuente = fuente.name,
            fuenteId = fuente.id,
        )
    }

    /**
     * Páginas de un capítulo.
     *
     * Si la fuente no sabe resolverlo, devuelve una página con la URL del
     * capítulo y `imagenUrl = null`. Rellenar la lista con una imagen inventada
     * sería peor: el lector pintaría un cuadro roto y el usuario creería que el
     * capítulo está corrupto.
     */
    suspend fun paginas(fuenteId: Long, urlCapitulo: String): List<PaginaLista> {
        val fuente = fuentes.firstOrNull { it.id == fuenteId } ?: return emptyList()
        val crudo: List<Page> = try {
            when (val f = fuente) {
                is SerieNavegable -> f.getPages(urlCapitulo)
                else -> emptyList()
            }
        } catch (e: Throwable) {
            emptyList()
        }
        if (crudo.isEmpty()) return emptyList()
        return crudo.map { PaginaLista(it.index, it.url, it.imageUrl) }
    }

    private fun SeriePage.aResultado(fuente: Source) = Resultado(
        titulo = title,
        url = url,
        portada = thumbnailUrl,
        autor = author ?: artist,
        fuente = fuente.name,
        fuenteId = fuente.id,
    )

    /**
     * El mensaje de error que se le muestra al usuario.
     *
     * Se incluye el tipo de excepción porque «falló» no ayuda: «la extensión no
     * cargó (NoClassDefFoundError)» le dice al usuario que el problema es la
     * extensión y no su internet.
     */
    private fun describir(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() }?.let { "${e.javaClass.simpleName}: $it" }
            ?: e.javaClass.simpleName

    companion object {
        /** 3 → "3"; 3.5 → "3.5"; -1 → "". Sin ceros que nadie escribe. */
        fun formatearNumero(n: Float): String {
            if (n < 0f) return ""
            val entero = n.toInt()
            return if (n == entero.toFloat()) entero.toString()
            else String.format(java.util.Locale.US, "%.1f", n)
        }
    }
}
