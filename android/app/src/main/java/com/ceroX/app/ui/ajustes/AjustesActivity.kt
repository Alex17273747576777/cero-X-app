package com.ceroX.app.ui.ajustes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ceroX.app.ui.estado.EstadoSistemaActivity
import com.ceroX.app.ui.idiomas.IdiomasActivity
import com.ceroX.app.ui.theme.CeroXTheme

/** Ajustes y herramientas secundarias; cada fila abre una pantalla existente. */
class AjustesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CeroXTheme {
                PantallaAjustes(onBack = { finish() })
            }
        }
    }
}

private data class AccionAjuste(
    val titulo: String,
    val detalle: String,
    val icono: ImageVector,
    val abrir: (Context) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(onBack: () -> Unit = {}) {
    val acciones = listOf(
        AccionAjuste("Diseño", "Paleta y apariencia de Compose", Icons.Default.Palette) {
            it.startActivity(FragmentHostActivity.intento(it, FragmentHostActivity.DISENO))
        },
        AccionAjuste("Modos y accesos", "Abrir lectura, fuentes, apps o IA", Icons.Default.Tune) {
            it.startActivity(FragmentHostActivity.intento(it, FragmentHostActivity.MODOS))
        },
        AccionAjuste("IPTV", "Fuentes M3U, canales y reproducción", Icons.Default.LiveTv) {
            it.startActivity(FragmentHostActivity.intento(it, FragmentHostActivity.IPTV))
        },
        AccionAjuste("Catálogo de apps", "Buscar, filtrar y abrir repositorios", Icons.Default.Apps) {
            it.startActivity(FragmentHostActivity.intento(it, FragmentHostActivity.APPS))
        },
        AccionAjuste("Imágenes Safebooru", "Búsqueda safe, miniaturas y favoritos", Icons.Default.Image) {
            it.startActivity(FragmentHostActivity.intento(it, FragmentHostActivity.BOORU))
        },
        AccionAjuste("Idiomas", "Idioma de la aplicación y traducciones", Icons.Default.Language) {
            it.startActivity(Intent(it, IdiomasActivity::class.java))
        },
        AccionAjuste("Módulos del sistema", "Filtrar, consultar, iniciar o detener", Icons.Default.Build) {
            it.startActivity(FragmentHostActivity.intento(it, FragmentHostActivity.MODULOS))
        },
        AccionAjuste("Estado del sistema", "Privilegios, fuentes y diagnóstico real", Icons.Default.Info) {
            it.startActivity(Intent(it, EstadoSistemaActivity::class.java))
        },
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes y herramientas") },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(acciones, key = { it.titulo }) { accion ->
                val context = LocalContext.current
                Card(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { accion.abrir(context) },
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) {
                        Icon(accion.icono, contentDescription = null)
                        androidx.compose.foundation.layout.Column(
                            Modifier.padding(start = 12.dp).weight(1f),
                        ) {
                            Text(accion.titulo, style = MaterialTheme.typography.titleMedium)
                            Text(accion.detalle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
