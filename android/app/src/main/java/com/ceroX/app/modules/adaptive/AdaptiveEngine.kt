package com.ceroX.app.modules.adaptive

import com.ceroX.app.modules.directories.EntradaDirectorio
import kotlinx.serialization.Serializable

/** Motor heurístico local: no es IA generativa ni envía señales a un proveedor. */
object MotorAdaptativo {
    const val DENSIDAD_COMPACTA = "compacta"
    const val DENSIDAD_COMODA = "cómoda"

    @Serializable
    data class Evento(
        val sesion: String,
        val tipo: TipoEvento,
        val categoria: String,
        val origen: String,
        val instante: Long,
    )

    @Serializable
    enum class TipoEvento { CATEGORIA, ORIGEN, FICHA, ENLACE, SINCRONIZAR }

    data class Perfil(
        val categoria: String,
        val densidad: String,
        val lineasDescripcion: Int,
        val prioridadesOrigen: Map<String, Int>,
        val explicacion: String,
        val adaptado: Boolean,
    )

    data class Recomendacion(val entrada: EntradaDirectorio, val puntuacion: Int, val motivo: String)

    fun base(categoria: String): Perfil = Perfil(
        categoria = categoria,
        densidad = if (categoria in setOf("Apps", "Anime", "Datos", "Herramientas")) DENSIDAD_COMPACTA else DENSIDAD_COMODA,
        lineasDescripcion = when (categoria) {
            "Guías" -> 6
            "Manga", "Novelas" -> 5
            "Datos" -> 2
            "Apps", "Anime", "Herramientas" -> 3
            else -> 4
        },
        prioridadesOrigen = emptyMap(),
        explicacion = "Diseño base para $categoria; sin señales aplicadas",
        adaptado = false,
    )

    fun perfil(eventos: List<Evento>, sesion: String, categoria: String, habilitado: Boolean = true): Perfil {
        if (!habilitado) return base(categoria).copy(explicacion = "Adaptación desactivada; diseño base")
        val relevantes = eventos.filter { categoria == "Todas" || it.categoria == categoria || it.categoria == "Todas" }
        val actuales = relevantes.filter { it.sesion == sesion }
        if (relevantes.isEmpty()) return base(categoria)
        val aperturas = actuales.count { it.tipo == TipoEvento.ENLACE }
        val fichas = actuales.count { it.tipo == TipoEvento.FICHA }
        val base = base(categoria)
        val compacta = aperturas >= 4 || base.densidad == DENSIDAD_COMPACTA
        val lineas = when {
            categoria == "Guías" -> 6
            fichas >= 2 && fichas > aperturas -> 6
            aperturas >= 4 -> 2
            else -> base.lineasDescripcion
        }
        val prioridades = relevantes.groupingBy { it.origen }.fold(0) { total, evento ->
            total + when (evento.tipo) {
                TipoEvento.ENLACE -> if (evento.sesion == sesion) 4 else 1
                TipoEvento.FICHA -> if (evento.sesion == sesion) 3 else 1
                TipoEvento.ORIGEN -> if (evento.sesion == sesion) 2 else 1
                TipoEvento.SINCRONIZAR -> 1
                TipoEvento.CATEGORIA -> 0
            }
        }.filterValues { it > 0 }.toList().sortedByDescending { it.second }.take(4).toMap()
        val razones = mutableListOf<String>()
        if (actuales.isNotEmpty()) razones += "${actuales.size} señales de esta sesión"
        if (relevantes.size > actuales.size) razones += "${relevantes.size - actuales.size} del historial local"
        if (aperturas >= 4) razones += "más compacto tras $aperturas aperturas"
        else if (compacta) razones += "densidad base de $categoria"
        if (lineas == 6 && categoria != "Guías") razones += "más contexto tras consultar fichas"
        prioridades.entries.firstOrNull()?.let { razones += "afinidad con ${it.key}" }
        return Perfil(categoria, if (compacta) DENSIDAD_COMPACTA else DENSIDAD_COMODA, lineas,
            prioridades, razones.joinToString(" · "), true)
    }

    fun ordenar(entradas: List<EntradaDirectorio>, perfil: Perfil, consulta: String): List<Recomendacion> {
        val q = consulta.trim().lowercase()
        return entradas.map { entrada ->
            val nombre = entrada.nombre.lowercase()
            var puntos = 0
            val motivos = mutableListOf<String>()
            when {
                q.isNotBlank() && nombre == q -> { puntos += 100; motivos += "nombre exacto" }
                q.isNotBlank() && nombre.startsWith(q) -> { puntos += 60; motivos += "empieza por la búsqueda" }
                q.isNotBlank() && q in nombre -> { puntos += 40; motivos += "coincide en el nombre" }
                q.isNotBlank() -> { puntos += 10; motivos += "coincide en metadatos" }
            }
            val afinidad = perfil.prioridadesOrigen[entrada.origen] ?: 0
            if (afinidad > 0) {
                puntos += afinidad * 8
                motivos += "afinidad local con ${entrada.origen} (+${afinidad * 8})"
            }
            if (perfil.categoria != "Todas" && entrada.categoria == perfil.categoria) puntos += 5
            if (motivos.isEmpty()) motivos += if (perfil.adaptado) "orden estable; sin afinidad extra" else "orden del directorio"
            Recomendacion(entrada, puntos, motivos.joinToString(" · "))
        }.sortedByDescending { it.puntuacion }
    }
}
