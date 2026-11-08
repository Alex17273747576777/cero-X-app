package eu.kanade.tachiyomi.source

/**
 * Punto de entrada que una extensión declara en su `META-INF/services`.
 *
 * El anfitrión carga el APK de la extensión con un DexClassLoader aislado, lee
 * el nombre de esta clase desde `META-INF/services/eu.kanade.tachiyomi.source.SourceFactory`
 * y la instancia. Es el mismo mecanismo de ServiceLoader que usa Java desde
 * siempre, y es lo que permite que una extensión no dependa de nada más que de
 * este contrato.
 */
interface SourceFactory {
    /** Devuelve las fuentes que trae esta extensión. */
    fun createSources(): List<Source>
}
