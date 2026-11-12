package com.ceroX.app.ui.dashboard

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.engine.ModuleHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    val moduleHealth = MutableLiveData<Map<String, ModuleHealth>>()

    /** La lectura y healthCheck no bloquean el hilo de interfaz. */
    fun refresh(app: CeroXApplication) {
        viewModelScope.launch(Dispatchers.IO) {
            moduleHealth.postValue(app.getAllHealth())
        }
    }
}
