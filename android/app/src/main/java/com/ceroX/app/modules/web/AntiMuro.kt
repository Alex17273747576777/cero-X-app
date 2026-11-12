package com.ceroX.app.modules.web

/**
 * El ocultador de carteles anti-bloqueador, opcional y confesado.
 *
 * Algunos sitios detectan que no cargan sus anuncios y ponen un cartel encima
 * del contenido. Esto no rompe su detección: oculta el cartel por estilo y por
 * DOM después de cargar la página, con la lista de selectores a la vista para
 * quien quiera auditarla. Es una opción del usuario, apagada por defecto, y no
 * se presenta como otra cosa.
 */
object AntiMuro {

    val SELECTORES: List<String> = listOf(
        "#adblock-overlay", "#adblock-modal", ".adblock-detected",
        ".ad-blocker-warning", "#detect-adblock", ".js-adblock-modal",
    )

    /** El JS que se evalúa tras cargar, si la opción está encendida. */
    val JS_OCULTAR: String = buildString {
        append("(function(){var s=document.createElement('style');")
        append("s.textContent='")
        append(SELECTORES.joinToString(",") { it })
        append("{display:none!important}';")
        append("document.documentElement.appendChild(s);")
        append(SELECTORES.joinToString("") { sel ->
            "document.querySelectorAll('" + sel + "').forEach(function(n){n.remove()});"
        })
        append("})();")
    }
}
