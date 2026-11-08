package com.ceroX.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * ConnectivityReceiver — Receptor de cambios de conectividad de red.
 *
 * Notifica al EventBus cuando cambia el estado de conectividad para que
 * los módulos puedan pausar/reanudar descargas y sincronización.
 */
class ConnectivityReceiver : BroadcastReceiver() {

    private val TAG = "ConnectivityReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ConnectivityManager.CONNECTIVITY_ACTION) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val isConnected = if (activeNetwork != null) {
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                false
            }

            Log.i(TAG, "Conectividad cambiada: conectado=$isConnected")

            try {
                val app = context.applicationContext as CeroXApplication
                app.coreEngine.eventBus.publish("network.changed", isConnected)
            } catch (e: Exception) {
                Log.e(TAG, "Error publicando evento de red", e)
            }
        }
    }
}