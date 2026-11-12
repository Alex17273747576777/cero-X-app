package com.ceroX.app.ui.extensiones

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ceroX.app.modules.extension_sandbox.ClienteRepositorios
import com.ceroX.app.modules.extension_sandbox.ExtensionIndice
import com.ceroX.app.modules.extension_sandbox.FuenteKotatsu
import com.ceroX.app.modules.extension_sandbox.InstaladorProjectNox
import com.ceroX.app.modules.extension_sandbox.RepositoriosAuditados
import com.ceroX.app.ui.theme.CeroXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class IntegracionesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Fuentes y extensiones"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContent {
            CeroXTheme {
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Fuentes y extensiones") })
                }) { padding ->
                    PantallaIntegraciones(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        cliente = ClienteRepositorios(),
                        instalar = { extension -> InstaladorProjectNox(applicationContext).prepararEInstalar(extension) },
                        abrirBiblioteca = {
                            startActivity(Intent(this, KisaraActivity::class.java))
                        },
                        abrirWeb = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
                    )
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack() else finish()
        return true
    }
}

@Composable
fun PantallaIntegraciones(
    modifier: Modifier = Modifier,
    cliente: ClienteRepositorios,
    instalar: (ExtensionIndice) -> String,
    abrirBiblioteca: () -> Unit,
    abrirWeb: (String) -> Unit,
) {
    var pestana by remember { mutableIntStateOf(0) }
    var consulta by remember { mutableStateOf("") }
    var incluirNsfw by remember { mutableStateOf(false) }
    var ocultarRotas by remember { mutableStateOf(true) }
    var nox by remember { mutableStateOf<List<ExtensionIndice>>(emptyList()) }
    var kotatsu by remember { mutableStateOf<List<FuenteKotatsu>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }
    var instalando by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()

    fun cargarNox() {
        if (cargando) return
        val repo = if (pestana == 0) RepositoriosAuditados.MANGA else RepositoriosAuditados.ANIME
        cargando = true; error = ""
        alcance.launch {
            try {
                val cargadas = withContext(Dispatchers.IO) { cliente.nox(repo) }
                nox = nox.filterNot { it.repositorio == repo.id } + cargadas
            } catch (e: Exception) { error = e.message ?: "No se pudo leer Project Nox" }
            cargando = false
        }
    }
    fun cargarKotatsu() {
        if (cargando) return
        cargando = true; error = ""
        alcance.launch {
            try { kotatsu = withContext(Dispatchers.IO) { cliente.kotatsu() } }
            catch (e: Exception) { error = e.message ?: "No se pudo leer Kotatsu" }
            cargando = false
        }
    }
    LaunchedEffect(pestana) {
        val repoId = if (pestana == 0) "nox-manga" else "nox-anime"
        if (pestana < 2 && nox.none { it.repositorio == repoId }) cargarNox()
        if (pestana == 2 && kotatsu.isEmpty()) cargarKotatsu()
    }

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = pestana) {
            Tab(pestana == 0, { pestana = 0; error = "" }, text = { Text("Manga Nox") })
            Tab(pestana == 1, { pestana = 1; error = "" }, text = { Text("Anime Nox") })
            Tab(pestana == 2, { pestana = 2; error = "" }, text = { Text("Kotatsu") })
        }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                consulta, { consulta = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (pestana == 2) "Buscar fuente, idioma o tipo" else "Buscar nombre, paquete o idioma") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar NSFW", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(incluirNsfw, { incluirNsfw = it })
                if (pestana == 2) {
                    Text("Ocultar rotas", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                    Switch(ocultarRotas, { ocultarRotas = it })
                }
            }
        }
        when {
            cargando -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(); Text(" Leyendo instantáneas auditadas…", Modifier.padding(8.dp))
            }
            error.isNotBlank() -> Column(Modifier.padding(16.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Button({ if (pestana == 2) cargarKotatsu() else cargarNox() }) { Text("Reintentar") }
            }
            pestana < 2 -> CatalogoNox(
                nox.filter { it.repositorio == if (pestana == 0) "nox-manga" else "nox-anime" },
                consulta, incluirNsfw, instalando, aviso, abrirBiblioteca, abrirWeb,
            ) { extension ->
                if (instalando != null) return@CatalogoNox
                instalando = extension.paquete; aviso = "Descargando y comprobando ${extension.nombre}…"
                alcance.launch {
                    try { aviso = withContext(Dispatchers.IO) { instalar(extension) } }
                    catch (e: Exception) { aviso = "No instalada: ${e.message ?: e.javaClass.simpleName}" }
                    instalando = null
                }
            }
            else -> ExploradorKotatsu(kotatsu, consulta, incluirNsfw, ocultarRotas, abrirWeb)
        }
    }
}

