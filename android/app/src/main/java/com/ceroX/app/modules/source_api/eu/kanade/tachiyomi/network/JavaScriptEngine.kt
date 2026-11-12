package eu.kanade.tachiyomi.network

import android.content.Context
import com.ceroX.app.modules.source_js.QuickJsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Puente 1.6 a QuickJS; conserva el timeout y el aislamiento del motor cero-X. */
@Suppress("UNUSED", "UNCHECKED_CAST")
class JavaScriptEngine(@Suppress("UNUSED_PARAMETER") context: Context) {
    private val engine = QuickJsEngine()

    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Default) {
        engine.evalAny(script) as T
    }

    fun close() = engine.close()
}
