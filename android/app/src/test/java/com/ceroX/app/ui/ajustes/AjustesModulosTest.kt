package com.ceroX.app.ui.ajustes

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AjustesModulosTest {
    @get:Rule val compose = createAndroidComposeRule<AjustesActivity>()

    @Test fun tocar_modulos_abre_su_host_real() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Módulos del sistema"))
        compose.onNodeWithText("Módulos del sistema").performClick()
        compose.waitForIdle()
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(FragmentHostActivity::class.java.name, intent.component?.className)
        assertEquals(FragmentHostActivity.MODULOS, intent.getStringExtra("destino"))
    }
}
