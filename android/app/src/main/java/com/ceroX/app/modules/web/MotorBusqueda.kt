package com.ceroX.app.modules.web

import java.net.URLEncoder

/**
 * Los motores de búsqueda del módulo web.
 *
 * Brave Search es el motor por defecto porque es lo decidido: búsqueda sin
 * perfilado de Google. La lista no contiene a Google ni como motor ni como
 * página de inicio, y añadirlo aquí sería un cambio visible en esta prueba y
 * en este comentario, no un ajuste escondido.
 */
object MotorBusqueda {

    data class Motor(val id: String, val nombre: String, val plantilla: String)

    val MOTORES: List<Motor> = listOf(
        Motor("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
        Motor("duck", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        Motor("searx", "SearXNG propio", ""),
    )

    val POR_DEFECTO = "brave"

    /** La URL de búsqueda, o null si el motor no existe o necesita base y no la tiene. */
    fun url(motorId: String, consulta: String, baseSearx: String = ""): String? {
        val motor = MOTORES.firstOrNull { it.id == motorId } ?: return null
        if (consulta.isBlank()) return null
        val q = URLEncoder.encode(consulta.trim(), "UTF-8")
        return when (motor.id) {
            "searx" -> {
                val base = baseSearx.trim().trimEnd('/')
                if (base.isEmpty() || !base.startsWith("http")) null else "$base/search?q=$q"
            }
            else -> motor.plantilla.format(q)
        }
    }
}
