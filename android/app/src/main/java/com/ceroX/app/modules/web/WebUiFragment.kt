package com.ceroX.app.modules.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentWebBinding
import java.io.ByteArrayInputStream

/**
 * La pantalla del web privado.
 *
 * Todo lo que toca vistas y managers del sistema vive aquí, en el hilo
 * principal: el WebView, el CookieManager y el JS del ocultador. Lo que decide
 * (motor, bloqueo, política) vive en los objetos del paquete y se prueba en JVM.
 */
class WebUiFragment : Fragment() {

    private var _b: FragmentWebBinding? = null
    private val b get() = _b!!
    private var modulo: WebModule? = null
    private var reglasExtra: List<String> = emptyList()
    private var bloqueo = true
    private var antiMuro = false
    private var cookies = Privacidad.POR_DEFECTO

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentWebBinding.inflate(i, c, false)
        return b.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        val app = requireActivity().application as CeroXApplication
        modulo = Pantallas.modulo(app, "web", WebModule::class.java) {
            if (isAdded && _b != null) b.webUrl.hint = getString(R.string.web_url_hint)
        }
        b.webBloqueo.isChecked = bloqueo
        b.webAntiMuro.isChecked = antiMuro
        b.webCookies.text = getString(etiquetaDe(cookies))
        b.webBloqueo.setOnCheckedChangeListener { _, on -> bloqueo = on }
        b.webAntiMuro.setOnCheckedChangeListener { _, on -> antiMuro = on }
        b.webCookies.setOnClickListener {
            cookies = Privacidad.siguiente(cookies)
            b.webCookies.text = getString(etiquetaDe(cookies))
            aplicaCookies()
        }
        b.webAtras.setOnClickListener { if (b.webVista.canGoBack()) b.webVista.goBack() }
        b.webRecargar.setOnClickListener { b.webVista.reload() }
        b.webIr.setOnClickListener { cargar(b.webUrl.text.toString()) }

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(cookies != Privacidad.PoliticaCookies.BLOQUEAR_TODO)
        cm.setAcceptThirdPartyCookies(b.webVista,
            cookies == Privacidad.PoliticaCookies.PERMITIR)
        b.webVista.settings.javaScriptEnabled = true
        b.webVista.settings.domStorageEnabled = false
        b.webVista.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                vista: WebView, peticion: android.webkit.WebResourceRequest,
            ): WebResourceResponse? {
                val url = peticion.url.toString()
                if (bloqueo && Bloqueador.debeBloquear(url, reglasExtra)) {
                    return WebResourceResponse(
                        "text/plain", "UTF-8",
                        ByteArrayInputStream(ByteArray(0)),
                    )
                }
                return super.shouldInterceptRequest(vista, peticion)
            }

            override fun onPageFinished(vista: WebView, url: String) {
                if (antiMuro) vista.evaluateJavascript(AntiMuro.JS_OCULTAR, null)
            }
        }
        cargar(MotorBusqueda.url(motor(), "cero-X") ?: "about:blank")
    }

    private fun etiquetaDe(p: Privacidad.PoliticaCookies): Int = when (p) {
        Privacidad.PoliticaCookies.BLOQUEAR_TODO -> R.string.web_cookies_todo
        Privacidad.PoliticaCookies.BLOQUEAR_TERCEROS -> R.string.web_cookies_terceros
        Privacidad.PoliticaCookies.PERMITIR -> R.string.web_cookies_permitir
    }

    /** El motor del módulo si llegó a cargarse; si no, el por defecto. */
    private fun motor(): String = modulo?.motor ?: MotorBusqueda.POR_DEFECTO

    private fun aplicaCookies() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(cookies != Privacidad.PoliticaCookies.BLOQUEAR_TODO)
        cm.setAcceptThirdPartyCookies(b.webVista,
            cookies == Privacidad.PoliticaCookies.PERMITIR)
    }

    private fun cargar(destino: String) {
        val url = if (destino.startsWith("http")) destino
        else MotorBusqueda.url(motor(), destino) ?: return
        b.webVista.loadUrl(url, Privacidad.cabeceras())
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
