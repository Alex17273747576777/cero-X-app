package com.cerox.comun

import kotlin.js.ExperimentalJsExport

/**
 * La cara pública del núcleo para JavaScript y Node.
 *
 * POR QUÉ HACE FALTA UN ARCHIVO APARTE PARA EXPORTAR
 * -------------------------------------------------
 * Kotlin/JS, por defecto, **no publica nada**: compila el módulo y el `.js`
 * resultante es un archivo vacío de 445 bytes que no expone ni una función. Es
 * lo que salía antes de este archivo, y es la razón por la que exista: un
 * artefacto que no expone nada es un artefacto que no se puede usar, aunque
 * compile sin errores.
 *
 * Las funciones de aquí devuelven **texto, números y listas de texto**, no los
 * tipos del módulo. Es a propósito: exportar `data class` a JS obliga a
 * exportar todo el grafo de tipos y a lidiar con la representación de Kotlin en
 * JavaScript. Una fachada plana es lo que un programa en Node o una web pueden
 * llamar sin ceremonia, y sigue siendo la misma lógica —no hay una copia—.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
object ApiWeb {

    /** Versión del núcleo, para que quien lo use sepa qué tiene. */
    @JsName("version")
    const val VERSION: String = "1.0.0"

    /** En qué plataforma corre: "js" cuando es Node o un navegador. */
    @JsName("plataforma")
    fun plataforma(): String = plataformaReal()

    /** Ordena nombres de archivo con la regla natural («2» antes que «10»). */
    @JsName("ordenar")
    fun ordenar(nombres: Array<String>): Array<String> =
        OrdenNatural.ordenar(nombres.toList()).toTypedArray()

    /** El número de un nombre de capítulo, o -1. */
    @JsName("numeroDe")
    fun numeroDe(nombre: String): Double = OrdenNatural.numeroDe(nombre).toDouble()

    /** Cuántas descargas a la vez para un perfil. Nunca más de dos. */
    @JsName("concurrencia")
    fun concurrencia(perfil: String): Int = PresupuestoDeCola.concurrencia(perfil)

    /** Espera antes del intento n, en milisegundos. */
    @JsName("esperaAntesDelIntento")
    fun esperaAntesDelIntento(intento: Int): Double =
        PresupuestoDeCola.esperaAntesDelIntento(intento).toDouble()

    /** Tope de bytes por tipo de archivo. */
    @JsName("topePorTipo")
    fun topePorTipo(nombreArchivo: String): Double =
        PresupuestoDeCola.topePorTipo(PresupuestoDeCola.clasificar(nombreArchivo)).toDouble()

    /** Progreso honesto: 0 si no se sabe el total, nunca 100. */
    @JsName("progreso")
    fun progreso(bytesHechos: Double, bytesEsperados: Double): Double =
        PresupuestoDeCola.progreso(bytesHechos.toLong(), bytesEsperados.toLong()).toDouble()

    /**
     * La regla del tracker: devuelve `"RESULTADO|motivo"`.
     *
     * Se devuelve texto y no un objeto porque cruzar un objeto Kotlin a JS
     * opaco obliga a envolverlo; dos campos en una cadena se leen igual y no
     * mienten sobre lo que hay.
     */
    @JsName("planDeTracker")
    fun planDeTracker(local: Int, remoto: Int, total: Int): String {
        val p = PlanDeTracker.planificar(local, remoto, total)
        return "${p.resultado}|${p.motivo}"
    }

    /** Aplica la variante del idioma y el glosario a un texto. */
    @JsName("variante")
    fun variante(texto: String, codigo: String, de: Array<String>, a: Array<String>): String {
        val glosario = buildMap { de.forEachIndexed { i, k -> a.getOrNull(i)?.let { put(k, it) } } }
        return Variante.aplicar(texto, Variante.porCodigo(codigo), glosario)
    }

    /** El sobre que envuelve una extensión antes de evaluarla. */
    @JsName("sobreDeExtension")
    fun sobreDeExtension(fuente: String): String = SobreDeExtension.construir(fuente)

    /** Los nombres que declaró una extensión, desde el JSON del sobre. */
    @JsName("nombresDeclarados")
    fun nombresDeclarados(json: String): Array<String> =
        SobreDeExtension.nombresDeclarados(json).toTypedArray()

    /** Una capacidad de la tabla, como "funcion|jvm|ios|js". */
    @JsName("capacidades")
    fun capacidades(): Array<String> = Plataforma.capacidades
        .map { "${it.funcion}|${it.jvm}|${it.ios}|${it.js}" }
        .toTypedArray()
}
