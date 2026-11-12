package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.PersistentCookieJar
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class CloudflareBypassException @JvmOverloads constructor(
    message: String,
    val browserVerificationRequired: Boolean = false,
) : IOException(message)

/**
 * Detecta un reto en vez de devolver su HTML como si fuera contenido.
 * cero-X no declara resolverlo: hace falta una verificación interactiva real.
 */
class CloudflareInterceptor(
    @Suppress("UNUSED_PARAMETER") cookieJar: PersistentCookieJar,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val reto = response.header("cf-mitigated") == "challenge" ||
            (response.code in listOf(403, 503) && response.header("Server") in SERVER_CHECK)
        if (reto) {
            response.close()
            throw CloudflareBypassException(
                "${request.url.host} pide verificación de Cloudflare; cero-X no la ha resuelto",
                browserVerificationRequired = true,
            )
        }
        return response
    }

    private companion object {
        val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
    }
}
