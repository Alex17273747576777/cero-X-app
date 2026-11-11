package com.ceroX.app.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.modules.reverse_engineering.DeepReverseEngineeringModule

/**
 * La pantalla de pruebas de estrés.
 *
 * Pedía el módulo al motor legacy con un cast no nulo: la actividad se cerraba en
 * el onCreate. [StressTestWrapper] ya aceptaba un módulo nulo, así que lo único que
 * hacía falta era no reventar antes de llegar a ella.
 */
class StressTestActivity : AppCompatActivity() {

    private var reModule: DeepReverseEngineeringModule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CeroXApplication
        reModule = Pantallas.modulo(app, "deep-reverse-engineering", DeepReverseEngineeringModule::class.java)

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
