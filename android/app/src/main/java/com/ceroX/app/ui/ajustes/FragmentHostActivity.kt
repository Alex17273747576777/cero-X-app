package com.ceroX.app.ui.ajustes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ceroX.app.R
import com.ceroX.app.modules.booru.BooruUIFragment
import com.ceroX.app.modules.catalog.CatalogUIFragment
import com.ceroX.app.modules.iptv.IptvUiFragment
import com.ceroX.app.modules.mode.ModeSwitcherFragment
import com.ceroX.app.modules.theme.ThemeSwitcherFragment

/** Aloja herramientas Views sin duplicar actividades ni dejar intents vacíos. */
class FragmentHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destino = intent.getStringExtra(EXTRA_DESTINO) ?: run { finish(); return }
        title = titulo(destino) ?: run { finish(); return }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(FrameLayout(this).apply { id = R.id.fragment_host_container })
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_host_container, fragmento(destino))
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
        private const val EXTRA_DESTINO = "destino"

        fun intento(context: Context, destino: String): Intent =
            Intent(context, FragmentHostActivity::class.java).putExtra(EXTRA_DESTINO, destino)

        private fun titulo(destino: String): String? = when (destino) {
            DISENO -> "Diseño"
            MODOS -> "Modos"
            IPTV -> "IPTV"
            APPS -> "Catálogo de apps"
            BOORU -> "Imágenes Safebooru"
            else -> null
        }

        private fun fragmento(destino: String): Fragment = when (destino) {
            DISENO -> ThemeSwitcherFragment()
            MODOS -> ModeSwitcherFragment()
            IPTV -> IptvUiFragment()
            APPS -> CatalogUIFragment()
            BOORU -> BooruUIFragment()
            else -> error("Destino desconocido")
        }
    }
}
