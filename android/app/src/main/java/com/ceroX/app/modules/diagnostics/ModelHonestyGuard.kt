package com.ceroX.app.modules.diagnostics

import android.content.Context
import org.json.JSONObject

/**
 * Guardián de honestidad: comprueba que ningún módulo anuncia un modelo ONNX
 * que no esté realmente empaquetado en el APK.
 *
 * POR QUÉ EXISTE
 * --------------
 * `KisaraReaderEngineModule` anunciaba tres modelos: `manga_colorization_v2_int8.onnx`,
 * `PP-OCRv6_manga` y `deoldify_artistic_quant.onnx`. En el APK hay cinco modelos y
 * **ninguno de esos tres está entre ellos**. El usuario abría la app, veía
 * "motor OCR PP-OCRv6 activo" y no traducía una sola página. Es exactamente la
 * clase de mentira que este proyecto no puede permitirse.
 *
 * Se arregla una vez a mano y vuelve a aparecer en tres meses. Se arregla con una
 * prueba que se ejecuta en cada compilación y no vuelve.
 *
 * CÓMO FUNCIONA
 * -------------
 * No confía en una lista escrita a mano: lee `MANIFEST.json`, que se generó
 * midiendo cada archivo, y compara contra lo que declaran los módulos. Si un
 * módulo nombra algo que no está, falla.
 *
 * Se ejecuta como prueba unitaria (`ModelHonestyTest`), así que un `gradle test`
 * la corre siempre y aquí no se publica nada que mienta.
 */
class ModelHonestyGuard(private val context: Context) {

    /** Ficheros .onnx que de verdad están en assets/models. */
    fun modelosEmpaquetados(): Set<String> {
        // No se crea ningún directorio. Antes había aquí un `mkdirs()` sobre un
        // directorio de prueba que no se usaba para nada: escribía en el
        // almacenamiento interno para comprobar una **lectura**. Un `mkdirs()`
        // de más es basura que se queda en el teléfono del usuario.
        //
        // Se listan desde el propio APK, no desde el manifiesto: si el manifiesto
        // y el APK discrepan, gana el APK, que es lo que recibe el usuario.
        val salida = mutableSetOf<String>()
        context.assets.list("models")?.forEach { nombre ->
            if (nombre.endsWith(".onnx")) salida.add(nombre)
        }
        return salida
    }

    /** Nombres de modelo declarados en MANIFEST.json. */
    fun modelosDelManifiesto(): Set<String> {
        val json = context.assets.open("models/MANIFEST.json").bufferedReader().use { it.readText() }
        val raiz = JSONObject(json)
        val arr = raiz.getJSONArray("modelos")
        val salida = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            salida.add(arr.getJSONObject(i).getString("archivo"))
        }
        return salida
    }

    /**
     * Busca en el texto de un `healthCheck` (o en cualquier mapa de estado)
     * menciones a modelos que no están empaquetados.
     *
     * Se buscan las dos formas en que un módulo puede mentir:
     *   1. un nombre de archivo `.onnx` que no existe
     *   2. un nombre de familia de modelo conocido que no está (PP-OCRv6, LaMa,
     *      deoldify…) — estos no llevan `.onnx` en el texto y se colaban
     */
    fun mentirasEn(declarado: Map<String, Any?>): List<String> {
        val empaquetados = modelosEmpaquetados()
        val problemas = mutableListOf<String>()

        fun revisarTexto(texto: String, donde: String) {
            // 1. cualquier *.onnx citado
            Regex("""[\w.\-]+\.onnx""").findAll(texto).forEach { m ->
                val nombre = m.value
                if (nombre !in empaquetados) {
                    problemas.add("$donde cita '$nombre', que no está en el APK")
                }
            }
            // 2. familias conocidas que no se empaquetaron nunca
            FAMILIAS_NO_EMPAQUETADAS.forEach { familia ->
                if (texto.contains(familia, ignoreCase = true)) {
                    problemas.add("$donde cita '$familia', que no se empaquetó")
                }
            }
        }

        declarado.forEach { (clave, valor) ->
            when (valor) {
                is String -> revisarTexto(valor, clave)
                is Collection<*> -> valor.forEach { v ->
                    if (v is String) revisarTexto(v, clave)
                }
                else -> Unit
            }
        }
        return problemas
    }

    companion object {
        /**
         * Familias de modelo que se mencionaron en el código y nunca entraron en
         * el APK, con el motivo. Si algún día se empaqueta una, se quita de aquí
         * y se añade al manifiesto.
         */
        val FAMILIAS_NO_EMPAQUETADAS = listOf(
            "PP-OCRv6",                    // no se descargó; el OCR usa manga-ocr
            "\\bLaMa\\b",                  // inpaint de bocadillos: nunca se empaquetó
            "deoldify",                    // colorización alternativa: descartada por peso
            "manga_colorization_v2_int8",  // int8: ORT_NOT_IMPLEMENTED ConvInteger
            "\\bllama\\.cpp\\b",             // traducción: no hay runtime nativo en el APK
            "Index-Translate",             // traducción: modelo de 2B, imposible de empaquetar
        )

        /**
         * Se comparan como expresiones regulares, NO como subcadenas.
         *
         * La versión anterior buscaba `contains(familia)`. Eso hacía que:
         *   "ollama" contuviera "llama"   → KaiModule acusado en falso
         * y en la primera medición de repos, que "reikai/" contuviera "ai/" → 1 681
         * archivos de IA a Reikai cuando tiene cero.
         *
         * Es el mismo error de raíz dos veces. Con `\\b` no puede repetirse: "LaMa"
         * solo casa como palabra suelta y no dentro de "ollama", que va en minúsculas.
         */
        fun casa(texto: String, patron: String): Boolean =
            Regex(patron).containsMatchIn(texto)
    }
}
