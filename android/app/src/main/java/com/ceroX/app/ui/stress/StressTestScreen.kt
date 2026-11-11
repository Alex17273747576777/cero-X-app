package com.ceroX.app.ui.stress

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.modules.reverse_engineering.DeepReverseEngineeringModule
import com.ceroX.app.modules.reverse_engineering.StressTestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * StressTestScreen — Pantalla de pruebas de estrés al extremo.
 * Ejecuta las 6 pruebas de estrés y muestra los resultados.
 */
@Composable
fun StressTestScreen(
    reModule: DeepReverseEngineeringModule?,
    modifier: Modifier = Modifier
) {
    // Un mapa no es un estado observable: con `by` no compila. Hay que envolver
    // el mapa en mutableStateOf y leerlo desde .value.
    var testResults by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var isRunning by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Pruebas de estrés",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Las etiquetas dicen lo que se mide de verdad. Antes anunciaban "10,000
        // series", "50 descargas" y "estrés térmico 2 horas" y el botón no
        // ejecutaba nada: escribía "completado" en el mapa para las seis.
        val tests = listOf(
            StressTestType.LIBRARY_MASSIVE to "Biblioteca: carga masiva",
            StressTestType.DOWNLOADS_CONCURRENT to "Concurrencia: 50 tareas de 100 ms",
            StressTestType.AI_INFERENCE to "Inferencia IA: 100 páginas",
            StressTestType.EXTENSIONS_MULTIPLE to "Extensiones simultáneas",
            StressTestType.THERMAL_2H to "Térmico: muestra de 3 s",
            StressTestType.COMPATIBILITY_8_TO_14 to "Compatibilidad Android 8-14"
        )

        tests.forEach { (type, label) ->
            val status = testResults[type.name] ?: "Pendiente"
            val detalle = testResults[type.name + " / detalle"]
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = status.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (status) {
                            "completado" -> MaterialTheme.colorScheme.primary
                            "fallido" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                // Aquí es donde se ve la diferencia: cada prueba cuenta lo que midió
                // o por qué no pudo medir nada. Cinco de las seis van a fallar, y
                // eso es correcto —la app no lleva modelo, ni biblioteca, ni
                // extensiones conectadas a este probador, ni puede emular otros
                // niveles de API—. Un panel todo en verde que no ejecutó nada es
                // peor que uno que dice lo que falta.
                if (detalle != null) {
                    Text(
                        text = detalle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Divider()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isRunning = true
                // Fuera del hilo principal: una de las pruebas abre 50 tareas y
                // otra hace red si algún día se conecta un termómetro real.
                alcance.launch {
                    val resultados = withContext(Dispatchers.Default) {
                        tests.map { (tipo, _) -> tipo to reModule?.runStressTest(tipo) }
                    }
                    val mapa = mutableMapOf<String, String>()
                    for ((tipo, r) in resultados) {
                        mapa[tipo.name] = when {
                            r == null -> "sin módulo"
                            r.passed -> "completado"
                            else -> "fallido"
                        }
                        mapa[tipo.name + " / detalle"] = r?.details
                            ?: "El módulo de ingeniería inversa no está registrado"
                    }
                    testResults = mapa
                    isRunning = false
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRunning) {
                CircularProgressIndicator()
            } else {
                Text(text = "Ejecutar todas las pruebas")
            }
        }
    }
}

/** Composable wrapper for stress test with RE module */
@Composable
fun StressTestWrapper(
    reModule: DeepReverseEngineeringModule?,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text("Ingeniería Inversa y Estrés") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } }
        )
        if (reModule != null) {
            StressTestScreen(reModule)
        } else {
            Text(text = "Módulo de RE no disponible")
        }
    }
}