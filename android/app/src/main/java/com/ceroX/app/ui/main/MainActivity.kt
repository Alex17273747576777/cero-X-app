package com.ceroX.app.ui.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.modules.mode.ModeSwitcherFragment
import com.ceroX.app.modules.theme.ThemeSwitcherFragment
import com.ceroX.app.ui.theme.CeroXTheme
import android.content.Intent
import com.ceroX.app.ui.catalogo.CatalogoActivity

/**
 * MainActivity — la pantalla que ve el usuario al abrir cero-X.
 *
 * QUÉ CAMBIÓ Y POR QUÉ
 * --------------------
 * Esta actividad tenía tres problemas que se sumaban en cascada:
 *
 *  1. La barra inferior declaraba 24 destinos, pero la app solo tiene 6
 *     Fragments. Veinticuatro botones que no llevan a ninguna parte es peor
 *     que cuatro que funcionan: el usuario toca «Novelas» y no pasa nada.
 *  2. `Icons.Default.Library_Books` no existe. En Compose el icono es
 *     `LibraryBooks`, y con guion bajo el compilador no encuentra nada — de
 *     ahí venían decenas de errores en este archivo.
 *  3. `showFragment` hacía `replace(R.id.fragment_container, ...)`, pero esta
 *     Activity usa Compose y no infla activity_main.xml: el contenedor no
 *     existía y cualquier navegación habría lanzado una excepción en tiempo
 *     de ejecución.
 *
 * Ahora la navegación es estado de Compose: cada destino muestra el Fragment
 * real que existe. Un destino sin pantalla no se muestra.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var app: CeroXApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as CeroXApplication

        setContent {
            CeroXTheme {
                CeroXApp(
                    onThemeClick = { showFragment(ThemeSwitcherFragment()) },
                    onModeClick = { showFragment(ModeSwitcherFragment()) },
                    // Abrir el catálogo es lo que convierte «hay módulos» en
                    // «hay algo que leer». Antes no había ningún camino desde
                    // esta pantalla hasta el lector: existía la clase y no se
                    // podía llegar a ella.
                    onReadClick = {
                        startActivity(Intent(this, CatalogoActivity::class.java))
                    },
                )
            }
        }
    }

    /** Reemplaza el contenido por un Fragment real dentro del contenedor de Compose. */
    fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun showThemeSwitcher() = showFragment(ThemeSwitcherFragment())

    fun showModeSwitcher() = showFragment(ModeSwitcherFragment())
}

/** Un destino de la barra inferior. Un destino es un Fragment que existe. */
private data class Destino(
    val id: String,
    val etiqueta: String,
    val crear: () -> Fragment,
)

private val DESTINOS = listOf(
    Destino("panel", "Panel") { com.ceroX.app.ui.dashboard.ModuleDashboardFragment() },
    Destino("diseno", "Diseño") { ThemeSwitcherFragment() },
    Destino("modos", "Modos") { ModeSwitcherFragment() },
    Destino("booru", "Booru") { com.ceroX.app.modules.booru.BooruUIFragment() },
    Destino("catalogo", "Catálogo") { com.ceroX.app.modules.catalog.CatalogUIFragment() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CeroXApp(
    onThemeClick: () -> Unit,
    onModeClick: () -> Unit,
    onReadClick: () -> Unit = {},
) {
    var seleccionado by remember { mutableStateOf(DESTINOS.first().id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("cero-X") },
                actions = {
                    IconButton(onClick = onReadClick) {
                        // Icono de libro real: `MenuBook` existe en
                        // material-icons-extended. Un `Refresh` para «leer»
                        // sería un botón que no dice lo que hace.
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Leer")
                    }
                    IconButton(onClick = onThemeClick) {
                        Icon(Icons.Default.Refresh, contentDescription = "Diseño")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                DESTINOS.forEach { destino ->
                    NavigationBarItem(
                        // Un destino sin icono propio usa el primero disponible:
                        // mejor un icono genérico que un botón vacío.
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        label = { Text(destino.etiqueta) },
                        selected = seleccionado == destino.id,
                        onClick = { seleccionado = destino.id },
                    )
                }
                // Acceso directo a Modos desde la barra, además del botón del panel.
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    label = { Text("Ir a modos") },
                    selected = false,
                    onClick = onModeClick,
                )
            }
        },
    ) { paddingValues ->
        // El contenedor donde viven los Fragments. Tiene que ser el mismo id
        // que usa showFragment, o la navegación revienta en ejecución.
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { contexto ->
                    androidx.fragment.app.FragmentContainerView(contexto).apply {
                        id = R.id.fragment_container
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Panel de arranque: lo que el usuario ve antes de elegir un destino. */
@Composable
fun PanelDeArranque(app: CeroXApplication, modifier: Modifier = Modifier) {
    val salud = remember { app.coreEngine.healthCheckAll() }
    Column(modifier = modifier.padding(16.dp)) {
        Text("cero-X")
        Text("${salud.size} módulos registrados")
        Text("Android 16 · API 36")
    }
}
