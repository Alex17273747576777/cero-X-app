package com.cerox.comun

/**
 * Qué plataforma se está ejecutando, resuelto por `expect/actual`.
 *
 * Cada plataforma tiene un archivo de tres líneas. Es el precio de que el mismo
 * fuente compile en la JVM, en iOS y en JS sin `if` por todas partes.
 *
 * PARA ANDROID: la app no compila este archivo; compila
 * `comun/src/androidAppMain/kotlin/com/cerox/comun/PlataformaRealAndroid.kt`, que
 * devuelve lo mismo sin `expect` (que solo existe en proyectos multiplataforma).
 */
expect fun plataformaReal(): String
