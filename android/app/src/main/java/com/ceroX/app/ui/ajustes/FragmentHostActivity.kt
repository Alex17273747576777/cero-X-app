package com.ceroX.app.ui.ajustes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ceroX.app.R
import com.ceroX.app.modules.booru.BooruPost
import com.ceroX.app.modules.booru.BooruUIFragment
import com.ceroX.app.modules.booru.VisorBooruFragment
import com.ceroX.app.modules.catalog.CatalogUIFragment
import com.ceroX.app.modules.iptv.IptvUiFragment
import com.ceroX.app.modules.mode.ModeSwitcherFragment
import com.ceroX.app.modules.theme.ThemeSwitcherFragment
import com.ceroX.app.ui.dashboard.ModuleDashboardFragment

/** Aloja herramientas Views sin duplicar actividades ni dejar intents vacíos. */
class FragmentHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destino = intent.getStringExtra(EXTRA_DESTINO) ?: run { finish(); return }
        title = titulo(destino) ?: run { finish(); return }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(FrameLayout(this).apply { id = R.id.fragment_host_container })
        if (savedInstanceState == null) {
            val fragment = fragmento(destino)
            // El visor necesita saber qué publicación mostrar; los argumentos van
            // en un Bundle aparte para no mezclarlos con el destino.
            intent.getBundleExtra(EXTRA_ARGUMENTOS)?.let { fragment.arguments = it }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_host_container, fragment)
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val DISENO = "diseno"
        const val MODOS = "modos"
        const val IPTV = "iptv"
        const val APPS = "apps"
        const val BOORU = "booru"
        const val MODULOS = "modulos"
        const val VISOR = "visor"
        private const val EXTRA_DESTINO = "destino"
        private const val EXTRA_ARGUMENTOS = "argumentos"

        fun intento(context: Context, destino: String): Intent =
            Intent(context, FragmentHostActivity::class.java).putExtra(EXTRA_DESTINO, destino)

        /** Visor de una publicación concreta, con sus datos como argumentos. */
        fun intentoVisor(context: Context, post: BooruPost): Intent =
            Intent(context, FragmentHostActivity::class.java)
                .putExtra(EXTRA_DESTINO, VISOR)
                .putExtra(EXTRA_ARGUMENTOS, VisorBooruFragment.argumentos(post))

        private fun titulo(destino: String): String? = when (destino) {
            DISENO -> "Diseño"
            MODOS -> "Modos"
            IPTV -> "IPTV"
            APPS -> "Catálogo de apps"
            BOORU -> "Imágenes Safebooru"
            MODULOS -> "Módulos del sistema"
            VISOR -> "Visor de imagen"
            else -> null
        }

        private fun fragmento(destino: String): Fragment = when (destino) {
            DISENO -> ThemeSwitcherFragment()
            MODOS -> ModeSwitcherFragment()
            IPTV -> IptvUiFragment()
            APPS -> CatalogUIFragment()
            BOORU -> BooruUIFragment()
            MODULOS -> ModuleDashboardFragment()
            VISOR -> VisorBooruFragment()
            else -> error("Destino desconocido")
        }
    }
}
