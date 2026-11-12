package com.ceroX.app.ui.main

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.ceroX.app.R
import com.ceroX.app.core.AplicaDiseno
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.core.engine.ReglasArranque
import com.ceroX.app.modules.mode.ModeSwitcherFragment
import com.ceroX.app.modules.theme.ThemeEngineModule
import com.ceroX.app.modules.theme.ThemeSwitcherFragment
import com.ceroX.app.modules.theme.UIThemeManager
import com.ceroX.app.modules.web.WebUiFragment
import com.ceroX.app.ui.ajustes.AjustesActivity
import com.ceroX.app.ui.catalogo.CatalogoActivity
import com.ceroX.app.ui.estado.EstadoSistemaActivity
import com.ceroX.app.ui.extensiones.IntegracionesActivity
import com.ceroX.app.ui.ia.PantallaIaActivity
import com.ceroX.app.ui.theme.CeroXTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Única actividad principal: cinco salidas claras y ninguna acción decorativa. */
class MainActivity : AppCompatActivity(), AplicaDiseno {
    private lateinit var app: CeroXApplication
    private val paleta = mutableStateOf<ColorScheme?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as CeroXApplication
        aplicarTema(null)
        setContent {
            CeroXTheme(colorSchemeOverride = paleta.value) {
                CeroXApp(
                    app = app,
                    onReadClick = { startActivity(Intent(this, CatalogoActivity::class.java)) },
                    onEstadoClick = { startActivity(Intent(this, EstadoSistemaActivity::class.java)) },
                    onIaClick = { startActivity(Intent(this, PantallaIaActivity::class.java)) },
                    onExtensionesClick = { startActivity(Intent(this, IntegracionesActivity::class.java)) },
                    onAjustesClick = { startActivity(Intent(this, AjustesActivity::class.java)) },
                    onMostrarFragmento = { mostrarDestino(it) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Una paleta elegida desde Ajustes se aplica al regresar a Compose.
        if (this::app.isInitialized) aplicarTema(null)
    }

    override fun aplicarTema(id: String?) {
        if (!this::app.isInitialized) return
        val motor = Pantallas.modulo(app, "theme-engine", ThemeEngineModule::class.java) ?: return
        if (id != null) motor.setTheme(id)
        val tema = motor.getCurrentTheme() ?: return
        val noche = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        paleta.value = UIThemeManager(this).colorSchemeFor(tema, isDark = noche)
    }

    /** Los destinos principales no acumulan una pila de copias al cambiar de pestaña. */
    private fun mostrarDestino(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss()
    }

    /** Fragmentos secundarios llamados por otras pantallas sí conservan Atrás. */
    fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun showThemeSwitcher() = showFragment(ThemeSwitcherFragment())
    fun showModeSwitcher() = showFragment(ModeSwitcherFragment())
}

private data class Destino(
    val id: String,
    val etiqueta: String,
    val icono: ImageVector,
    val descripcion: String,
    val crear: (() -> Fragment)? = null,
)

private val DESTINOS = listOf(
    Destino("panel", "Inicio", Icons.Default.Home, "Ir al inicio"),
    Destino("biblioteca", "Biblioteca", Icons.AutoMirrored.Filled.MenuBook, "Abrir biblioteca y catálogo"),
    Destino("fuentes", "Fuentes", Icons.Default.Extension, "Abrir fuentes y extensiones"),
    Destino("web", "Web", Icons.Default.Language, "Abrir navegador") { WebUiFragment() },
    Destino("ia", "IA", Icons.Default.SmartToy, "Abrir asistente y modelos"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CeroXApp(
    app: CeroXApplication,
    onReadClick: () -> Unit = {},
    onEstadoClick: () -> Unit = {},
    onIaClick: () -> Unit = {},
    onExtensionesClick: () -> Unit = {},
    onAjustesClick: () -> Unit = {},
    onMostrarFragmento: (Fragment) -> Unit = {},
) {
    var seleccionado by rememberSaveable { mutableStateOf(DESTINOS.first().id) }
    val titulo = DESTINOS.firstOrNull { it.id == seleccionado }?.etiqueta ?: "cero-X"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (seleccionado == "panel") "cero-X" else titulo) },
                actions = {
                    IconButton(onClick = onAjustesClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Abrir ajustes")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                DESTINOS.forEach { destino ->
                    NavigationBarItem(
                        icon = { Icon(destino.icono, contentDescription = null) },
                        label = { Text(destino.etiqueta, maxLines = 1) },
                        selected = seleccionado == destino.id,
                        onClick = {
                            when (destino.id) {
                                "biblioteca" -> onReadClick()
                                "fuentes" -> onExtensionesClick()
                                "ia" -> onIaClick()
                                else -> seleccionado = destino.id
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = destino.descripcion },
                    )
                }
            }
        },
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            if (seleccionado == "panel") {
                PanelDeModulos(
                    app = app,
                    modifier = Modifier.fillMaxSize(),
                    onReadClick = onReadClick,
                    onExtensionesClick = onExtensionesClick,
                    onIaClick = onIaClick,
                    onEstadoClick = onEstadoClick,
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        FragmentContainerView(context).apply { id = R.id.fragment_container }
                    },
                )
            }
        }
    }

