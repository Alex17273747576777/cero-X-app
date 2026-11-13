package com.ceroX.app.ui.dashboard

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.ceroX.app.R
import com.ceroX.app.core.AlmacenExterno
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.ui.ajustes.FragmentHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardAndroidTest {
    @Test fun pantalla_real_muestra_modulos_y_filtra() {
        AlmacenExterno.olvidar()
        val controller = Robolectric.buildActivity(
            FragmentHostActivity::class.java,
            FragmentHostActivity.intento(ApplicationProvider.getApplicationContext(), FragmentHostActivity.MODULOS),
        ).setup()
        try {
            val activity = controller.get()
            assertEquals("Módulos del sistema", activity.title.toString())
            val lista = activity.findViewById<RecyclerView>(R.id.recycler_view)
            val buscador = activity.findViewById<androidx.appcompat.widget.SearchView>(R.id.search_view)
            val app = activity.application as CeroXApplication
            val limite = System.currentTimeMillis() + 20_000
            while (lista.adapter?.itemCount == 0 && System.currentTimeMillis() < limite) {
                app.lifecycleManager.getAllHealth()
                shadowOf(activity.mainLooper).idle()
                Thread.sleep(25)
            }
            shadowOf(activity.mainLooper).idle()
            val total = lista.adapter?.itemCount ?: 0
            assertTrue("El panel no recibió la salud del gestor", total > 0)
            val nombre = "houbi-reader"
            assertTrue(app.lifecycleManager.getRegistry().listModules().contains(nombre))
            buscador.setQuery(nombre, false)
            shadowOf(activity.mainLooper).idle()
            val limiteFiltro = System.currentTimeMillis() + 5_000
            while (lista.adapter?.itemCount != 1 && System.currentTimeMillis() < limiteFiltro) {
                shadowOf(activity.mainLooper).idle()
                Thread.sleep(25)
            }
            shadowOf(activity.mainLooper).idle()
            assertEquals(1, lista.adapter?.itemCount)
            lista.layoutManager?.scrollToPosition(0)
            lista.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), 0)
            lista.layout(0, 0, 1080, 1920)
            shadowOf(activity.mainLooper).idle()
            val fila = lista.findViewHolderForAdapterPosition(0)?.itemView
            assertNotNull("La fila filtrada no se pudo materializar", fila)
            fila!!.performClick()
            shadowOf(activity.mainLooper).idle()
            val dialogo = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertNotNull("La fila no abrió el diálogo de detalles", dialogo)
            assertTrue(dialogo!!.isShowing)
            assertTrue(dialogo.findViewById<TextView>(android.R.id.message).text.contains("Ciclo:"))
            assertNotNull(activity.findViewById<TextView>(R.id.dashboard_status))
        } finally { controller.pause().stop().destroy() }
    }
}
