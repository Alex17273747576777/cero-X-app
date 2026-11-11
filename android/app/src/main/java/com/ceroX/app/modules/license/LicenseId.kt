package com.ceroX.app.modules.license

/**
 * Qué licencia tiene cero-X y por qué.
 *
 * **Medido, no supuesto.** El 2026-11-08 se consultó la licencia real por la API
 * de GitHub de toda la cadena de la que sale cero-X:
 *
 * ```
 * mihonapp/mihon            -> Apache-2.0
 * komikku-app/komikku       -> Apache-2.0
 * PineappleTwilight/houri   -> Apache-2.0
 * tachiyomiorg/tachiyomi    -> NONE (el repositorio ya no declara licencia)
 * ```
 *
 * Es decir: **el linaje es Apache-2.0**, no GPL. Ese hecho sigue siendo verdad
 * y es la razón de que exista [NOTICE] y de que el enum conserve `APACHE2`.
 *
 * **Pero cero-X es GPL-3.0 desde el 2026-11-11**, por decisión del autor (único
 * autor del repo) y por una razón práctica: lo que se va a fusionar a partir de
 * ahora —RootlessViPER4Android, Echo-Music, N-Zik, cloudstream, ytdlnis— es
 * GPL-3.0, y el código GPL no puede entrar en un proyecto Apache-2.0. La
 * compatibilidad va en una dirección: Apache dentro de GPL sí; GPL dentro de
 * Apache no.
 *
 * Lo que impone cada una, sin letra pequeña:
 *
 *   • GPL-3.0 **sí** obliga a ofrecer el código fuente correspondiente al
 *     distribuir. El repo es público, así que se cumple, y `LicenseCompliance`
 *     lo comprueba en vez de suponerlo.
 *   • Apache-2.0 (el linaje) obliga a **conservar el aviso y el NOTICE**, a
 *     indicar qué archivos se modificaron y concede una licencia de patentes que
 *     no se puede retirar. Esas obligaciones no desaparecen al relicenciar: las
 *     partes Apache siguen siendo Apache dentro del conjunto GPL.
 *   • El cambio es de ida: `canRelicensePermissively` es `false` para GPL3 porque
 *     quien ya recibió el código bajo GPL conserva esos derechos.
 *
 * Y un límite que ninguna licencia de este repo arregla: `namidaco/namida` es una
 * EULA propietaria que prohíbe redistribuir y reempaquetar. No se usa su código.
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
     * Si se puede relicenciar a algo **más** permisivo.
     *
     * Apache-2.0 y MIT sí: los dos permiten redistribuir bajo otros términos
     * mientras se conserve el aviso. La GPL no, porque quien ya recibió bajo GPL
     * conserva esos derechos y no se le pueden quitar.
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
