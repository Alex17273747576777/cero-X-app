package com.ceroX.app.modules.web

import java.net.URI
import java.net.URLEncoder

/** Motores de búsqueda del módulo web. Google no está en la lista. */
object MotorBusqueda {

    data class Motor(val id: String, val nombre: String, val plantilla: String)

    val MOTORES: List<Motor> = listOf(
        Motor("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
        Motor("duck", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        Motor("searx", "SearXNG propio", ""),
    )

    val POR_DEFECTO = "brave"

    /** Base HTTPS de un SearXNG propio, sin credenciales en la URL. */
    fun baseSearxValida(baseSearx: String): String? {
        val base = baseSearx.trim().trimEnd('/')
        if (base.isEmpty()) return null
        val uri = runCatching { URI(base) }.getOrNull() ?: return null
        return if (uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
        ) base else null
    }

    /** La URL de búsqueda, o null si el motor no existe o la consulta está vacía. */
    fun url(motorId: String, consulta: String, baseSearx: String = ""): String? {
        val motor = MOTORES.firstOrNull { it.id == motorId } ?: return null
        if (consulta.isBlank()) return null
        val q = URLEncoder.encode(consulta.trim(), "UTF-8")
        return when (motor.id) {
            "searx" -> baseSearxValida(baseSearx)?.let { "$it/search?q=$q" }
            else -> motor.plantilla.format(q)
        }
    }

    /** Portada sin consulta: abrir la pantalla no lanza una búsqueda inventada. */
    fun inicio(motorId: String, baseSearx: String = ""): String? = when (motorId) {
        "brave" -> "https://search.brave.com/"
        "duck" -> "https://duckduckgo.com/"
        "searx" -> baseSearxValida(baseSearx)?.let { "$it/" }
        else -> null
    }

    fun nombre(motorId: String): String =
        MOTORES.firstOrNull { it.id == motorId }?.nombre ?: MOTORES.first().nombre

    /** Rota sólo por los motores utilizables; SearXNG necesita una base válida. */
    fun siguiente(actual: String, baseSearx: String = ""): String {
        val disponibles = MOTORES.filter { it.id != "searx" || inicio("searx", baseSearx) != null }
        val indice = disponibles.indexOfFirst { it.id == actual }
        return disponibles[(indice + 1 + disponibles.size) % disponibles.size].id
    }
}
