package com.ceroX.app.modules.diagnostics

/**
 * En qué banco de pruebas está esto, dicho con medidas.
 *
 * POR QUÉ EXISTE
 * --------------
 * Se pidió "instalar VM y Sandbox". En la máquina de trabajo no se puede: sin
 * `/dev/kvm`, sin banderas `vmx` ni `svm` en el CPU y con 1.982 MB de RAM, el
 * emulador oficial no arranca y QEMU por TCG tampoco cabe. Decir "VM instalada"
 * sin esas medidas sería exactamente la clase de anuncio vacío que este repo
 * persigue con `ModelHonestyGuard`.
 *
 * Así que en vez de anunciar un banco, se mide y se dice cuál hay. Tres cosas:
 *
 *   1. Si el aparato es físico o emulado, por lo que declara su propio build.
 *   2. Si hay virtualización por hardware disponible (KVM y banderas). Sin eso
 *      una VM es una promesa, no un banco.
 *   3. Qué sandbox hay de verdad en el aparato: un proveedor de privilegios
 *      (Shizuku), un clonador de perfiles (Island, Parallel Space) o un perfil
 *      de trabajo. El sandbox de extensiones (QuickJS) y el camino Shizuku ya
 *      tienen su propio sitio en el informe; aquí se nombra lo que hay
 *      instalado alrededor.
 *
 * Todo es decisión pura: los hechos entran como parámetros y por eso se prueban
 * en la JVM, sin aparato. Quien los mide es [BancosAndroid].
 */
object Bancos {

    /** Cabecera de la sección, la misma que busca `BancosDeclaradosTest`. */
    const val SECCION = "── BANCO DE PRUEBAS ──"

    /** Lo que delata un emulador en los campos del build. */
    val MARCAS_DE_EMULADOR = listOf(
        "goldfish", "ranchu", "cuttlefish", "google_sdk", "sdk_gphone",
        "genymotion", "vbox", "emulator",
    )

    /** Paquetes que, si están instalados, ofrecen un sandbox o privilegios. */
    val PAQUETES_DE_SANDBOX = listOf(
        "rikka.shizuku" to "Shizuku (privilegios por binder)",
        "com.oasisfeng.island" to "Island (perfil de trabajo aislado)",
        "com.excelliance.dualaid" to "Parallel Space (app clonada)",
        "com.lbe.parallel.intl" to "Parallel Space (app clonada)",
    )

    /** Físico o emulado, por lo que el propio aparato declara de sí mismo. */
    fun esEmulador(
        huella: String,
        hardware: String,
        producto: String,
        fabricante: String,
    ): Boolean {
        val todo = listOf(huella, hardware, producto, fabricante)
            .joinToString(" ").lowercase()
        return MARCAS_DE_EMULADOR.any { todo.contains(it) }
    }

    /**
     * Cuántas veces aparecen `vmx` (Intel) o `svm` (AMD) en `/proc/cpuinfo`.
     * Cero significa que el hypervisor no le pasa virtualización al sistema:
     * ningún emulador x86 va a arrancar ahí.
     */
    fun banderasDeVirtualizacion(cpuinfo: String): Int =
        Regex("""\b(vmx|svm)\b""").findAll(cpuinfo.lowercase()).count()

    /** KVM existe sólo si la ruta medida está en la lista de las que existen. */
    fun kvmPresente(rutasQueExisten: List<String>): Boolean = "/dev/kvm" in rutasQueExisten

    /**
     * Qué sandbox hay alrededor de la app. Devuelve líneas legibles; vacía si no
     * hay ninguno, que es lo normal y no un fallo.
     */
    fun sandboxDetectado(paquetes: List<String>, perfiles: Int): List<String> {
        val salida = mutableListOf<String>()
        for ((paquete, descripcion) in PAQUETES_DE_SANDBOX) {
            if (paquete in paquetes) salida.add("$paquete — $descripcion")
        }
        if (perfiles > 1) {
            salida.add("$perfiles perfiles de usuario: hay un perfil aislado disponible")
        }
        return salida
    }

    /**
     * El veredicto. Un banco se declara con medidas. Sin KVM, QEMU puede usar
     * traducción TCG: no se deduce ni un arranque completo ni una imposibilidad.
     * Hay que comprobar ADB, el boot y el rendimiento en esa máquina concreta.
     */
    fun veredicto(emulador: Boolean, kvm: Boolean, banderas: Int): String = when {
        !emulador -> "dispositivo físico: el banco es este aparato, no una VM"
        kvm && banderas > 0 -> "emulador con KVM y $banderas banderas de virtualización: banco válido"
        else -> "emulador sin KVM (${if (kvm) "presente" else "ausente"}) y $banderas banderas: " +
            "no es un banco completo; TCG puede arrancar, pero hay que medirlo"
    }

    /**
     * Una afirmación sobre un banco vale lo que valen sus medidas. Sin ninguna,
     * se devuelve la frase que lo dice en vez de repetir la afirmación.
     */
    fun bancoDeclarado(afirmacion: String, medidas: List<String>): String =
        if (medidas.isEmpty()) {
            "«$afirmacion» sin ninguna medida detrás: no es un banco, es una intención"
        } else {
            "«$afirmacion» con ${medidas.size} medidas: " + medidas.joinToString(" · ")
        }

    /** El informe completo, en el orden en que se pinta. */
    fun lineas(
        huella: String,
        hardware: String,
        producto: String,
        fabricante: String,
        modelo: String,
        abis: List<String>,
        nucleos: Int,
        ramMb: Long,
        banderas: Int,
        kvm: Boolean,
        paquetesSandbox: List<String>,
        perfiles: Int,
    ): List<String> {
        val emulador = esEmulador(huella, hardware, producto, fabricante)
        val salida = mutableListOf<String>()
        salida.add(veredicto(emulador, kvm, banderas))
        salida.add("Aparato: $fabricante $modelo · hardware $hardware · producto $producto")
        salida.add("CPU: $nucleos núcleos · ABI ${abis.joinToString(", ")} · RAM $ramMb MB")
        salida.add(
            bancoDeclarado(
                "virtualización por hardware",
                listOf(
                    "/dev/kvm " + if (kvm) "presente" else "ausente",
                    "banderas vmx/svm: $banderas",
                ),
            ),
        )
        val sandbox = sandboxDetectado(paquetesSandbox, perfiles)
        if (sandbox.isEmpty()) {
            salida.add("Sandbox alrededor de la app: ninguno instalado. No es un fallo.")
        } else {
            salida.add("Sandbox alrededor de la app:")
            sandbox.forEach { salida.add("· $it") }
        }
        salida.add(
            "El sandbox de la app es otro: extensiones en QuickJS y privilegios por " +
                "Shizuku, que se informan en sus propias secciones.",
        )
        return salida
    }
}
