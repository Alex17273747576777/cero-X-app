package com.ceroX.app.ui.estado

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ceroX.app.modules.permissions.PrivilegeProbe
import com.ceroX.app.modules.source_js.MotorJs
import com.ceroX.app.modules.source_js.QuickJsEngine
import com.ceroX.app.sources.RepositorioGlobal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La pantalla que dice **qué puede hacer la app en este teléfono**, medido.
 *
 * POR QUÉ EXISTE
 * --------------
 * Había tres cosas comprobables que no tenían ni un botón: el nivel de
 * privilegio (`PrivilegeProbe`), el motor JavaScript (`QuickJsEngine`) y las
 * fuentes registradas (`RepositorioGlobal`). Todo eso estaba probado con pruebas
 * unitarias y era **invisible** para el usuario. Una capacidad que no se puede
 * consultar desde la app es una capacidad que no existe para quien la usa.
 *
 * LA REGLA QUE SIGUE
 * ------------------
 * No resume: muestra la respuesta de cada comprobación con su motivo. Si Shizuku
 * está instalado pero no arrancado, dice exactamente eso. Si el motor JS no
 * arranca, dice el error, no «no disponible». Un panel de estado que redondea
 * hacia arriba es peor que no tener panel.
 *
 * LO QUE **NO** HACE
 * ------------------
 * No pide permisos, no instala nada y no cambia ajustes del sistema. Solo lee y
 * pinta. Cambiar de estado es cosa del usuario en su teléfono.
 */
class EstadoSistemaActivity : AppCompatActivity() {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Estado del sistema"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val texto = android.widget.TextView(this).apply {
            setPadding(48, 48, 48, 48)
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val raiz = android.widget.ScrollView(this).apply { addView(texto) }
        setContentView(raiz)
        texto.text = "Comprobando…"

        alcance.launch {
            val informe = withContext(Dispatchers.IO) { componerInforme() }
            texto.text = informe
        }
    }

    /**
     * El informe completo. Público y sin `Context` de por medio en la parte
     * pura para poder probarlo: lo único que necesita Android es
     * `PrivilegeProbe`, y eso se le pasa ya resuelto.
     */
    private suspend fun componerInforme(): String {
        val sb = StringBuilder()

        sb.appendLine("── PRIVILEGIOS ──")
        val probe = PrivilegeProbe(this)
        val informe = runCatching { probe.detectar() }
        informe.fold(
            onSuccess = { i ->
                sb.appendLine("Nivel: ${i.nivel.id.uppercase()} · ${i.nivel.canDo}")
                i.dispositivos.forEach { sb.appendLine("· ${it.describir()}") }
                sb.appendLine("Instalación: ${i.caminoDeInstalacion}")
                i.notas.forEach { sb.appendLine("  $it") }
                // La frase que impide que este panel mienta:
                sb.appendLine(
                    if (i.hayAlguno) "Hay al menos un gestor usable."
                    else "Ningún gestor es usable: instalado, servicio vivo y autorizado no se cumplen a la vez."
                )
            },
            onFailure = { e -> sb.appendLine("No se pudo comprobar: ${e.javaClass.simpleName}: ${e.message}") },
        )

        sb.appendLine()
        sb.appendLine("── MOTOR JAVASCRIPT ──")
        // Se crea el motor de verdad y se le pide una suma: es la única forma de
        // saber si el `.so` de QuickJS carga en **este** teléfono. `MotorJs.disponible()`
        // solo comprueba que la clase exista, y eso no arranca nada.
        val motor = QuickJsEngine()
        try {
            val t0 = System.currentTimeMillis()
            val resultado = runCatching { motor.eval("1+1", 2_000) }
            val ms = System.currentTimeMillis() - t0
            resultado.fold(
                onSuccess = { sb.appendLine("${MotorJs.nombre}: responde ($it) en $ms ms") },
                onFailure = { e -> sb.appendLine("${MotorJs.nombre}: no arranca · ${e.javaClass.simpleName}: ${e.message}") },
            )
        } finally {
            runCatching { motor.close() }
        }
        MotorJs.justificacion().lines().forEach { sb.appendLine("  $it") }

        sb.appendLine()
        sb.appendLine("── FUENTES DE CONTENIDO ──")
        val resumen = RepositorioGlobal.resumen()
        sb.appendLine("Registradas: ${resumen["fuentes"]} · navegables: ${resumen["navegables"]}")
        sb.appendLine("Carpeta local: ${if (resumen["fuente_local"] == true) "sí" else "no"}")
        @Suppress("UNCHECKED_CAST")
        (resumen["detalle"] as? List<Map<String, Any?>>)?.forEach {
            sb.appendLine("· ${it["nombre"]} (${it["lang"]}) id=${it["id"]}")
        }
        if ((resumen["fuentes"] as? Int ?: 0) == 0) {
            sb.appendLine("No hay ninguna fuente. El catálogo saldrá vacío y no es un fallo.")
        }

        return sb.toString()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }
}
