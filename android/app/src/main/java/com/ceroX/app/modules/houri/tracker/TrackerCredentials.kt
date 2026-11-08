package com.ceroX.app.modules.houri.tracker

import android.content.Context

/**
 * El token de cada tracker, guardado donde debe.
 *
 * DÓNDE NO SE GUARDA
 * ------------------
 * Ni en `SharedPreferences` normal, ni en un archivo de `filesDir`, ni en la
 * base de datos. Un token de AniList da acceso a la cuenta entera del usuario, y
 * el `filesDir` de una app se puede leer en un teléfono rooteado o con un backup
 * mal hecho. Se usa `EncryptedSharedPreferences`, que deriva una clave del
 * Keystore del dispositivo.
 *
 * SI EL KEYSTORE NO ESTÁ
 * ----------------------
 * En un dispositivo sin Keystore utilizable (emuladores antiguos, algún ROM
 * raro) `EncryptedSharedPreferences` lanza. En ese caso **no se cae a
 * preferences normales**: se devuelve null al leer y false al guardar, y la
 * pantalla de ajustes lo dice. Guardar un token en claro para que «funcione» es
 * exactamente el tipo de atajo que después sale caro.
 */
class TrackerCredentials(private val context: Context) {

    private fun prefs(): android.content.SharedPreferences? = try {
        val maestro = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            ARCHIVO,
            maestro,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Throwable) {
        null
    }

    /** El token, o null si no hay o no se pudo leer de forma segura. */
    fun token(tracker: Tracker): String? =
        prefs()?.getString(clave(tracker), null)?.takeIf { it.isNotBlank() }

    /** Guarda el token. Devuelve false si no hay almacén seguro disponible. */
    fun guardar(tracker: Tracker, token: String): Boolean {
        val p = prefs() ?: return false
        return try {
            p.edit().putString(clave(tracker), token.trim()).commit()
        } catch (e: Throwable) {
            false
        }
    }

    fun borrar(tracker: Tracker) {
        prefs()?.edit()?.remove(clave(tracker))?.commit()
    }

    /** Qué hay configurado, sin revelar el token. */
    fun resumen(): Map<String, Boolean> =
        Tracker.entries.associate { it.id to (token(it) != null) }

    /** Si se puede guardar de forma cifrada. Si es false, la UI tiene que avisar. */
    fun almacenSeguroDisponible(): Boolean = prefs() != null

    private fun clave(tracker: Tracker) = "token_${tracker.id}"

    companion object {
        private const val ARCHIVO = "cerox_trackers_cifrado"
    }
}
