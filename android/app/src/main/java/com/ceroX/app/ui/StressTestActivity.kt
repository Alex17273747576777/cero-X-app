package com.ceroX.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.modules.reverse_engineering.DeepReverseEngineeringModule

class StressTestActivity : AppCompatActivity() {

    private lateinit var reModule: DeepReverseEngineeringModule

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CeroXApplication
        reModule = app.coreEngine.registry.getInstance("deep-reverse-engineering") as DeepReverseEngineeringModule

        setContent {
            com.ceroX.app.ui.theme.CeroXTheme {
                com.ceroX.app.ui.stress.StressTestWrapper(
                    reModule = reModule,
                    onBack = { finish() }
                )
            }
        }
    }
}