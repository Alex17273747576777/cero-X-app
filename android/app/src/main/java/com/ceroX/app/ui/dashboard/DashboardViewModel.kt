package com.ceroX.app.ui.dashboard

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.engine.ModuleHealth

class DashboardViewModel : ViewModel() {
    val moduleHealth = MutableLiveData<Map<String, ModuleHealth>>()

    /**
     * La salud de todos los módulos, leída del gestor que de verdad los tiene.
     *
     * Recibía el motor legacy, que estaba vacío: el panel de módulos enseñaba cero
     * filas mientras el panel de arranque, que sí lee del gestor, enseñaba los 36.
     * Dos pantallas de la misma app en desacuerdo sobre cuántos módulos hay es el
     * tipo de cosa que se arregla leyendo las dos del mismo sitio.
     */
    fun refresh(app: CeroXApplication) {
        moduleHealth.value = app.getAllHealth()
    }
}
