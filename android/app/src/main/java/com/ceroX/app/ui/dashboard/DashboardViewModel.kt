package com.ceroX.app.ui.dashboard

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.CoreEngine

class DashboardViewModel : ViewModel() {
    val moduleHealth = MutableLiveData<Map<String, ModuleHealth>>()

    fun refresh(engine: CoreEngine) {
        moduleHealth.value = engine.healthCheckAll()
    }
}