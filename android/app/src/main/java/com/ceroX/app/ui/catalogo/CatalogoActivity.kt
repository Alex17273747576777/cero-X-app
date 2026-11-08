package com.ceroX.app.ui.catalogo

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ceroX.app.sources.ContentRepository
import com.ceroX.app.sources.RepositorioGlobal
import com.ceroX.app.ui.reader.ReaderActivity
import com.ceroX.app.ui.reader.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * El catálogo: buscar → elegir obra → elegir capítulo → leer.
 *
 * POR QUÉ VIEWS NORMALES Y NO COMPOSE
 * -----------------------------------
 * El resto de pantallas nuevas están en Compose, pero esta es la que tiene que
 * poder recorrerse de arriba abajo para comprobar que cada paso llama a algo que
 * existe. Con `ListView` y `TextView` se ve en el código, en orden, qué se pide
 * en cada momento; con Compose habría que saltar entre composables para
 * reconstruir el flujo. El lector sí es Compose porque ahí manda el dibujo.
 *
 * LO QUE ESTA PANTALLA HACE HONESTAMENTE
 * --------------------------------------
 *  - Si no hay ninguna fuente, **lo dice** y explica cómo añadir una carpeta.
 *    No muestra una lista vacía fingiendo que busca.
 *  - Si una fuente falló, pinta su nombre y el motivo. Un buscador que pierde
 *    una fuente en silencio es un buscador que miente.
 *  - Cada paso deshabilita los botones mientras trabaja: pulsar «buscar» cinco
 *    veces no lanza cinco búsquedas.
 */
class CatalogoActivity : AppCompatActivity() {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repo: ContentRepository

    private lateinit var campoBusqueda: EditText
    private lateinit var botonBuscar: Button
    private lateinit var textoEstado: TextView
    private lateinit var lista: ListView
    private lateinit var contenedor: LinearLayout

    /** Lo que hay pintado ahora, para saber qué hacer al tocar un elemento. */
    private var resultados: List<ContentRepository.Resultado> = emptyList()
    private var capitulos: List<ContentRepository.Capitulo> = emptyList()
    private var detalleActual: ContentRepository.Detalle? = null
    private var paso = Paso.BUSCAR

    private enum class Paso { BUSCAR, CAPITULOS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Catálogo"
        repo = RepositorioGlobal.obtener()
        construirVista()
        describirFuentes()
    }

    /**
     * La pantalla se construye en código, sin XML.
     *
     * Un layout XML con ids para tres controles obliga a mantener dos archivos
     * sincronizados; aquí se ve en un sitio qué hay y qué hace cada botón.
     */
    private fun construirVista() {
        contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        campoBusqueda = EditText(this).apply {
            hint = "Buscar una obra"
            setSingleLine()
        }

        botonBuscar = Button(this).apply {
            text = "Buscar"
            setOnClickListener { buscar() }
        }

        textoEstado = TextView(this).apply {
            setPadding(0, 16, 0, 16)
            textSize = 13f
        }

        lista = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            setOnItemClickListener { _, _, posicion, _ ->
                if (paso == Paso.CAPITULOS) abrirCapitulo(posicion) else abrirObra(posicion)
            }
        }

        contenedor.addView(campoBusqueda)
        contenedor.addView(botonBuscar)
        contenedor.addView(textoEstado)
        contenedor.addView(lista)

