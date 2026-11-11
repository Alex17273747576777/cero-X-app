package com.ceroX.app.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ceroX.app.core.engine.ModuleInterface
import com.ceroX.app.core.engine.ModuleStatus

/**
 * Cómo resuelve una pantalla el módulo que necesita.
 *
 * Había otro camino y estaba roto. Seis pantallas pedían su módulo a un motor
 * «legacy» cuyo getter sólo lo construía: nunca registraba nada, porque los 36
 * módulos se registran en `lifecycleManager`. La búsqueda devolvía null y el cast
 * no nulo cerraba la pantalla en el acto. «Diseño» es un destino de la barra
 * inferior, o sea que la app se caía al tocarlo, y otras cinco pantallas igual.
 * Ese motor está borrado para que el camino roto no vuelva a existir.
 *
 * Este objeto devuelve null en vez de reventar y lo dice por log: una pantalla
 * vacía que se explica es un fallo visible; una excepción es un cierre. Y si el
 * módulo todavía no se inicializó —el arranque va en su propio hilo desde la
 * 1.0.1— pide su inicialización en segundo plano y llama a `alIniciarse` en el
 * hilo principal cuando termina, para que la pantalla se repinte con los datos.
 */
object Pantallas {

    private const val TAG = "ceroX.pantallas"

    /**
     * El módulo `nombre` como `T`, o null si no está registrado o no es de ese tipo.
     *
     * @param alIniciarse se ejecuta en el hilo principal cuando el módulo termina de
     *   inicializarse, si es que no lo estaba. Null si a la pantalla le da igual.
     */
    fun <T : ModuleInterface> modulo(
        app: CeroXApplication,
        nombre: String,
        clase: Class<T>,
        alIniciarse: (() -> Unit)? = null,
    ): T? {
        val instancia = app.getModuleInstance(nombre)
        if (instancia == null) {
            Log.w(TAG, "$nombre no está registrado: la pantalla queda vacía en vez de cerrarse")
            return null
        }
        if (!clase.isInstance(instancia)) {
            Log.w(TAG, "$nombre es ${instancia.javaClass.simpleName}, no ${clase.simpleName}")
            return null
        }
        val estado = app.getModuleStatus(nombre)
        val listo = estado == ModuleStatus.INITIALIZED || estado == ModuleStatus.STARTED
        if (!listo && alIniciarse != null) {
            val futuro = app.lifecycleManager.iniciarEnSegundoPlano(nombre)
            Thread({
                try {
                    futuro.get()
                } catch (e: Exception) {
                    Log.w(TAG, "no se pudo inicializar $nombre: ${e.javaClass.simpleName}")
                }
                Handler(Looper.getMainLooper()).post(alIniciarse)
            }, "cerox-pantalla-$nombre").start()
        }
        return clase.cast(instancia)
    }
}

/**
 * Lo implementa quien puede repintar la interfaz al cambiar la paleta.
 *
 * Existe para que la pantalla de Diseño no dependa de una actividad concreta por
 * nombre: llama a la actividad a través de esta interfaz y, si no la implementa,
 * no pasa nada.
 */
interface AplicaDiseno {
    fun aplicarTema(id: String?)
}
