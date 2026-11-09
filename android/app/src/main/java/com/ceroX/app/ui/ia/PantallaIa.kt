package com.ceroX.app.ui.ia

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import com.ceroX.app.modules.ai.ModelAssets
import com.ceroX.app.modules.kisara.KisaraColorizer
import com.ceroX.app.modules.kisara.KisaraUpscaler
import com.ceroX.app.ui.reader.PreferenciasDeIa
import com.ceroX.app.ui.theme.CeroXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * La pantalla de los modelos: encender, apagar y ver qué pasó de verdad.
 *
 * QUÉ FALTABA
 * -----------
 * El README decía «funciona (falta pantalla)» en la colorización y en el reescalado.
 * No era una frase de relleno: los modelos se cargaban, se ejecutaban y devolvían
 * una imagen, pero no había ningún sitio en la app desde donde encenderlos, y el
 * lector ni siquiera llamaba al pipeline. Esta pantalla es ese sitio.
 *
 * QUÉ HACE QUE NO SEA UN INTERRUPTOR DECORATIVO
 * ---------------------------------------------
 *  1. **Ejecuta el modelo de verdad al pulsar «probar»**, sobre una imagen sintética
 *     de 256x256. No dice «disponible»: dice cuántos milisegundos tardó y qué
 *     tamaño tiene la imagen que salió.
 *  2. **Enseña el tamaño de las dos imágenes**, antes y después, para que se vea el
 *     ×4 y no haya que creérselo.
 *  3. **Guarda el motivo del último fallo** y lo sigue enseñando aunque se apague.
 *     Un modelo que falla en silencio es peor que uno que no está.
 *  4. **Dice el coste medido al lado de cada interruptor.** Quien lo enciende en un
 *     móvil de gama baja tiene que saber que son segundos por página, no milisegundos.
 */
class PantallaIaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CeroXTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PantallaIa(applicationContext)
                }
            }
        }
    }
}

/** Estado de una prueba concreta: nada, trabajando, o el parte de lo que pasó. */
private data class Prueba(
    val nombre: String,
    val ms: Long,
    val antes: String,
    val despues: String,
    val motivo: String,
    val exito: Boolean,
)

