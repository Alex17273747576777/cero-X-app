package com.ceroX.app.ui.main

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.ceroX.app.R
import com.ceroX.app.core.AplicaDiseno
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.core.engine.ReglasArranque
import com.ceroX.app.modules.mode.ModeSwitcherFragment
import com.ceroX.app.modules.theme.ThemeEngineModule
import com.ceroX.app.modules.theme.ThemeSwitcherFragment
import com.ceroX.app.modules.theme.UIThemeManager
import com.ceroX.app.ui.catalogo.CatalogoActivity
import com.ceroX.app.ui.estado.EstadoSistemaActivity
import com.ceroX.app.ui.theme.CeroXTheme

/**
 * MainActivity — la pantalla que ve el usuario al abrir cero-X.
 *
 * QUÉ CAMBIÓ EN LA 1.0.1 Y POR QUÉ
 * --------------------------------
 * La barra inferior cambiaba un estado de Compose (`seleccionado`) que **nadie
 * leía**: el contenido era un `FragmentContainerView` nacido vacío, y por eso la
 * pantalla se veía en blanco con seis botones que no hacían nada. Eso es peor
 * que no tener barra: promete navegación y no la da.
 *
 * Ahora `seleccionado` decide el contenido de verdad: «Panel» monta el panel de
 * estado de los módulos (leído del gestor en el instante, con botón para iniciar
 * los perezosos), y cada otro destino monta su Fragment real en el contenedor.
 * Un destino sin pantalla no aparece en la barra.
 */
class MainActivity : AppCompatActivity(), AplicaDiseno {

    private lateinit var app: CeroXApplication

    /**
     * El esquema de colores elegido, o null para el de siempre.
     *
     * Es estado de Compose fuera de la composición a propósito: lo escribe
     * [aplicarTema], al que llama la pantalla de Diseño cuando el usuario elige una
     * paleta, y en cuanto cambia se repinta todo lo que cuelga de `CeroXTheme`.
     */
    private val paleta = mutableStateOf<ColorScheme?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as CeroXApplication
        aplicarTema(null)

        setContent {
            CeroXTheme(colorSchemeOverride = paleta.value) {
                CeroXApp(
                    app = app,
                    onThemeClick = { showFragment(ThemeSwitcherFragment()) },
                    onModeClick = { showFragment(ModeSwitcherFragment()) },
                    // Abrir el catálogo es lo que convierte «hay módulos» en
                    // «hay algo que leer». Antes no había ningún camino desde
                    // esta pantalla hasta el lector: existía la clase y no se
                    // podía llegar a ella.
                    onReadClick = {
                        startActivity(Intent(this, CatalogoActivity::class.java))
                    },
                    onEstadoClick = {
                        startActivity(Intent(this, EstadoSistemaActivity::class.java))
                    },
                    onMostrarFragmento = { showFragment(it) },
                )
            }
        }
    }

    /**
     * Aplica la paleta `id`, o la que ya estuviera elegida si es null.
     *
     * Convierte el `AppTheme` del motor en un `ColorScheme` con `UIThemeManager` y lo
     * guarda en [paleta]; Compose se repinta solo. Si el motor de temas no está
     * registrado, no se toca nada y la app sigue con sus colores.
     */
    override fun aplicarTema(id: String?) {
        if (!this::app.isInitialized) return
        val motor = Pantallas.modulo(app, "theme-engine", ThemeEngineModule::class.java) ?: return
        if (id != null) motor.setTheme(id)
        val tema = motor.getCurrentTheme() ?: return
        val noche = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        paleta.value = UIThemeManager(this).colorSchemeFor(tema, isDark = noche)
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
    // Un destino es un Fragment que existe: esta entrada se añade con la pantalla
    // hecha, no antes. Una barra con un botón que no lleva a ningún sitio es justo
    // lo que hacía que esta app se viera en blanco.
    Destino("iptv", "IPTV") { com.ceroX.app.modules.iptv.IptvUiFragment() },
    Destino("web", "Web") { com.ceroX.app.modules.web.WebUiFragment() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CeroXApp(
    app: CeroXApplication,
    onThemeClick: () -> Unit,
    onModeClick: () -> Unit,
    onReadClick: () -> Unit = {},
    onEstadoClick: () -> Unit = {},
    onMostrarFragmento: (Fragment) -> Unit = {},
) {
    var seleccionado by remember { mutableStateOf(DESTINOS.first().id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("cero-X") },
                actions = {
                    IconButton(onClick = onReadClick) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Leer")
                    }
                    IconButton(onClick = onThemeClick) {
                        Icon(Icons.Default.Refresh, contentDescription = "Diseño")
                    }
                    IconButton(onClick = onEstadoClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Estado del sistema")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                DESTINOS.forEach { destino ->
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        label = { Text(destino.etiqueta) },
                        selected = seleccionado == destino.id,
                        onClick = { seleccionado = destino.id },
                    )
                }
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    label = { Text("Ir a modos") },
                    selected = false,
                    onClick = onModeClick,
                )
            }
        },
    ) { paddingValues ->
        // El estado elegido decide el contenido. Antes decidía nada.
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (seleccionado == "panel") {
                PanelDeModulos(app, Modifier.fillMaxSize())
            } else {
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

    LaunchedEffect(seleccionado) {
        if (seleccionado != "panel") {
            DESTINOS.firstOrNull { it.id == seleccionado }?.let { onMostrarFragmento(it.crear()) }
        }
    }
}

/**
 * El panel de arranque honesto: el estado real de cada módulo, leído del gestor
 * en el instante de abrir o de pulsar actualizar. Los perezosos tienen botón para
 * iniciarse; los que fallaron dicen que fallaron y cuántas veces se reintentó.
 */
@Composable
fun PanelDeModulos(app: CeroXApplication, modifier: Modifier = Modifier) {
    var resumen by remember { mutableStateOf(emptyList<ReglasArranque.ResumenModulo>()) }
    LaunchedEffect(Unit) { resumen = app.lifecycleManager.resumen() }

    val listos = resumen.count { it.estado == "listo" || it.estado == "funcionando" }
    val fallidos = resumen.count { it.estado == "falló" }
    val perezosos = resumen.count { it.pesado && it.estado.startsWith("pendiente") }

    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Estado de los módulos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { resumen = app.lifecycleManager.resumen() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar estado")
            }
        }
        Text(
            "$listos de ${resumen.size} listos · $perezosos perezosos · $fallidos fallidos",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Leído del gestor en este instante; nada de esto está escrito a mano.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        resumen.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(m.nombre, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${m.version} · ${m.estado}" +
                            if (m.reintentos > 0) " · reintentos: ${m.reintentos}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (m.pesado && m.estado.startsWith("pendiente")) {
                    Button(onClick = {
                        app.lifecycleManager.iniciarEnSegundoPlano(m.nombre)
                        // El inicio vive en otro hilo: se vuelve a leer el estado
                        // cuando ha tenido tiempo de resolverse, y el panel enseña
                        // lo que haya, sea listo o sea falló.
                        Thread({
                            Thread.sleep(1500)
                            resumen = app.lifecycleManager.resumen()
                        }, "cerox-refresco-panel").start()
                    }) { Text("Iniciar") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
