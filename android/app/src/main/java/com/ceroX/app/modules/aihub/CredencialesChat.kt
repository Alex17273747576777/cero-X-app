package com.ceroX.app.modules.aihub

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Endpoint, modelo y clave en el almacén cifrado por Android Keystore. */
class CredencialesChat(private val context: Context) {
    private fun prefs(): SharedPreferences? = try {
        val maestro = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, ARCHIVO, maestro,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Throwable) {
        null
    }

    fun cargar(): ConfiguracionChat {
        val p = prefs()
        return ConfiguracionChat(
            baseUrl = p?.getString("base", null) ?: "https://api.openai.com",
            modelo = p?.getString("modelo", null) ?: "gpt-4o-mini",
            apiKey = p?.getString("clave", null).orEmpty(),
        )
    }

    /** No hay fallback en claro: false obliga a avisar al usuario. */
    fun guardar(config: ConfiguracionChat): Boolean = try {
        prefs()?.edit()
            ?.putString("base", config.baseUrl.trim())
            ?.putString("modelo", config.modelo.trim())
            ?.putString("clave", config.apiKey)
            ?.commit() == true
    } catch (e: Throwable) {
        false
    }

    fun borrarClave(): Boolean = try {
        prefs()?.edit()?.remove("clave")?.commit() == true
    } catch (e: Throwable) {
        false
    }

    fun almacenSeguroDisponible(): Boolean = prefs() != null

    private companion object {
        const val ARCHIVO = "cerox_asistente_cifrado"
    }
}
