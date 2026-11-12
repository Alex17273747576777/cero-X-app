package com.ceroX.app.modules.mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModosNavegablesTest {
    @Test
    fun solo_se_ofrecen_modos_con_destino_android() {
        val gestor = ModeManagerModule()
        assertEquals(ModeManagerModule.MODOS_NAVEGABLES, gestor.listModes().map { it.id }.toSet())
        assertFalse("Audio no tiene pantalla y no debe activarse", gestor.setMode("audio"))
        assertFalse(gestor.setMode("video_booru"))
        assertTrue(gestor.setMode("manga_comics"))
        assertEquals("manga_comics", gestor.getCurrentMode()?.id)
    }
}
