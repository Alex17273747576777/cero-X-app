package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

/** Registra los servicios que un APK 1.6 obtiene mediante Injekt. */
object HostExtensiones {
    @Volatile
    var inicializado: Boolean = false
        private set

    lateinit var network: NetworkHelper
        private set
    lateinit var javascript: JavaScriptEngine
        private set

    @Synchronized
    fun inicializar(application: Application) {
        if (inicializado) return
        val context = application.applicationContext
        network = NetworkHelper(context)
        javascript = JavaScriptEngine(context)
        Injekt.addSingleton<Application>(application)
        Injekt.addSingleton<Context>(context)
        Injekt.addSingleton(network)
        Injekt.addSingleton(javascript)
        Injekt.addSingleton(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        })
        inicializado = true
    }
}
