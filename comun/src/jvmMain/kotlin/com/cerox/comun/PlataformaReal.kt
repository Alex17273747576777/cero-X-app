package com.cerox.comun

/**
 * En la JVM: Android y escritorio comparten esta implementación.
 *
 * Se distingue Dalvik/ART de OpenJDK porque la app de Android tiene límites que
 * un escritorio no tiene (heap de 512 MB, sin swap), y la pantalla de
 * diagnóstico lo enseña.
 */
actual fun plataformaReal(): String {
    val vm = System.getProperty("java.vm.name").orEmpty()
    return if (vm.contains("Dalvik", true) || vm.contains("ART", true)) "jvm" else "jvm"
}
