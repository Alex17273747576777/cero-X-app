package com.cerox.comun

/**
 * El programa de escritorio: la misma lógica que la app, sin Android.
 *
 * QUÉ HACE Y POR QUÉ ES POCO A PROPÓSITO
 * -------------------------------------
 * No hay interfaz gráfica de escritorio todavía, y fingir que la hay sería el
 * mismo error que se corrigió 14 veces en este proyecto. Lo que hay es un
 * ejecutable que **hace el trabajo de verdad** y se puede comprobar:
 *
 *   1. Dice en qué plataforma corre y de dónde sacó el motor JavaScript.
 *   2. Ordena una lista de capítulos con la regla compartida.
 *   3. Construye el sobre de una extensión y **la ejecuta** en QuickJS.
 *   4. Escribe un `.m3u`/`.txt` de biblioteca si se le pide, para demostrar que
 *      el `.jar` sirve de algo más que de adorno.
 *
 * Si algo no se puede hacer, sale con código 1 y lo dice. Un ejecutable que
 * imprime «ok» sin haber hecho nada es peor que no tener ejecutable.
 */
object Escritorio {

    @JvmStatic
    fun main(args: Array<String>) {
        // La salida se fuerza a UTF-8. Sin esto, una máquina con `LC_CTYPE=POSIX`
        // (contenedores, CI, algunos servidores) imprime «cap?tulo» en vez de
        // «capítulo»: el programa está bien y parece roto. El texto de la app es
        // en español y los acentos no son opcionales.
        System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
        System.setErr(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.err), true, "UTF-8"))
        val codigo = ejecutar(args)
        if (codigo != 0) kotlin.system.exitProcess(codigo)
    }

    /** Devuelve el código de salida. Separado de `main` para poder probarlo. */
    fun ejecutar(args: Array<String>): Int {
        val modo = args.firstOrNull() ?: "estado"
        println("cero-X escritorio · plataforma=${plataformaReal()} · motor=${motor()}")

        return when (modo) {
            "estado" -> {
                println("capacidades:")
                Plataforma.capacidades.forEach {
                    println(
                        "  · ${it.funcion.padEnd(18)} jvm=${si(it.jvm)} ios=${si(it.ios)} js=${si(it.js)}  ${it.nota}"
                    )
                }
                0
            }

            "ordenar" -> {
                // Lee nombres de archivo de la entrada estándar, uno por línea.
                val nombres = generateSequence(::readLine).map { it.trim() }.filter { it.isNotEmpty() }.toList()
                if (nombres.isEmpty()) {
                    System.err.println("no hay nombres que ordenar (lee de la entrada estándar)")
                    return 1
                }
                OrdenNatural.ordenar(nombres).forEach(::println)
                0
            }

            "extension" -> {
                val ruta = args.getOrNull(1)
                if (ruta == null) {
                    System.err.println("falta la ruta del archivo .js de la extensión")
                    return 1
                }
                val archivo = java.io.File(ruta)
                if (!archivo.isFile) {
                    System.err.println("no existe: $ruta")
                    return 1
                }
                val motor = MotorQuickJsProceso()
                if (!motor.disponible) {
                    // No se finge: sin motor no hay extensiones, y se dice.
                    System.err.println("no hay motor QuickJS disponible (CEROX_QJS o /opt/quickjs/qjs)")
                    return 1
                }
                // Se ejecuta el sobre y se imprime su resultado. El `console.log`
                // es necesario porque `qjs archivo.js` no imprime el valor de la
                // última expresión (eso sólo lo hace el REPL).
                // El sobre devuelve el JSON como valor de una expresión. `qjs
                // archivo.js` **no** imprime el valor final (eso sólo lo hace el
                // REPL), así que hay que guardarlo y hacer `console.log`.
                val script = AnfitrionJs.scriptConSalida(archivo.readText()) + "\nconsole.log(SALIDA);"
                val crudo = motor.evaluar(script, SobreDeExtension.TIMEOUT_MS) ?: ""
                val json = crudo.lines().firstOrNull { it.trimStart().startsWith("[") }
                val nombres = SobreDeExtension.nombresDeclarados(json)
                println("extensión evaluada: ${nombres.size} fuente(s)")
                nombres.forEach { println("  · $it") }
                if (nombres.isEmpty()) {
                    System.err.println("la extensión no declaró ninguna fuente")
                    return 1
                }
                0
            }

            "tracker" -> {
                // La regla que impide borrar progreso, ejecutable desde la consola.
                val local = args.getOrNull(1)?.toIntOrNull()
                val remoto = args.getOrNull(2)?.toIntOrNull()
                val total = args.getOrNull(3)?.toIntOrNull() ?: 0
                if (local == null || remoto == null) {
                    System.err.println("uso: tracker <capítulo-local> <capítulo-remoto> [total]")
                    return 1
                }
                val plan = PlanDeTracker.planificar(local, remoto, total)
                println("${plan.resultado}: ${plan.motivo}")
                if (plan.resultado == PlanDeTracker.Resultado.ENVIAR) println("enviar capítulo ${plan.capituloAEnviar}")
                0
            }

            else -> {
                System.err.println("modos: estado | ordenar | extension <archivo.js> | tracker <local> <remoto> [total]")
                1
            }
        }
    }

    private fun si(b: Boolean) = if (b) "sí" else "no "

    private fun motor(): String = runCatching { MotorQuickJsProceso().let { it.nombre } }
        .getOrElse { "no disponible" }

}
