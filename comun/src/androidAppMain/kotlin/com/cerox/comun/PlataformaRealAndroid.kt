package com.cerox.comun

/**
 * La plataforma, en la app de Android.
 *
 * Es la misma función que en JVM, iOS y JS (`expect fun plataformaReal()` en
 * `commonMain`), pero sin `expect`: ese mecanismo solo existe en un proyecto
 * multiplataforma, y aquí los mismos fuentes se compilan dentro de un módulo de
 * Android. El valor es el mismo dato; lo único que cambia es cómo se declara.
 */
fun plataformaReal(): String = "android"
