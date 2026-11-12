package com.ceroX.app.modules.web

/**
 * La política de privacidad del WebView.
 *
 * Este objeto no toca el CookieManager del sistema: describe la política y da
 * las cabeceras. Aplicarla al WebView es trabajo del fragmento, que vive en el
 * hilo principal y es el único que puede tocar vistas y managers del sistema.
 */
object Privacidad {

    enum class PoliticaCookies { BLOQUEAR_TODO, BLOQUEAR_TERCEROS, PERMITIR }

    val POR_DEFECTO = PoliticaCookies.BLOQUEAR_TERCEROS

    fun siguiente(p: PoliticaCookies): PoliticaCookies = when (p) {
        PoliticaCookies.BLOQUEAR_TODO -> PoliticaCookies.BLOQUEAR_TERCEROS
        PoliticaCookies.BLOQUEAR_TERCEROS -> PoliticaCookies.PERMITIR
        PoliticaCookies.PERMITIR -> PoliticaCookies.BLOQUEAR_TODO
    }

    /** Cabeceras que el WebView añade a cada petición. DNT siempre. */
    fun cabeceras(): Map<String, String> = mapOf(
        "DNT" to "1",
        "Sec-GPC" to "1",
    )
}
