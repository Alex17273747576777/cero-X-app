package com.ceroX.app.modules.license

/**
 * Qué licencia tiene cero-X y por qué.
 *
 * cero-X es un fork del linaje Mihon/Tachiyomi/Komikku, y ese linaje es
 * **GPL-3.0**. Lo que impone la GPL y lo que no, dicho sin letra pequeña:
 *
 *   • Obliga a publicar el código modificado al distribuirlo.
 *   • Obliga a mantener la misma licencia en las modificaciones.
 *   • **No** obliga a cobrar ni prohíbe cobrar.
 *   • **No** obliga a dar soporte.
 *
 * La consecuencia práctica que más se ignora: si distribuyes una APK
 * modificada, tienes que ofrecer el código. Publicar solo la APK no cumple.
 */
enum class LicenseId(val id: String, val spdx: String, val displayName: String) {
    GPL3(
        id = "gpl-3.0",
        spdx = "GPL-3.0-only",
        displayName = "GNU General Public License v3.0",
    ),
    LGPL3(
        id = "lgpl-3.0",
        spdx = "LGPL-3.0-only",
        displayName = "GNU Lesser General Public License v3.0",
    ),
    APACHE2(
        id = "apache-2.0",
        spdx = "Apache-2.0",
        displayName = "Apache License 2.0",
    ),
    MIT(
        id = "mit",
        spdx = "MIT",
        displayName = "MIT License",
    ),
    PROPRIETARY(
        id = "proprietary",
        spdx = "LicenseRef-Proprietary",
        displayName = "Propietaria (todos los derechos reservados)",
    );

    /** Si al distribuir una versión modificada hay que publicar el código. */
    val requiresSourceOnDistribution: Boolean
        get() = this == GPL3 || this == LGPL3

    /**
     * Si se puede relicenciar a algo más permisivo.
     *
     * La GPL no permite bajar a MIT: quien recibe puede exigir los mismos
     * derechos que la GPL le dio. Esto no es una opinión, es la cláusula 2.
     */
    val canRelicensePermissively: Boolean
        get() = this == APACHE2 || this == MIT

    companion object {
        fun fromId(id: String): LicenseId? =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) || it.spdx.equals(id, ignoreCase = true) }
    }
}

/**
 * Una obligación concreta con su estado de cumplimiento.
 *
 * `met = false` no es un fallo de compilación: es una tarea pendiente que la
 * app puede mostrar. Una licencia "cumplida" que nadie ha comprobado es papel
 * mojado, y este proyecto ya tuvo 14 cosas que decían estar hechas.
 */
data class LicenseObligation(
    val id: String,
    val description: String,
    val met: Boolean,
    val evidence: String,
) {
    val status: String get() = if (met) "cumplida" else "PENDIENTE"
}

/**
 * Comprobador de la licencia del propio proyecto.
 *
 * Se evalúa sobre hechos que se pueden verificar en el repositorio (¿hay LICENSE?
 * ¿hay NOTICE? ¿se publica el código?) y no sobre intenciones.
 */
class LicenseCompliance(
    val license: LicenseId,
    private val hasLicenseFile: Boolean,
    private val hasNoticeFile: Boolean,
    private val sourcePublished: Boolean,
    private val bundledAssets: List<BundledAsset>,
) {

    /** Los modelos ONNX tienen su propia licencia y hay que respetarla. */
    data class BundledAsset(
        val name: String,
        val license: String,
        val attributionInApp: Boolean,
    )

    fun obligations(): List<LicenseObligation> = buildList {
        add(
            LicenseObligation(
                id = "license-file",
                description = "El repositorio incluye el texto de la licencia",
                met = hasLicenseFile,
                evidence = if (hasLicenseFile) "LICENSE presente" else "falta LICENSE",
            )
        )
        add(
            LicenseObligation(
                id = "notice",
                description = "Se reconoce el trabajo del linaje Mihon/Tachiyomi",
                met = hasNoticeFile,
                evidence = if (hasNoticeFile) "NOTICE presente" else "falta NOTICE",
            )
        )
        if (license.requiresSourceOnDistribution) {
            add(
                LicenseObligation(
                    id = "source-available",
                    description = "Al distribuir la APK se ofrece el código correspondiente",
                    met = sourcePublished,
                    evidence = if (sourcePublished) "repositorio público" else "repositorio no publicado",
                )
            )
        }
        // Cada modelo empaquetado arrastra su licencia.
        bundledAssets.forEach { a ->
            add(
                LicenseObligation(
                    id = "asset:${a.name}",
                    description = "Crédito visible del modelo ${a.name} (${a.license})",
                    met = a.attributionInApp,
                    evidence = if (a.attributionInApp) "acreditado en la app" else "sin crédito visible",
                )
            )
        }
    }

    fun pending(): List<LicenseObligation> = obligations().filter { !it.met }

    /** Solo se puede decir "cumple" si no queda ninguna pendiente. */
    fun isCompliant(): Boolean = pending().isEmpty()

    fun summary(): String {
        val p = pending()
        return if (p.isEmpty()) "licencia ${license.spdx}: sin obligaciones pendientes"
        else "licencia ${license.spdx}: ${p.size} pendiente(s) — ${p.joinToString(", ") { it.id }}"
    }
}
