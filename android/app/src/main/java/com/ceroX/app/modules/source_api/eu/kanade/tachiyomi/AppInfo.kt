// API de extensions-lib 1.6. La identidad procede del build real de cero-X.
package eu.kanade.tachiyomi

import com.ceroX.app.BuildConfig

/** Información que las extensiones consultan sobre la aplicación anfitriona. */
@Suppress("Unused")
object AppInfo {
    @JvmStatic
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE

    @JvmStatic
    fun getVersionName(): String = BuildConfig.VERSION_NAME
}