    LaunchedEffect(seleccionado) {
        DESTINOS.firstOrNull { it.id == seleccionado }?.crear?.let { onMostrarFragmento(it()) }
    }
}

/** Inicio con recorridos reales y estado leído del gestor, no tarjetas de relleno. */
@Composable
fun PanelDeModulos(
    app: CeroXApplication,
    modifier: Modifier = Modifier,
    onReadClick: () -> Unit = {},
    onExtensionesClick: () -> Unit = {},
    onIaClick: () -> Unit = {},
    onEstadoClick: () -> Unit = {},
) {
    var resumen by remember { mutableStateOf(emptyList<ReglasArranque.ResumenModulo>()) }
    val alcance = rememberCoroutineScope()
    LaunchedEffect(Unit) { resumen = app.lifecycleManager.resumen() }

    val listos = resumen.count { it.estado == "listo" || it.estado == "funcionando" }
    val fallidos = resumen.count { it.estado == "falló" }
    val perezosos = resumen.count { it.pesado && it.estado.startsWith("pendiente") }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("¿Qué quieres hacer?", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Lee tus fuentes, descubre extensiones o explora la web. Cada acceso abre una pantalla real.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onReadClick, Modifier.fillMaxWidth()) { Text("Buscar algo para leer") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onExtensionesClick, Modifier.weight(1f)) { Text("Fuentes") }
                        OutlinedButton(onIaClick, Modifier.weight(1f)) { Text("Asistente IA") }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Estado de la aplicación", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$listos de ${resumen.size} módulos listos · $perezosos diferidos · $fallidos fallidos",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onEstadoClick) {
                        Icon(Icons.Default.Info, contentDescription = "Ver diagnóstico completo")
                    }
                    IconButton(onClick = { resumen = app.lifecycleManager.resumen() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar estado")
                    }
                }
            }
        }
        item {
            Text("Módulos", style = MaterialTheme.typography.titleMedium)
            Text(
                "Los módulos diferidos se inician sólo cuando los pides. El estado se vuelve a leer después.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(resumen, key = { it.nombre }) { modulo ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(modulo.nombre, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${modulo.version} · ${modulo.estado}" +
                                if (modulo.reintentos > 0) " · reintentos: ${modulo.reintentos}" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (modulo.pesado && modulo.estado.startsWith("pendiente")) {
                        OutlinedButton(onClick = {
                            app.lifecycleManager.iniciarEnSegundoPlano(modulo.nombre)
                            alcance.launch {
                                delay(1500)
                                resumen = app.lifecycleManager.resumen()
                            }
                        }) { Text("Iniciar") }
                    }
                }
            }
        }
    }
}
