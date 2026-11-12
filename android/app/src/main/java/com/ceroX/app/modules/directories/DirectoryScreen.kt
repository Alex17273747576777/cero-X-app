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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ceroX.app.modules.adaptive.AdaptationStore
import com.ceroX.app.modules.adaptive.MotorAdaptativo
import com.ceroX.app.modules.web.MotorBusqueda
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PantallaDirectorios(
    repositorio: DirectoryRepository,
    adaptacion: AdaptationStore,
    abrirWeb: (String) -> Unit,
) {
    var consulta by rememberSaveable { mutableStateOf("") }
    var origen by rememberSaveable { mutableStateOf("Todos") }
    var categoria by rememberSaveable { mutableStateOf("Todas") }
    var adulto by rememberSaveable { mutableStateOf(false) }
    var entradas by remember { mutableStateOf(repositorio.cachesLocales() + entradasAuditoria()) }
    var cargando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf("Sin sincronización automática. Elige un origen y actualízalo cuando quieras.") }
    val sesion = rememberSaveable { UUID.randomUUID().toString() }
    var eventos by remember { mutableStateOf(adaptacion.eventos()) }
    var habilitado by remember { mutableStateOf(adaptacion.habilitado()) }
    var revisionAdaptacion by remember { mutableIntStateOf(0) }
    var señalesPendientes by remember { mutableIntStateOf(0) }
    val alcance = rememberCoroutineScope()

    fun registrar(tipo: MotorAdaptativo.TipoEvento, cat: String, fuente: String) {
        if (adaptacion.registrar(sesion, tipo, cat, fuente)) {
            eventos = adaptacion.eventos()
            señalesPendientes++
        }
    }

    val visibles = filtrarDirectorios(entradas, consulta, origen, categoria, adulto).map {
        if (it.origen == "brave" && consulta.isNotBlank()) {
            it.copy(url = MotorBusqueda.url("brave", consulta) ?: it.url, nombre = "Brave: $consulta")
        } else it
    }
    val perfil = remember(categoria, revisionAdaptacion, habilitado) {
        MotorAdaptativo.perfil(eventos, sesion, categoria, habilitado)
    }
    val recomendaciones = MotorAdaptativo.ordenar(visibles, perfil, consulta)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            consulta, { consulta = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            singleLine = true, label = { Text("Buscar en directorios y metadatos") },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
            item { FilterChip(origen == "Todos", { origen = "Todos" }, { Text("Todos") }) }
            items(DIRECTORIOS) { d -> FilterChip(origen == d.id, {
                origen = d.id
                registrar(MotorAdaptativo.TipoEvento.ORIGEN, categoria, d.id)
            }, { Text(d.nombre) }) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
            items(CATEGORIAS_DIRECTORIO) { c -> FilterChip(categoria == c, {
                categoria = c
                registrar(MotorAdaptativo.TipoEvento.CATEGORIA, c, origen)
                revisionAdaptacion++
                señalesPendientes = 0
            }, { Text(c) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("18+", style = MaterialTheme.typography.bodySmall)
            Switch(adulto, { adulto = it }, Modifier.padding(horizontal = 6.dp))
            Text("Adaptación local", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(habilitado, {
                habilitado = it
                adaptacion.establecerHabilitado(it)
                revisionAdaptacion++
            })
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
            item {
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
                            registrar(MotorAdaptativo.TipoEvento.SINCRONIZAR, categoria, id)
                            r.aviso?.let { avisos += "${id}: $it" }
                        }
                        entradas = entradas.filterNot { it.origen in ids } + nuevas
                        aviso = if (avisos.isEmpty()) "Actualización terminada; ${nuevas.size} entradas." else avisos.joinToString("\n")
                        cargando = false
                    }
                }, enabled = !cargando) { Text(if (origen == "Todos") "Actualizar todos" else "Actualizar") }
            }
            item {
                OutlinedButton({ revisionAdaptacion++; señalesPendientes = 0 }, enabled = habilitado && señalesPendientes > 0) {
                    Text(if (señalesPendientes > 0) "Aplicar ($señalesPendientes)" else "Adaptación aplicada")
                }
            }
            item { TextButton({ if (adaptacion.deshacerUltimo(sesion)) {
                eventos = adaptacion.eventos(); revisionAdaptacion++; señalesPendientes = 0
            } }, enabled = eventos.any { it.sesion == sesion }) { Text("Deshacer") } }
            item { TextButton({
                adaptacion.restablecer(); eventos = emptyList(); revisionAdaptacion++; señalesPendientes = 0
            }) { Text("Restablecer") } }
        }
        if (cargando) Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(); Text(" Actualizando sin bloquear la lectura…", Modifier.padding(8.dp))
        }
        Text("${recomendaciones.size} resultados · ${perfil.categoria} · ${perfil.densidad}",
            Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.Bold)
        Text(perfil.explicacion, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
        if (señalesPendientes > 0) Text("Hay señales nuevas: el orden y la densidad no cambiarán hasta aplicarlas.",
            Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
        Text(
            "Descubrimiento, no instalación ni garantía de seguridad. El motor usa reglas locales explicables, no IA generativa; " +
                "no guarda consultas ni URLs. Appteka Rebuild no tiene código de la variante verificado. Cachés externas.",
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
        )
        if (!adaptacion.disponible()) Text("Sin almacenamiento externo: la adaptación sólo durará esta sesión.",
            Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text(aviso, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
            color = if (aviso.contains("Fallo") || aviso.contains("Sin almacenamiento")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (recomendaciones.isEmpty() && !cargando) {
            Text("No hay datos que coincidan. Actualiza el origen o cambia los filtros.", Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(recomendaciones, key = { "${it.entrada.origen}:${it.entrada.id}:${it.entrada.url}" }) { recomendacion ->
                val entrada = recomendacion.entrada
                val padding = if (perfil.densidad == MotorAdaptativo.DENSIDAD_COMPACTA) 8.dp else 12.dp
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entrada.nombre, fontWeight = FontWeight.Bold)
                        Text("${DIRECTORIOS.firstOrNull { it.id == entrada.origen }?.nombre ?: entrada.origen} · ${entrada.categoria}",
                            style = MaterialTheme.typography.labelMedium)
                        if (entrada.descripcion.isNotBlank()) Text(entrada.descripcion, maxLines = perfil.lineasDescripcion,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        if (entrada.plataformas.isNotEmpty()) Text(entrada.plataformas.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        if (perfil.adaptado || consulta.isNotBlank()) Text("Por qué: ${recomendacion.motivo}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({
                                registrar(MotorAdaptativo.TipoEvento.FICHA, entrada.categoria, entrada.origen)
                                abrirWeb(entrada.detalle)
                            }) { Text("Ficha") }
                            TextButton({
                                registrar(MotorAdaptativo.TipoEvento.ENLACE, entrada.categoria, entrada.origen)
                                abrirWeb(entrada.url)
                            }) { Text(if (entrada.origen == "brave") "Buscar" else "Abrir enlace") }
                        }
                    }
                }
            }
        }
    }
}