        // Un ScrollView envolvería la lista y rompería el desplazamiento: el
        // ListView tiene que quedarse con todo el alto que sobre.
        setContentView(contenedor)
    }

    /** Dice qué fuentes hay. Es lo primero que hay que saber si algo no sale. */
    private fun describirFuentes() {
        val resumen = RepositorioGlobal.resumen()
        val cuantas = resumen["fuentes"] as Int
        textoEstado.text = when {
            cuantas == 0 -> """
                No hay ninguna fuente registrada.

                cero-X lee de dos sitios:
                  · una carpeta del teléfono (siempre disponible)
                  · extensiones de Mihon/Komikku, si las instalas

                Para usar una carpeta, crea «lector» dentro de los archivos de la
                app y mete ahí una carpeta por obra y una subcarpeta por capítulo.
            """.trimIndent()
            else -> "$cuantas fuente(s) · ${resumen["navegables"]} navegable(s)"
        }
        botonBuscar.isEnabled = cuantas > 0
    }

    private fun buscar() {
        val consulta = campoBusqueda.text?.toString().orEmpty().trim()
        botonBuscar.isEnabled = false
        textoEstado.text = "Buscando…"
        paso = Paso.BUSCAR

        alcance.launch {
            val r = repo.buscar(consulta)
            resultados = r.resultados
            capitulos = emptyList()
            detalleActual = null

            val fallos = r.fallos.joinToString("\n") { "· ${it.nombre}: ${it.error}" }
            textoEstado.text = buildString {
                append("${r.resultados.size} resultado(s) de ${r.fuentesOk} fuente(s)")
                if (fallos.isNotBlank()) append("\nFallaron:\n$fallos")
                if (r.resultados.isEmpty() && fallos.isBlank()) append("\nNo se encontró nada.")
            }

            // Una sola línea por elemento, con el título y la fuente en el mismo
            // texto: `simple_list_item_2` depende de ids internos de Android que
            // cambian entre versiones, y una lista que revienta en Android 8
            // porque cambió un id no vale la pena por una segunda línea.
            lista.adapter = ArrayAdapter(
                this@CatalogoActivity,
                android.R.layout.simple_list_item_1,
                resultados.map { "${it.titulo}\n${it.fuente} · ${it.autor ?: "autor desconocido"}" },
            )
            botonBuscar.isEnabled = true
        }
    }

    private fun abrirObra(posicion: Int) {
        val resultado = resultados.getOrNull(posicion) ?: return
        botonBuscar.isEnabled = false
        textoEstado.text = "Abriendo ${resultado.titulo}…"

        alcance.launch {
            val d = repo.detalle(resultado.fuenteId, resultado.url)
            botonBuscar.isEnabled = true
            if (d == null) {
                textoEstado.text = "No se pudo abrir «${resultado.titulo}»: la fuente no respondió."
                return@launch
            }
            detalleActual = d
            capitulos = d.capitulos
            paso = Paso.CAPITULOS
            textoEstado.text = "${d.titulo} · ${d.capitulos.size} capítulo(s) · ${d.fuente}"
            lista.adapter = ArrayAdapter(
                this@CatalogoActivity,
                android.R.layout.simple_list_item_1,
                d.capitulos.map { it.etiqueta },
            )
        }
    }

    private fun abrirCapitulo(posicion: Int) {
        val capitulo = capitulos.getOrNull(posicion) ?: return
        val detalle = detalleActual ?: return
        startActivity(
            Intent(this, ReaderActivity::class.java).apply {
                putExtra(ReaderActivity.EXTRA_FUENTE_ID, detalle.fuenteId)
                putExtra(ReaderActivity.EXTRA_FUENTE_NOMBRE, detalle.fuente)
                putExtra(ReaderActivity.EXTRA_OBRA, detalle.titulo)
                putExtra(ReaderActivity.EXTRA_CAPITULO, capitulo.etiqueta)
                putExtra(ReaderActivity.EXTRA_URL_CAPITULO, capitulo.url)
                // Se abre en webtoon por defecto: es el modo que no puede
                // fallar, porque no hay sentido de lectura que equivocar.
                putExtra(ReaderActivity.EXTRA_MODO, ReaderState.Modo.WEBTOON.name)
            }
        )
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Volver de capítulos lleva a los resultados, no cierra la pantalla.
        if (paso == Paso.CAPITULOS && resultados.isNotEmpty()) {
            paso = Paso.BUSCAR
            capitulos = emptyList()
            detalleActual = null
            textoEstado.text = "${resultados.size} resultado(s)"
            lista.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                resultados.map { "${it.titulo}\n${it.fuente}" },
            )
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }
}
