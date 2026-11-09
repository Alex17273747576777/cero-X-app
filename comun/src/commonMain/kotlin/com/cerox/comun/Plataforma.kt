package com.cerox.comun

/**
 * El núcleo de cero-X que NO es de Android.
 *
 * QUÉ HAY AQUÍ Y POR QUÉ
 * ----------------------
 * Todo lo que estaba en Kotlin con `import android.*` y no lo necesitaba. No es
 * una reescritura: es la misma lógica, movida a un módulo que se compila a:
 *
 *   · `.jar`      → escritorio (Windows, Linux, macOS con la JVM)
 *   · framework   → iOS (necesita macOS para enlazar)
 *   · `.js`       → web, y de ahí un escritorio con Electron si algún día hace
 *                   falta, o una web de lectura
 *
 * La regla que se siguió para decidir qué se mueve: **si el archivo importa
 * `android.*`, no se mueve**. Si solo usa `kotlin.*`, se mueve tal cual. Eso deja
 * fuera el lector de bitmaps y dentro las reglas, que es donde están los fallos.
 */
object Plataforma {

    /** En qué plataforma se está ejecutando. Se usa para no prometer de más. */
    enum class Tipo { JVM, IOS, JS, DESCONOCIDA }

    /**
     * El tipo de plataforma, detectado y no declarado.
     *
     * Se comprueba el nombre de la JVM porque `expect/actual` obligaría a un
     * archivo por plataforma solo para esto, y aquí no hace falta tanta
     * ceremonia: lo único que se hace con el dato es enseñarlo.
     */
    fun tipo(): Tipo =
        // `System.getProperty` no existe en Kotlin/JS común, así que se intenta
        // leer y, si revienta, se declara DESCONOCIDA. Es un dato informativo:
        // ningún comportamiento depende de él.
        try {
            when (plataformaReal()) {
                "jvm" -> Tipo.JVM
                "ios" -> Tipo.IOS
                "js" -> Tipo.JS
                else -> Tipo.DESCONOCIDA
            }
        } catch (e: Throwable) {
            Tipo.DESCONOCIDA
        }

    /**
     * Qué funciones del núcleo existen en cada plataforma, dicho sin adornos.
     *
     * Esto es lo que faltaba en el README: no un «hay módulos de escritorio»,
     * sino una tabla de qué hace cada binario y qué no.
     */
    val capacidades: List<Capacidad> = listOf(
        Capacidad("lector", jvm = true, ios = false, js = false,
            nota = "pintar páginas necesita un canvas nativo; el pipeline de imágenes usa ONNX, que no existe en iOS"),
        Capacidad("descargas", jvm = true, ios = true, js = false,
            nota = "en web no hay sistema de archivos donde dejar los capítulos"),
        Capacidad("trackers", jvm = true, ios = true, js = false,
            nota = "en web chocaría con CORS de AniList y MAL"),
        Capacidad("extensiones-js", jvm = true, ios = true, js = true,
            nota = "es el caso que justifica el motor compartido"),
        Capacidad("ocr", jvm = true, ios = false, js = false,
            nota = "manga-ocr es un .onnx y ONNX Runtime no tiene build para iOS en este proyecto"),
        Capacidad("traduccion-variantes", jvm = true, ios = true, js = true,
            nota = "son reglas de texto, no modelos"),
    )

    data class Capacidad(
        val funcion: String,
        val jvm: Boolean,
        val ios: Boolean,
        val js: Boolean,
        val nota: String,
    ) {
        fun en(plataforma: Tipo): Boolean = when (plataforma) {
            Tipo.JVM -> jvm
            Tipo.IOS -> ios
            Tipo.JS -> js
            Tipo.DESCONOCIDA -> false
        }
    }

    /** Lo que se le enseña a quien pregunte, en texto plano. */
    fun resumen(): String = buildString {
        appendLine("cero-X — núcleo compartido")
        appendLine("plataforma detectada: ${tipo()}")
        capacidades.forEach {
            appendLine(
                "  ${it.funcion.padEnd(22)}" +
                    "jvm=${if (it.jvm) "sí" else "no"}  " +
                    "ios=${if (it.ios) "sí" else "no"}  " +
                    "web=${if (it.js) "sí" else "no"}  — ${it.nota}"
            )
        }
    }.trimEnd()
}
