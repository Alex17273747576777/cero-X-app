package com.ceroX.app.ui.ia

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.modules.aihub.AIHubModule
import com.ceroX.app.modules.aihub.ConfiguracionChat
import com.ceroX.app.modules.aihub.CredencialesChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chat visible y alcanzable; ninguna respuesta se fabrica en la interfaz. */
@Composable
fun AsistenteChat(context: Context) {
    val app = context.applicationContext as CeroXApplication
    val credenciales = remember { CredencialesChat(context) }
    val inicial = remember { credenciales.cargar() }
    var base by remember { mutableStateOf(inicial.baseUrl) }
    var modelo by remember { mutableStateOf(inicial.modelo) }
    var clave by remember { mutableStateOf(inicial.apiKey) }
    var entrada by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }
    var modulo by remember { mutableStateOf<AIHubModule?>(null) }
    val alcance = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        modulo = Pantallas.modulo(app, "ai-hub", AIHubModule::class.java) {
            modulo = app.getModuleInstance("ai-hub") as? AIHubModule
            revision++
        }
    }

    fun configuracion() = ConfiguracionChat(base, modelo, clave)
    fun elegir(url: String, nombreModelo: String) {
        base = url
        modelo = nombreModelo
        aviso = "Preset cambiado; revisa el modelo exacto antes de enviar"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Asistente con IA", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Conversa con un endpoint OpenAI-compatible. El texto sale del teléfono " +
                    "hacia el proveedor configurado. La clave se cifra con Android Keystore; " +
                    "la conversación sólo vive en memoria.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { elegir("https://api.openai.com", "gpt-4o-mini") }) { Text("OpenAI") }
                TextButton(onClick = { elegir("https://openrouter.ai/api", "openai/gpt-4o-mini") }) { Text("OpenRouter") }
                TextButton(onClick = { elegir("https://api.groq.com/openai", "llama-3.1-8b-instant") }) { Text("Groq") }
            }
            TextButton(onClick = { elegir("http://127.0.0.1:11434", "llama3.2") }) {
                Text("Ollama en este dispositivo")
            }
            OutlinedTextField(
                value = base, onValueChange = { base = it },
                label = { Text("URL base HTTPS") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = modelo, onValueChange = { modelo = it },
                label = { Text("Modelo exacto") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = clave, onValueChange = { clave = it },
                label = { Text("API key (vacía en Ollama local)") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    aviso = if (credenciales.guardar(configuracion())) {
                        "Configuración guardada cifrada"
                    } else {
                        "No hay almacén seguro; la clave no se guardó"
                    }
                }) { Text("Guardar") }
                TextButton(onClick = {
                    clave = ""
                    aviso = if (credenciales.borrarClave()) "Clave borrada" else "No se pudo borrar la clave"
                }) { Text("Borrar clave") }
                TextButton(onClick = {
                    modulo?.borrarConversacion()
                    revision++
                    aviso = "Conversación borrada de memoria"
                }) { Text("Limpiar chat") }
            }

            val motor = modulo
            if (motor == null) {
                Text("El módulo todavía se está iniciando…", style = MaterialTheme.typography.bodySmall)
            } else {
                val mensajes = remember(motor, revision) { motor.mensajes() }
                if (mensajes.isNotEmpty()) {
                    HorizontalDivider()
                    mensajes.forEach { mensaje ->
                        Text(
                            if (mensaje.rol == "user") "Tú" else "IA",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(mensaje.contenido, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            OutlinedTextField(
                value = entrada, onValueChange = { if (it.length <= 12_000) entrada = it },
                label = { Text("Mensaje") }, minLines = 2, maxLines = 5,
                enabled = !trabajando, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !trabajando && entrada.isNotBlank() && modulo != null,
                onClick = {
                    val motor = modulo ?: return@Button
                    if (!motor.isStarted()) motor.start()
                    val texto = entrada.trim()
                    trabajando = true
                    aviso = ""
                    alcance.launch {
                        try {
                            val config = configuracion()
                            val respuesta = withContext(Dispatchers.IO) { motor.conversar(config, texto) }
                            entrada = ""
                            revision++
                            aviso = "${respuesta.modelo} · HTTP ${respuesta.codigoHttp} · ${respuesta.milisegundos} ms"
                            if (!credenciales.guardar(config)) {
                                aviso += " · clave no guardada: almacén seguro no disponible"
                            }
                        } catch (e: Exception) {
                            revision++
                            aviso = e.message ?: "${e.javaClass.simpleName}: no se recibió respuesta"
                        } finally {
                            trabajando = false
                        }
                    }
                },
            ) { Text(if (trabajando) "Esperando respuesta…" else "Enviar") }
            if (trabajando) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text("La red trabaja en segundo plano; puedes salir de esta pantalla.")
                }
            }
            if (aviso.isNotBlank()) Text(aviso, style = MaterialTheme.typography.bodySmall)
        }
    }
}