@Composable
private fun CatalogoNox(
    extensiones: List<ExtensionIndice>, consulta: String, incluirNsfw: Boolean,
    instalando: String?, aviso: String, abrirBiblioteca: () -> Unit,
    abrirWeb: (String) -> Unit, instalar: (ExtensionIndice) -> Unit,
) {
    val filtradas = extensiones.filter {
        (incluirNsfw || !it.nsfw) && (consulta.isBlank() || listOf(it.nombre, it.paquete, it.idioma)
            .any { campo -> campo.contains(consulta.trim(), true) })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${extensiones.size} extensiones · ${filtradas.size} coinciden", fontWeight = FontWeight.Bold)
                Text(
                    "Instantánea fijada al commit ${extensiones.firstOrNull()?.let { if (it.repositorio == "nox-manga") "55b9127e" else "d2b689f3" } ?: "auditado"}. " +
                        "Se comprueba el certificado Project Nox y el SHA-256 cuando se publica. " +
                        "La instalación es para Mihon/Aniyomi: el contrato de ejecución dentro de cero-X " +
                        "aún está en migración y no se presenta como operativo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    TextButton(abrirBiblioteca) { Text("Biblioteca Kisara") }
                    TextButton({ abrirWeb("https://github.com/Awerkori") }) { Text("Código y licencias") }
                }
                if (aviso.isNotBlank()) Text(aviso, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
            }
        }
        items(filtradas.take(100), key = { it.paquete }) { extension ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(extension.nombre, style = MaterialTheme.typography.titleMedium)
                Text("${extension.idioma} · v${extension.version} · ${extension.fuentes.size} fuente(s)" +
                    if (extension.nsfw) " · NSFW" else "", style = MaterialTheme.typography.bodySmall)
                Text(
                    extension.fuentes.take(3).joinToString(" · ") { it.baseUrl.ifBlank { it.nombre } },
                    style = MaterialTheme.typography.bodySmall, maxLines = 2,
                )
                Text(
                    if (extension.sha256 != null) "SHA-256 publicado y obligatorio" else "Sin hash en el índice: se exige firma Project Nox",
                    style = MaterialTheme.typography.labelSmall,
                )
                Button(
                    onClick = { instalar(extension) },
                    enabled = instalando == null,
                ) { Text(if (instalando == extension.paquete) "Comprobando…" else "Verificar e instalar para Mihon/Aniyomi") }
            }
            HorizontalDivider()
        }
        if (filtradas.size > 100) item { Text("Refina la búsqueda: se muestran 100 de ${filtradas.size}.", Modifier.padding(16.dp)) }
    }
}

@Composable
private fun ExploradorKotatsu(
    fuentes: List<FuenteKotatsu>, consulta: String, incluirNsfw: Boolean,
    ocultarRotas: Boolean, abrirWeb: (String) -> Unit,
) {
    val filtradas = fuentes.filter {
        (incluirNsfw || !it.nsfw) && (!ocultarRotas || !it.rota) &&
            (consulta.isBlank() || listOf(it.nombre, it.titulo, it.idioma, it.tipo, it.repositorio)
                .any { campo -> campo.contains(consulta.trim(), true) })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${fuentes.size} fuentes indexadas · ${fuentes.count { it.rota }} marcadas rotas", fontWeight = FontWeight.Bold)
                Text(
                    "Explorador, no ejecución de parsers. Los JAR Kotatsu necesitan el contrato MangaLoaderContext " +
                        "y compatibilidad con cero-X; un enlace o un índice no demuestra que funcionen. " +
                        "El índice skepsun estaba vacío en la instantánea auditada.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton({ abrirWeb("https://github.com/Shebyyy/kotatsu-multi-parsers") }) { Text("Procedencia y builds") }
                HorizontalDivider()
            }
        }
        items(filtradas.take(120), key = { "${it.repositorio}:${it.nombre}" }) { fuente ->
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(fuente.titulo, style = MaterialTheme.typography.titleMedium)
                Text("${fuente.repositorio.removePrefix("kotatsu-")} · ${fuente.idioma} · ${fuente.tipo}" +
                    (if (fuente.rota) " · ROTA según el índice" else "") + if (fuente.nsfw) " · NSFW" else "")
                if (fuente.baseUrl.isNotBlank()) Text(
                    fuente.baseUrl + if (fuente.baseUrl.startsWith("http://")) " (HTTP: sólo metadato; no se abre)" else "",
                    color = if (fuente.baseUrl.startsWith("https://")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (fuente.baseUrl.startsWith("https://")) {
                        Modifier.clickable { abrirWeb(fuente.baseUrl) }
                    } else Modifier,
                )
            }
            HorizontalDivider()
        }
        if (filtradas.size > 120) item { Text("Refina la búsqueda: se muestran 120 de ${filtradas.size}.", Modifier.padding(16.dp)) }
    }
}
