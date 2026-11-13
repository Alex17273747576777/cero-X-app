package com.ceroX.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Que el toolchain de Kotlin/JS, con sus dependencias vulnerables, no llegue al APK.
 *
 * DE DÓNDE VIENE
 * --------------
 * El 2026-11-13 Dependabot reportó tres alertas abiertas en este repositorio, una de
 * ellas alta (RCE), todas sobre `serialize-javascript`. En un proyecto Android escrito
 * en Kotlin eso no tiene sentido aparente, así que se midió en vez de suponer: la
 * dependencia vive en `comun/kotlin-js-store/yarn.lock` fijada en 6.0.2, la arrastra
 * `mocha` (10.7.0) —que es el runner de pruebas del target `js(IR) { nodejs() }`— y los
 * tres parches exigen 7.0.3, 7.0.5 y 7.1.3, o sea un salto mayor que el `^6.0.2` de
 * mocha no permite. Forzar la resolución rompería el target JS y aquí no se puede
 * verificar porque ese target no se compila en este entorno.
 *
 * Lo que sí se midió es que **no forma parte del producto**: la app no compila el
 * target JS, incluye las fuentes de `comun/` directamente en su propio source set, el
 * APK de depuración lleva cero ficheros `.js` y `comun/build` ni siquiera existe. Así
 * que el riesgo real es del entorno de construcción del target JS, no de quien instala
 * la app.
 *
 * QUÉ COMPRUEBA
 * -------------
 * Esa conclusión es cierta hoy y es exactamente el tipo de cosa que deja de serlo en
 * silencio: basta con que alguien cablee el bundle JS en `assets` o añada una
 * dependencia sobre la salida de `comun/build` para que las tres alertas pasen de
 * «no alcanza al usuario» a «va dentro del APK». Sin esta prueba ese cambio no avisa.
 *
 *   1. El único consumidor del lockfile sigue siendo el target JS con `nodejs()`.
 *   2. La app consume `comun/` por fuentes, no por artefactos compilados de JS.
 *   3. El build de la app no referencia el lockfile ni la salida JS de `comun`.
 *   4. Si `serialize-javascript` cambia de versión, se ve aquí: la versión fijada se
 *      afirma explícitamente para que el cambio obligue a releer las alertas en vez de
 *      colar de tapadillo.
 *
 * NO COMPRUEBA que el target JS sea seguro ni que sus pruebas pasen: no se compila en
 * este entorno y afirmar lo contrario sería un cascarón.
 */
class CadenaDeSuministroJsTest {

