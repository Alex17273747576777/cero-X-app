package com.ceroX.app.modules.houri.tracker

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * La pantalla donde el usuario pega su token y vincula la obra.
 *
 * POR QUÉ HACE FALTA UNA PANTALLA Y NO BASTA CON EL CLIENTE
 * ---------------------------------------------------------
 * Un cliente de tracker sin sitio donde pegar el token no sirve de nada, y un
 * tracker que se activa solo con un token que el usuario no ha dado sería peor:
 * estaría mandando datos de lectura a una cuenta sin permiso. Aquí el usuario
 * decide, y ve el resultado **tal cual** lo devolvió el servidor.
 *
 * LO QUE ESTA PANTALLA NO HACE
 * ----------------------------
 * No adivina el id de la obra en AniList ni en MAL a partir del título. Buscar
 * por título y coger el primer resultado es cómo la gente acaba con el progreso
 * del manga equivocado. El id se pega a mano, y la pantalla explica de dónde
 * sacarlo.
 */
class TrackerActivity : AppCompatActivity() {

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var credenciales: TrackerCredentials
    private val cliente = TrackerClient()

    private lateinit var campoToken: EditText
    private lateinit var campoId: EditText
    private lateinit var campoCapitulo: EditText
    private lateinit var campoTotal: EditText
    private lateinit var resultado: TextView
    private lateinit var textoAlmacen: TextView
    private var tracker: Tracker = Tracker.ANILIST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Sincronización"
        credenciales = TrackerCredentials(this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        textoAlmacen = TextView(this).apply { textSize = 12f }
        raiz.addView(textoAlmacen)

        raiz.addView(TextView(this).apply {
            text = "1 · Elige el tracker"
            textSize = 15f
            setPadding(0, 24, 0, 8)
        })

        val filaTrackers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Tracker.entries.forEach { t ->
            filaTrackers.addView(Button(this).apply {
                text = t.nombreVisible
                setOnClickListener {
                    tracker = t
                    campoToken.setText(credenciales.token(t).orEmpty())
                    resultado.text = "Tracker: ${t.nombreVisible} · token en ${t.urlAjustes}"
                }
            })
        }
        raiz.addView(filaTrackers)

        raiz.addView(TextView(this).apply {
            text = "2 · Pega tu token (se guarda cifrado con el Keystore del teléfono)"
            textSize = 15f
            setPadding(0, 24, 0, 8)
        })

        campoToken = EditText(this).apply {
            hint = "token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        raiz.addView(campoToken)

        raiz.addView(Button(this).apply {
            text = "Guardar token"
            setOnClickListener {
                val guardado = credenciales.guardar(tracker, campoToken.text.toString())
                resultado.text = if (guardado) {
                    "Token de ${tracker.nombreVisible} guardado cifrado."
                } else {
                    "No se pudo guardar cifrado: este dispositivo no tiene Keystore utilizable.\n" +
                        "No se guarda en claro a propósito."
                }
            }
        })

        raiz.addView(TextView(this).apply {
            text = "3 · Id de la obra en el tracker (no el de la fuente)"
            textSize = 15f
            setPadding(0, 24, 0, 8)
        })

        campoId = EditText(this).apply {
            hint = "p. ej. 30013"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        raiz.addView(campoId)

        raiz.addView(TextView(this).apply {
            text = "El id es el número que aparece en la URL de la obra en anilist.co " +
                "(anilist.co/manga/30013) o en myanimelist.net (…/manga/13). " +
                "No se busca por título a propósito: coger el primer resultado es " +
                "cómo se acaba con el progreso del manga equivocado."
            textSize = 12f
        })

        raiz.addView(TextView(this).apply {
            text = "4 · Capítulo y total"
            textSize = 15f
            setPadding(0, 24, 0, 8)
        })

        campoCapitulo = EditText(this).apply {
            hint = "capítulo leído"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        campoTotal = EditText(this).apply {
            hint = "total de capítulos (0 si no se sabe)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        raiz.addView(campoCapitulo)
        raiz.addView(campoTotal)

        raiz.addView(Button(this).apply {
            text = "Sincronizar ahora"
            setOnClickListener { sincronizar() }
        })

        resultado = TextView(this).apply {
            textSize = 13f
            setPadding(0, 24, 0, 0)
        }
        raiz.addView(resultado)

        setContentView(android.widget.ScrollView(this).apply { addView(raiz) })

        textoAlmacen.text = if (credenciales.almacenSeguroDisponible()) {
            "Almacén cifrado disponible."
        } else {
            "AVISO: este dispositivo no ofrece almacén cifrado; no se podrá guardar el token."
        }
        campoToken.setText(credenciales.token(tracker).orEmpty())
    }

    private fun sincronizar() {
        val id = campoId.text.toString().toLongOrNull()
        val capitulo = campoCapitulo.text.toString().toIntOrNull()
        if (id == null || capitulo == null) {
            resultado.text = "Faltan el id de la obra o el capítulo."
            return
        }
        val total = campoTotal.text.toString().toIntOrNull() ?: 0
        val token = campoToken.text.toString()

        resultado.text = "Sincronizando…"
        alcance.launch {
            val r = cliente.sincronizar(
                tracker = tracker,
                token = token,
                local = ProgresoARemoto(
                    idEnTracker = id,
                    capitulo = capitulo,
                    totalCapitulos = total,
                    estado = PlanificadorSync.estadoAlTerminarCapitulo(capitulo, total),
                ),
            )
            // Se pinta el resultado tal cual, sin adornarlo: si AniList devolvió
            // un 401, el usuario tiene que leer 401, no «algo falló».
            resultado.text = buildString {
                append(if (r.exito) "✔ " else "✘ ")
                append(r.resultado.name)
                append("\n")
                append(r.mensaje)
                if (r.progresoRemotoAntes >= 0) append("\nEl tracker tenía: ${r.progresoRemotoAntes}")
                append("\n${r.ms} ms")
            }
        }
    }

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }
}
