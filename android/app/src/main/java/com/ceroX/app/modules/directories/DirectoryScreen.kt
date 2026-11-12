package com.ceroX.app.modules.directories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ceroX.app.modules.web.MotorBusqueda
import kotlinx.coroutines.launch

@Composable
fun PantallaDirectorios(repositorio: DirectoryRepository, abrirWeb: (String) -> Unit) {
    var consulta by remember { mutableStateOf("") }
    var origen by remember { mutableStateOf("Todos") }
    var categoria by remember { mutableStateOf("Todas") }
    var adulto by remember { mutableStateOf(false) }
    var entradas by remember { mutableStateOf(repositorio.cachesLocales() + entradasAuditoria()) }
    var cargando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf("Sin sincronización automática. Elige un origen y actualízalo cuando quieras.") }
    val alcance = rememberCoroutineScope()

    val visibles = filtrarDirectorios(entradas, consulta, origen, categoria, adulto).map {
        if (it.origen == "brave" && consulta.isNotBlank()) {
            it.copy(url = MotorBusqueda.url("brave", consulta) ?: it.url, nombre = "Brave: $consulta")
        } else it
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            consulta, { consulta = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            singleLine = true, label = { Text("Buscar en directorios y metadatos") },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
            item { FilterChip(origen == "Todos", { origen = "Todos" }, { Text("Todos") }) }
            items(DIRECTORIOS) { d -> FilterChip(origen == d.id, { origen = d.id }, { Text(d.nombre) }) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
            items(CATEGORIAS_DIRECTORIO) { c -> FilterChip(categoria == c, { categoria = c }, { Text(c) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mostrar entradas 18+", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(adulto, { adulto = it })
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                if (cargando) return@Button
                cargando = true; aviso = "Actualizando…"
                alcance.launch {
                    val ids = if (origen == "Todos") DIRECTORIOS.map { it.id } else listOf(origen)
                    val nuevas = mutableListOf<EntradaDirectorio>()
                    val avisos = mutableListOf<String>()
                    for (id in ids) {
                        val r = repositorio.cargar(id, forzarRed = true)
                        nuevas += r.entradas
                        r.aviso?.let { avisos += "${id}: $it" }
                    }
                    entradas = entradas.filterNot { it.origen in ids } + nuevas
                    aviso = if (avisos.isEmpty()) "Actualización terminada; ${nuevas.size} entradas." else avisos.joinToString("\n")
                    cargando = false
                }
            }, enabled = !cargando) { Text(if (origen == "Todos") "Actualizar todos" else "Actualizar origen") }
            if (cargando) CircularProgressIndicator(Modifier.padding(8.dp))
        }
        Text("${visibles.size} resultados", Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.Bold)
        Text(
            "Descubrimiento y enlaces: no se ejecutan extensiones, no se instalan APK y «listado» no significa «seguro». " +
                "Appteka Rebuild no tiene código de la variante verificado. Las cachés viven en almacenamiento externo.",
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
        )
        Text(aviso, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
            color = if (aviso.contains("Fallo") || aviso.contains("Sin almacenamiento")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (visibles.isEmpty() && !cargando) {
            Text("No hay datos que coincidan. Actualiza el origen o cambia los filtros.", Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(visibles, key = { "${it.origen}:${it.id}:${it.url}" }) { entrada ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entrada.nombre, fontWeight = FontWeight.Bold)
                        Text("${DIRECTORIOS.firstOrNull { it.id == entrada.origen }?.nombre ?: entrada.origen} · ${entrada.categoria}",
                            style = MaterialTheme.typography.labelMedium)
                        if (entrada.descripcion.isNotBlank()) Text(entrada.descripcion, maxLines = 4, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                        if (entrada.plataformas.isNotEmpty()) Text(entrada.plataformas.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ abrirWeb(entrada.detalle) }) { Text("Ficha") }
                            TextButton({ abrirWeb(entrada.url) }) { Text(if (entrada.origen == "brave") "Buscar" else "Abrir enlace") }
                        }
                    }
                }
            }
        }
    }
}
