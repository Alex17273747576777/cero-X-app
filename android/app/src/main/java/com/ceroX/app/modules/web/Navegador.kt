package com.ceroX.app.modules.web

import java.net.URI

/** Convierte lo escrito en una URL HTTPS o en una búsqueda, sin aceptar HTTP. */
object Navegador {

    private fun httpsValida(texto: String): String? {
        val uri = runCatching { URI(texto.trim()) }.getOrNull() ?: return null
        return if (uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
        ) texto.trim() else null
    }

    fun destino(texto: String, motorId: String, baseSearx: String = ""): String? {
        val entrada = texto.trim()
        if (entrada.isEmpty()) return null
        if (entrada.startsWith("http://", ignoreCase = true)) return null
        if (entrada.startsWith("https://", ignoreCase = true)) return httpsValida(entrada)
        if (entrada.contains("://") || entrada.contains(' ')) {
            return MotorBusqueda.url(motorId, entrada, baseSearx)
        }

        // Un dominio escrito sin esquema se vuelve HTTPS; lo demás es búsqueda.
        val candidata = "https://$entrada"
        val uri = runCatching { URI(candidata) }.getOrNull()
        val host = uri?.host
        if (host != null && (host.contains('.') || host == "localhost")) {
            return httpsValida(candidata)
        }
        return MotorBusqueda.url(motorId, entrada, baseSearx)
    }
}
