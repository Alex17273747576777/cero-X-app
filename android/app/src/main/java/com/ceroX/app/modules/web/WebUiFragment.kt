package com.ceroX.app.modules.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentWebBinding
import java.io.ByteArrayInputStream

/** Pantalla WebView: los efectos Android viven aquí; las decisiones, en objetos JVM. */
class WebUiFragment : Fragment() {

    private var _b: FragmentWebBinding? = null
    private val b get() = _b!!
    private var modulo: WebModule? = null
    private var motorActual = MotorBusqueda.POR_DEFECTO
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
            if (isAdded && _b != null) actualizarReglas()
        }
        motorActual = modulo?.motor ?: MotorBusqueda.POR_DEFECTO
        b.webSearxBase.setText(modulo?.baseSearx ?: "")
        actualizarMotor()
        actualizarReglas()

        b.webBloqueo.isChecked = bloqueo
        b.webAntiMuro.isChecked = antiMuro
        b.webCookies.text = getString(etiquetaDe(cookies))
        b.webBloqueo.setOnCheckedChangeListener { _, on -> bloqueo = on }
        b.webAntiMuro.setOnCheckedChangeListener { _, on -> antiMuro = on }
        b.webMotor.setOnClickListener {
            motorActual = MotorBusqueda.siguiente(motorActual, baseSearx())
            modulo?.motor = motorActual
            actualizarMotor()
            cargarInicio()
        }
        b.webFmhya.setOnClickListener { cargarUrl("https://fmhy.net/") }
        b.webCookies.setOnClickListener {
            cookies = Privacidad.siguiente(cookies)
            b.webCookies.text = getString(etiquetaDe(cookies))
            aplicaCookies()
        }
        b.webLimpiar.setOnClickListener { borrarDatos() }
        b.webAtras.setOnClickListener { if (b.webVista.canGoBack()) b.webVista.goBack() }
        b.webRecargar.setOnClickListener { b.webVista.reload() }
        b.webIr.setOnClickListener {
            val destino = Navegador.destino(b.webUrl.text.toString(), motorActual, baseSearx())
            if (destino == null) mostrarErrorDestino() else cargarUrl(destino)
        }

        b.webVista.settings.javaScriptEnabled = true
        b.webVista.settings.domStorageEnabled = false
        b.webVista.settings.allowFileAccess = false
        b.webVista.settings.allowContentAccess = false
        b.webVista.settings.mediaPlaybackRequiresUserGesture = true
        b.webVista.settings.setSupportMultipleWindows(false)
        aplicaCookies()
        b.webVista.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                vista: WebView, peticion: android.webkit.WebResourceRequest,
            ): WebResourceResponse? {
                val url = peticion.url.toString()
                if (bloqueo && Bloqueador.debeBloquear(url, modulo?.reglasExtra ?: emptyList())) {
                    return WebResourceResponse(
                        "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)),
                    )
                }
                return super.shouldInterceptRequest(vista, peticion)
            }

            override fun onPageFinished(vista: WebView, url: String?) {
                if (antiMuro) vista.evaluateJavascript(AntiMuro.JS_OCULTAR, null)
            }
        }
        b.webVista.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            descargar(url, contentDisposition, mimeType)
        }
        cargarInicio()
    }

    private fun etiquetaDe(p: Privacidad.PoliticaCookies): Int = when (p) {
        Privacidad.PoliticaCookies.BLOQUEAR_TODO -> R.string.web_cookies_todo
        Privacidad.PoliticaCookies.BLOQUEAR_TERCEROS -> R.string.web_cookies_terceros
        Privacidad.PoliticaCookies.PERMITIR -> R.string.web_cookies_permitir
    }

    private fun baseSearx(): String {
        val base = b.webSearxBase.text.toString()
        modulo?.baseSearx = base
        return base
    }

    private fun actualizarMotor() {
        b.webMotor.text = MotorBusqueda.nombre(motorActual)
    }

    private fun actualizarReglas() {
        val total = Bloqueador.INCLUIDAS.size + (modulo?.reglasExtra?.size ?: 0)
        b.webEstado.text = "${getString(R.string.web_reglas)}: $total"
    }

    private fun aplicaCookies() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(cookies != Privacidad.PoliticaCookies.BLOQUEAR_TODO)
        cm.setAcceptThirdPartyCookies(
            b.webVista, cookies == Privacidad.PoliticaCookies.PERMITIR,
        )
    }

    private fun cargarInicio() {
        val url = MotorBusqueda.inicio(motorActual, baseSearx())
        if (url == null) mostrarErrorDestino() else cargarUrl(url)
    }

    private fun cargarUrl(url: String) {
        b.webVista.loadUrl(url, Privacidad.cabeceras())
    }

    private fun mostrarErrorDestino() {
        b.webEstado.text = getString(R.string.web_destino_invalido)
    }

    private fun borrarDatos() {
        b.webVista.stopLoading()
        b.webVista.clearCache(true)
        b.webVista.clearHistory()
        b.webVista.clearFormData()
        b.webVista.clearSslPreferences()
        WebStorage.getInstance().deleteAllData()
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
            Handler(Looper.getMainLooper()).post {
                if (isAdded && _b != null) {
                    b.webEstado.text = getString(R.string.web_datos_borrados)
                }
            }
        }
    }

    private fun descargar(url: String, contentDisposition: String?, mimeType: String?) {
        try {
            val uri = Uri.parse(url)
            require(uri.scheme.equals("https", ignoreCase = true))
            val nombre = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val solicitud = DownloadManager.Request(uri)
                .setTitle(nombre)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombre)
                .addRequestHeader("DNT", "1")
                .addRequestHeader("Sec-GPC", "1")
            if (mimeType != null) solicitud.setMimeType(mimeType)
            CookieManager.getInstance().getCookie(url)?.let {
                solicitud.addRequestHeader("Cookie", it)
            }
            requireContext().getSystemService(DownloadManager::class.java).enqueue(solicitud)
            Toast.makeText(requireContext(), R.string.web_descarga_iniciada, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            if (isAdded) {
                Toast.makeText(requireContext(), R.string.web_descarga_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        _b?.webVista?.apply {
            stopLoading()
            setDownloadListener(null)
            destroy()
        }
        _b = null
        super.onDestroyView()
    }
}