    /**
     * Localiza un fichero subiendo desde el directorio de trabajo de las pruebas.
     *
     * Es el idioma que ya usan [com.ceroX.app.modules.license.LicenciaRepoTest] y
     * [com.ceroX.app.modules.catalog.CatalogoAuditoriaTest]. La primera versión de esta
     * prueba calculaba la raíz del repo subiendo dos niveles desde el módulo de la app,
     * y falló con «falta comun/build.gradle.kts»: el número de niveles no es estable, el
     * marcador sí. Dos pruebas rotas por una suposición sobre el árbol.
     */
    private fun delRepo(rel: String): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, rel) }
            .firstOrNull { it.exists() }

    private fun leer(vararg ruta: String): String {
        val rel = ruta.joinToString("/")
        val f = delRepo(rel)
        assertTrue("falta $rel", f != null)
        return f!!.readText()
    }

    private val moduloApp: File = run {
        var d = File(".").absoluteFile
        while (!File(d, "src/main/java/com/ceroX/app").isDirectory && d.parentFile != null) {
            d = d.parentFile
        }
        require(File(d, "src/main/java/com/ceroX/app").isDirectory) { "no se encontró el árbol" }
        d
    }

    @Test
    fun el_lockfile_js_solo_lo_consume_el_target_js() {
        val buildComun = leer("comun", "build.gradle.kts")
        assertTrue(
            "el target JS declaró nodejs(), que es lo que usa kotlin-js-store/yarn.lock",
            buildComun.contains("js(IR)") && buildComun.contains("nodejs()"),
        )
        val lock = leer("comun", "kotlin-js-store", "yarn.lock")
        assertTrue("el lockfile fija serialize-javascript", lock.contains("serialize-javascript@"))
        // 4. Versión afirmada a propósito: si cambia, esta prueba falla y obliga a
        //    releer las alertas de Dependabot en vez de dar el cambio por bueno.
        assertTrue(
            "serialize-javascript ya no está en 6.0.2: releer las alertas de Dependabot " +
                "(GHSA-5c6j-r48x-rmvq alta, GHSA-qj8w-gfj5-8c6v moderada, GHSA-m5mm-p8h7-m29v baja) " +
                "y actualizar docs/PENDIENTES-EXTERNOS.md con la versión nueva",
            lock.contains("serialize-javascript@^6.0.2"),
        )
        assertTrue("mocha es quien arrastra la dependencia", lock.contains("mocha@"))
    }

    @Test
    fun la_app_consume_comun_por_fuentes_y_no_por_artefactos_js() {
        val buildApp = leer("android", "app", "build.gradle")
        assertTrue(
            "la app debe seguir incluyendo las fuentes de comun/ en su source set",
            buildApp.contains("comun/src/commonMain/kotlin"),
        )

        // Lo que convertiría las alertas en riesgo real para quien instala la app.
        //
        // Se mira el código activo, no el texto del fichero: la primera versión de
        // esta prueba buscaba la subcadena «kotlin-js-store» en cualquier parte y la
        // encontró en la propia declaración de `inputs.files` del build.gradle, que
        // existe justamente para que Gradle vuelva a ejecutar las pruebas cuando ese
        // lockfile cambia. O sea que el verificador se delataba a sí mismo y nacía en
        // rojo por la línea que lo hace útil. Un comentario que explica por qué algo
        // está prohibido no es una violación; la disciplina es la misma que aplica
        // CoherenciaDePruebasTest a las prohibiciones del proyecto.
        val activo = sinComentarios(buildApp).sinDeclaracionDeEntradas()
        listOf("kotlin-js-store", "yarn.lock", "comun/build", "jsBrowser", "jsNode").forEach {
            assertFalse(
                "el build de la app consume «$it»: el toolchain JS dejaría de ser sólo " +
                    "de construcción y las tres alertas de Dependabot pasarían a afectar " +
                    "al producto. Revisar esta prueba y docs/PENDIENTES-EXTERNOS.md.",
                activo.contains(it),
            )
        }
    }

    /** Quita comentarios de línea y de bloque: lo que importa es el código activo. */
    private fun sinComentarios(texto: String): String {
        val sinBloque = Regex("(?s)/[*].*?[*]/").replace(texto, "")
        return sinBloque.lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")
    }

    /**
     * Quita la declaración de entradas de la tarea de pruebas.
     *
     * Menciona el lockfile a propósito: es lo que hace que Gradle vuelva a ejecutar
     * estas pruebas cuando cambia. Excluirla no debilita la guardia, porque lo que la
     * guardia busca es que la app **consuma** la salida JS, y una entrada de tarea no
     * consume nada.
     */
    private fun String.sinDeclaracionDeEntradas(): String {
        val inicio = indexOf("inputs.files(")
        if (inicio < 0) return this
        val fin = indexOf(").withPropertyName(", inicio)
        return if (fin < 0) this else substring(0, inicio) + substring(fin)
    }

    @Test
    fun ninguna_fuente_de_la_app_carga_el_bundle_js() {
        val fuentes = File(moduloApp, "src/main/java")
        require(fuentes.isDirectory) { "no está src/main/java" }
        val culpables = fuentes.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val t = f.readText()
                // QuickJS sí ejecuta JS, pero el suyo es un motor nativo con guiones
                // propios: no es el bundle de webpack que produce el target JS de comun.
                t.contains("comun/build") || t.contains("kotlin-js-store")
            }
            .map { it.name }
            .toList()
        assertTrue("fuentes que cargan la salida JS de comun: $culpables", culpables.isEmpty())
    }
}