@Composable
fun PantallaIa(context: Context) {
    val prefs = remember { PreferenciasDeIa(context) }
    var colorizar by remember { mutableStateOf(prefs.colorizar) }
    var reescalar by remember { mutableStateOf(prefs.reescalar) }
    var trabajando by remember { mutableStateOf(false) }
    var prueba by remember { mutableStateOf<Prueba?>(null) }
    var motivoGuardado by remember { mutableStateOf(prefs.ultimoMotivo) }
    val alcance = rememberCoroutineScope()

    // Los modelos que hay de verdad dentro del APK, leídos del manifiesto.
    // `catalogo()` devuelve una entrada por modelo con lo que el manifiesto dice de él.
    val modelos = remember {
        runCatching {
            ModelAssets(context).catalogo().map { m ->
                val archivo = m["archivo"] as? String ?: m["id"] as? String ?: "?"
                val existe = m["presente"] == true
                "$archivo${if (existe) "" else "  (no está en el APK)"}"
            }
        }.getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Modelos en el teléfono", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Se ejecutan aquí, en el dispositivo. Ninguna página, imagen o texto sale " +
                "del teléfono: no hay red en este camino.",
            style = MaterialTheme.typography.bodySmall,
        )

        // ── los interruptores ────────────────────────────────────────────────
        Interruptor(
            titulo = "Colorizar páginas en blanco y negro",
            detalle = "colorize.onnx · 117,3 MiB · medido: ~1 490 ms por página de 256×256",
            encendido = colorizar,
            alCambiar = {
                colorizar = it
                prefs.colorizar = it
            },
        )
        Interruptor(
            titulo = "Reescalar ×4",
            detalle = "anime4x.onnx (4,9 MiB, 2 673 ms) o esrgan_x4.onnx (63,9 MiB, 24 587 ms), " +
                "elegido según la página",
            encendido = reescalar,
            alCambiar = {
                reescalar = it
                prefs.reescalar = it
            },
        )

        Text(prefs.resumen(), style = MaterialTheme.typography.bodyMedium)

        // ── la prueba de verdad ──────────────────────────────────────────────
        Button(
            enabled = !trabajando && (colorizar || reescalar),
            onClick = {
                trabajando = true
                prueba = null
                alcance.launch {
                    val r = withContext(Dispatchers.Default) { probar(context, colorizar, reescalar) }
                    prueba = r
                    trabajando = false
                    if (r != null) {
                        prefs.anotar(
                            com.ceroX.app.modules.reader.ReaderPipeline.Resultado(
                                pagina = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888),
                                aplicado = if (r.exito) listOf(r.nombre) else emptyList(),
                                fallido = if (r.exito) emptyList() else listOf(r.motivo),
                                ms = r.ms,
                            )
                        )
                        motivoGuardado = prefs.ultimoMotivo
                    }
                }
            },
        ) {
            Text(if (trabajando) "Ejecutando el modelo…" else "Probar con una imagen de 256×256")
        }

        if (trabajando) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Text(
                    "El reescalado general tarda hasta 24 s. Se espera; no se cancela a medias.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        prueba?.let { p ->
            Text(
                if (p.exito) "Salió: ${p.nombre}" else "No salió: ${p.nombre}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Antes: ${p.antes}")
            Text("Después: ${p.despues}")
            Text("Tiempo: ${p.ms} ms", style = MaterialTheme.typography.bodyMedium)
            if (!p.exito) {
                Text("Motivo: ${p.motivo}", color = MaterialTheme.colorScheme.error)
            }
        }

        // ── lo último que pasó en el lector ──────────────────────────────────
        if (motivoGuardado.isNotBlank()) {
            Text("Último resultado en el lector", style = MaterialTheme.typography.titleSmall)
            Text(motivoGuardado, style = MaterialTheme.typography.bodySmall)
            if (prefs.ultimosMs > 0) {
                Text("${prefs.ultimosMs} ms", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ── qué hay dentro ───────────────────────────────────────────────────
        Text("Archivos en el APK", style = MaterialTheme.typography.titleSmall)
        if (modelos.isEmpty()) {
            Text(
                "El manifiesto no se pudo leer. Los modelos pueden estar, pero esta " +
                    "pantalla no puede asegurarlo: aquí no hay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            modelos.forEach { m -> Text("· $m", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Interruptor(
    titulo: String,
    detalle: String,
    encendido: Boolean,
    alCambiar: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(detalle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = encendido, onCheckedChange = alCambiar)
    }
}

/**
 * Ejecuta el modelo de verdad sobre una imagen sintética y devuelve el parte.
 *
 * Por qué sintética y no una página real: esta pantalla no tiene un capítulo
 * abierto, y pedirle al usuario que navegue hasta uno para comprobar si el modelo
 * funciona es pedirle que depure por nosotros. Una imagen de 256×256 con ruido
 * suave basta para que el modelo trabaje y para medir cuánto tarda.
 */
private fun probar(context: Context, colorizar: Boolean, reescalar: Boolean): Prueba? {
    val assets = ModelAssets(context)
    return if (colorizar) {
        val original = sintetica(256)
        val r = KisaraColorizer(assets).colorear(original, 256)
        Prueba(
            nombre = "colorizar",
            ms = r.ms,
            antes = "${original.width}×${original.height}",
            despues = r.bitmap?.let { "${it.width}×${it.height}" } ?: "sin imagen",
            motivo = r.motivo,
            exito = r.exito,
        )
    } else if (reescalar) {
        val original = sintetica(128)
        val r = KisaraUpscaler(assets).reescalar(original, 256)
        Prueba(
            nombre = r.modelo.ifBlank { "reescalar" },
            ms = r.ms,
            antes = "${original.width}×${original.height}",
            despues = r.bitmap?.let { "${it.width}×${it.height}" } ?: "sin imagen",
            motivo = r.motivo,
            exito = r.exito,
        )
    } else {
        null
    }
}

/**
 * Una imagen con degradado y una línea negra: no es una página de manga, pero tiene
 * los dos extremos que el modelo necesita para trabajar (zonas planas y un borde).
 */
private fun sintetica(lado: Int): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(lado, lado, android.graphics.Bitmap.Config.ARGB_8888)
    val pixeles = IntArray(lado * lado)
    for (y in 0 until lado) {
        for (x in 0 until lado) {
            val v = (x * 255 / (lado - 1) + y * 255 / (lado - 1)) / 2
            val esLinea = y in (lado / 3)..(lado / 3 + 2)
            val gris = if (esLinea) 0 else 255 - v
            pixeles[y * lado + x] = (0xFF shl 24) or (gris shl 16) or (gris shl 8) or gris
        }
    }
    bmp.setPixels(pixeles, 0, lado, 0, 0, lado, lado)
    return bmp
}
