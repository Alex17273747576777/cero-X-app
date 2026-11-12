package com.ceroX.app.modules.kisara

/** Consulta local: términos AND, comillas exactas, `-excluido` y campos title/author/tag. */
object BusquedaBiblioteca {
    private val token = Regex("""-?(?:title:|author:|tag:)?(?:"([^"]*)"|(\S+))""")

    fun filtrar(entries: List<KisaraLibraryEntry>, consulta: String): List<KisaraLibraryEntry> {
        val texto = consulta.trim()
        if (texto.isEmpty()) return entries
        val terminos = token.findAll(texto).map { coincidencia ->
            var bruto = coincidencia.value
            val negado = bruto.startsWith("-")
            if (negado) bruto = bruto.drop(1)
            val campo = listOf("title:", "author:", "tag:").firstOrNull { bruto.startsWith(it, true) }
            if (campo != null) bruto = bruto.drop(campo.length)
            val valor = bruto.removeSurrounding("\"").trim()
            Triple(campo?.dropLast(1)?.lowercase(), valor, negado)
        }.filter { it.second.isNotEmpty() }.toList()
        if (terminos.isEmpty()) return entries
        return entries.filter { entry ->
            terminos.all { (campo, valor, negado) ->
                val coincide = when (campo) {
                    "title" -> entry.title.contains(valor, true)
                    "author" -> entry.author.contains(valor, true)
                    "tag" -> entry.tags.any { it.contains(valor, true) }
                    else -> entry.title.contains(valor, true) || entry.author.contains(valor, true) ||
                        entry.tags.any { it.contains(valor, true) }
                }
                coincide != negado
            }
        }
    }
}

/**
 * Fusiona sin perder la progresión acumulada en iteraciones anteriores.
 * Los capítulos no se suman: las copias pueden contener los mismos capítulos.
 */
fun fusionarBiblioteca(
    entradas: List<KisaraLibraryEntry>,
    principalId: String,
    duplicados: List<String>,
): List<KisaraLibraryEntry> {
    require(entradas.any { it.id == principalId }) { "La entrada principal no existe" }
    val ids = duplicados.filter { it != principalId }.distinct()
    require(ids.all { id -> entradas.any { it.id == id } }) { "Una entrada duplicada no existe" }
    var principal = entradas.first { it.id == principalId }
    ids.forEach { id ->
        val copia = entradas.first { it.id == id }
        principal = principal.copy(
            progress = maxOf(principal.progress, copia.progress),
            chaptersRead = maxOf(principal.chaptersRead, copia.chaptersRead),
            totalChapters = maxOf(principal.totalChapters, copia.totalChapters),
            isFavorite = principal.isFavorite || copia.isFavorite,
            sources = (principal.sources + copia.sources + listOfNotNull(principal.sourceId, copia.sourceId)).distinct(),
            tags = (principal.tags + copia.tags).distinct(),
            lastUpdated = maxOf(principal.lastUpdated, copia.lastUpdated),
            isTracked = principal.isTracked || copia.isTracked,
        )
    }
    return entradas.filter { it.id !in ids }.map { if (it.id == principalId) principal else it }
}
