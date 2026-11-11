package com.ceroX.app.modules.reverse_engineering

/**
 * CodeComparator — Comparativa de código en paralelo entre las 8 apps base.
 */
class CodeComparator {

    /**
     * No se compara nada, y el resultado lo dice.
     *
     * Comparar dos implementaciones exige ejecutarlas o analizarlas; aqui no se
     * hace ninguna de las dos cosas. Devolver la primera clave del mapa como
     * "implementación óptima" era inventarse un resultado con el orden de llegada.
     */
    fun compareFunction(functionName: String, implementations: Map<String, String>): CodeComparisonResult {
        if (implementations.isEmpty()) {
            return CodeComparisonResult(functionName, implementations, "error", null,
                "No hay implementaciones para comparar")
        }
        return CodeComparisonResult(
            functionName = functionName,
            implementations = implementations,
            status = "sin_comparar",
            bestImplementation = null,
            details = "Se recibieron ${implementations.size} implementaciones; ninguna se ejecuto ni se analizo, asi que no hay optima"
        )
    }

    /**
     * Igual que `compareFunction`: sin medir no hay mejor lector.
     *
     * Devolvía el primero de la lista y el detalle "Comparativa de lectores
     * completada".
     */
    fun compareReaders(apps: List<String>): ReaderComparisonResult {
        return ReaderComparisonResult(
            apps = apps,
            bestReader = "ninguna: no se midio ninguna",
            details = "No se comparo ningun lector: haria falta medirlos y aqui no se midio nada"
        )
    }
}

data class CodeComparisonResult(
    val functionName: String,
    val implementations: Map<String, String>,
    val status: String,
    val bestImplementation: String?,
    val details: String
)

data class ReaderComparisonResult(
    val apps: List<String>,
    val bestReader: String,
    val details: String
)