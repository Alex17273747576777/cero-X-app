// Adaptado de Mihon/libtachi (Apache-2.0), sin transporte desktop ni WebView oculto.
package eu.kanade.tachiyomi.network

import android.content.Context
import com.ceroX.app.BuildConfig
import com.ceroX.app.core.AlmacenExterno
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Servicios OkHttp que el contrato 1.6 entrega a cada extensión. */
class NetworkHelper(context: Context) {
    private val app = context.applicationContext
    private val raiz = requireNotNull(AlmacenExterno.directorio(app)) {
        "Las extensiones necesitan el almacenamiento externo propio para caché y cookies"
    }.let { File(it, "host-extensiones/red").apply {
        if (!isDirectory && !mkdirs()) throw IllegalStateException("No se pudo crear la caché de red")
    } }

    val cookieJar = PersistentCookieJar(File(raiz, "cookies.json"))

    private fun clientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .cache(Cache(File(raiz, "http"), 20L * 1024 * 1024))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        // KeiSource comprueba estos tres nombres: no son decoración, forman
        // parte del contrato del cliente que las extensiones Project Nox esperan.
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))

    val client: OkHttpClient by lazy {
        clientBuilder().addInterceptor(CloudflareInterceptor(cookieJar)).build()
    }

    @Deprecated("El cliente normal ya detecta Cloudflare")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient get() = client

    /** No suplanta un navegador: identifica cero-X y su versión instalada. */
    fun defaultUserAgentProvider(): String =
        "cero-X/${BuildConfig.VERSION_NAME} (Android; ${app.packageName})"
}
