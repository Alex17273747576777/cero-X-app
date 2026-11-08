package com.ceroX.app.ui.main

import androidx.compose.material.icons.Icons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.modules.mode.ModeSwitcherFragment
import com.ceroX.app.modules.theme.ThemeSwitcherFragment
import com.ceroX.app.ui.theme.CeroXTheme

class MainActivity : AppCompatActivity() {

    private lateinit var app: CeroXApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as CeroXApplication

        setContent {
            CeroXTheme {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(text = "cero-X") },
                            actions = {
                                IconButton(onClick = { /* theme */ }) {
                                    Icon(imageVector = Icons.Default.Palette, contentDescription = "Tema")
                                }
                                IconButton(onClick = { /* refresh */ }) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Actualizar")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        CeroXBottomNav { selectedRoute ->
                            // Handle navigation
                        }
                    }
                ) { paddingValues ->
                    CeroXDashboardScreen(
                        engine = app.coreEngine,
                        modifier = Modifier.fillMaxSize().padding(paddingValues)
                    )
                }
            }
        }
    }

    fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun showThemeSwitcher() {
        showFragment(ThemeSwitcherFragment())
    }

    fun showModeSwitcher() {
        showFragment(ModeSwitcherFragment())
    }
}

/** Bottom navigation con todos los módulos de fusión */
@Composable
fun CeroXBottomNav(onRouteSelected: (String) -> Unit) {
    val items = listOf(
        BottomNavItem("dashboard", "Panel", Icons.Default.Dashboard),
        BottomNavItem("houbi", "Houbi Reader", Icons.Default.Book),
        BottomNavItem("unified-reader", "Lector Unificado", Icons.Default.Library_Books),
        BottomNavItem("novel", "Novelas", Icons.Default.Book),
        BottomNavItem("comic", "Cómics", Icons.Default.Menu_Book),
        BottomNavItem("booru", "Booru", Icons.Default.Image),
        BottomNavItem("prisma", "Prisma", Icons.Default.Image_Search_Manager),
        BottomNavItem("aihub", "AI Hub", Icons.Default.Smart_Assistant),
        BottomNavItem("kai", "Kia IA", Icons.Default.Psychology),
        BottomNavItem("sources", "RepoForge Sources", Icons.Default.Link),
        BottomNavItem("audio", "Audio", Icons.Default.Music_Note),
        BottomNavItem("video", "Video", Icons.Default.Videocam),
        BottomNavItem("ai", "IA Neural", Icons.Default.Psychology),
        BottomNavItem("media", "Multimedia", Icons.Default.Play_Circle),
        BottomNavItem("server", "Servidores", Icons.Default.Cloud),
        BottomNavItem("tools", "Herramientas", Icons.Default.Build),
        BottomNavItem("catalog", "Catálogo", Icons.Default.List),
        BottomNavItem("env", "Entornos", Icons.Default.Storage),
        BottomNavItem("re", "Ing. Inversa", Icons.Default.Code),
        BottomNavItem("updates", "Actualizar", Icons.Default.System_Update),
        BottomNavItem("sandbox", "SanTBox", Icons.Default.Security),
        BottomNavItem("factory", "Fábrica", Icons.Default.Factory),
        BottomNavItem("theme", "Tema/Diseño", Icons.Default.Palette),
        BottomNavItem("mode", "Modos", Icons.Default.Switch_Camera),
    )

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label) },
                selected = false,
                onClick = { onRouteSelected(item.route) }
            )
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** Pantalla de panel mostrando estado de salud de todos los módulos */
@Composable
fun CeroXDashboardScreen(
    engine: com.ceroX.app.core.engine.CoreEngine,
    modifier: Modifier = Modifier
) {
    val healthStates by remember { mutableStateOf(engine.healthCheckAll()) }

    Column(modifier = modifier) {
        Text(
            text = "cero-X — Fork Maestro de 17+ Ecosistemas",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        Text(
            text = "Houbi (PineappleTwilight/houri) = Lector Base Principal | Android 16 (API 36)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Text(
            text = "Módulos: ${healthStates.size} activos | Tema + Modos + Multiplataforma + Booru + Prisma + AI Hub + Kai",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(healthStates.entries.toList(), key = { it.key }) { (name, health) ->
                ModuleCard(
                    name = name,
                    version = health.version,
                    status = health.status,
                    details = health.details
                )
            }
        }

        Row(
            modifier = Modifier.padding(16.dp).align(alignment = androidx.compose.ui.Alignment.End),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { /* refresh */ }) {
                Text("Actualizar estado")
            }
            Button(onClick = { /* theme */ }) {
                Text("Diseño")
            }
            Button(onClick = { /* mode */ }) {
                Text("Modos")
            }
        }
    }
}

/** Tarjeta de salud de módulo */
@Composable
fun ModuleCard(
    name: String,
    version: String,
    status: String,
    details: Map<String, Any>
) {
    val color = when (status) {
        "healthy" -> MaterialTheme.colorScheme.primary
        "stopped" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.FontWeight.Bold
                )
                Text(
                    text = "v$version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    text = status.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = androidx.compose.ui.text.FontWeight.Bold
                )
                Text(
                    text = "Detalles: ${details.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}