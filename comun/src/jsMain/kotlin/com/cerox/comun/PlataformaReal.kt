package com.cerox.comun

/** En Kotlin/JS no hay JVM: siempre es el navegador o Node. */
actual fun plataformaReal(): String = "js"
