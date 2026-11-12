package com.ceroX.app.modules.adaptive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ceroX.app.core.AlmacenExterno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptationStoreTest {
    @Before fun limpiar() { AlmacenExterno.olvidar() }

    @Test fun persiste_externamente_deshace_limita_y_desactiva() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val store = AdaptationStore(contexto)
        repeat(405) { store.registrar("s", MotorAdaptativo.TipoEvento.ENLACE, "Manga", "miyomi", it.toLong()) }
        assertEquals(AdaptationStore.MAX_EVENTOS, store.eventos().size)
        assertEquals(5L, store.eventos().first().instante)
        assertTrue(AdaptationStore(contexto).eventos().isNotEmpty())
        assertTrue(store.deshacerUltimo("s"))
        assertFalse(store.deshacerUltimo("otra"))
        assertEquals(399, store.eventos().size)
        store.restablecer()
        assertTrue(store.eventos().isEmpty())
        store.establecerHabilitado(false)
        assertFalse(store.registrar("s", MotorAdaptativo.TipoEvento.ENLACE, "Manga", "miyomi"))
        assertFalse(AdaptationStore(contexto).habilitado())
    }
}
