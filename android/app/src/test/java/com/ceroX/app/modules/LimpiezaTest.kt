package com.ceroX.app.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Que lo quitado en la limpieza no vuelva, y que lo que queda no mienta.
 *
 * Estas pruebas leen el fuente del repositorio, no el comportamiento: vigilan que
 * nadie vuelva a escribir una búsqueda que fabrica resultados, una bandera que no
 * toca nada, o un healthCheck que informa de una función inexistente. Son baratas de
 * romper a propósito y caras de romper por accidente, que es lo que hace falta para
 * un patrón que este repo repitió sesenta y tantas veces.
 *
 * Nota sobre los nombres: las aserciones buscan los identificadores tal cual. Por eso
 * los KDoc de los módulos limpiados explican lo que había sin repetir el nombre
 * exacto —el detalle forense está en `docs/LIMPIEZA.md`— y una mención en un
 * comentario no vuelve a colar el código borrado por la puerta de atrás de un grep.
 */
class LimpiezaTest {

    private val main: File by lazy {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "android/app/src/main") }
            .firstOrNull { it.isDirectory }
        assertTrue("no encuentro android/app/src/main desde " + File("").absolutePath, f != null)
        f!!
    }

    private val src: File get() = File(main, "java/com/ceroX/app")

    /**
     * Una comilla doble, construida como Char para no tener que escaparla.
     *
     * Varias aserciones de esta prueba buscan literales Kotlin dentro del fuente
     * (`"ffmpeg"`, `config["context"]`) y escribirlas con barras invertidas dentro
     * de una cadena es justo lo que rompió la primera versión de este fichero.
     */
    private val q: String = '"'.toString()

    private fun leer(rel: String): String {
        val f = File(src, rel)
        assertTrue("falta $rel", f.exists())
        return f.readText()
    }

    private fun todosLosKotlin(): List<File> =
        src.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun elAudioYaNoInventaResultadosDeBusqueda() {
        val a = leer("modules/audio/AudioModule.kt")
        assertFalse("vuelve el método de búsqueda fabricada", a.contains("searchWotaku"))
        assertFalse("vuelve el título inventado", a.contains("Resultado Wotaku"))
        assertTrue("y no dice lo que le falta", a.contains("No busca en ninguna parte"))
        assertTrue("play() debe fallar si no hay reproductor", a.contains("mediaPlayer ?: return false"))
    }

    @Test
    fun elVideoYaNoInformaDeUnaAceleracionQueNoTocaNada() {
        val v = leer("modules/video/VideoModule.kt")
        assertFalse("vuelve la bandera de aceleración", v.contains("hwAccel"))
        assertFalse("vuelve su clave de config", v.contains("hw_accel"))
        assertTrue("y no explica por qué no la tiene", v.contains("No tiene aceleración por hardware"))
        assertTrue("el contexto tiene que llegar por el config", v.contains("config[" + q + "context" + q + "]"))
        val c = leer("core/ConfigManager.kt")
        assertFalse("el default sigue en ConfigManager", c.contains("hw_accel"))
        assertFalse("y sigue declarando un backend de vídeo que no existe", c.contains(q + "ffmpeg" + q))
        assertFalse("y un backend de audio que tampoco", c.contains(q + "wotaku" + q))
    }

    @Test
    fun elMediaYaNoDevuelveUnEnlaceFalsoDeYouTube() {
        val m = leer("modules/media/MediaModule.kt")
        assertFalse("vuelve la búsqueda de YouTube", m.contains("searchYouTube"))
        assertFalse("vuelve la URL de relleno", m.contains("watch?v=placeholder"))
        assertFalse("vuelve la bandera de interfaz que no pintaba nada", m.contains("compose_ui"))
        assertTrue("y no dice lo que le falta", m.contains("No busca en YouTube"))
        assertFalse("YouTubeSupport.kt debía estar borrado",
            File(src, "modules/youtube_support/YouTubeSupport.kt").exists())
    }

    @Test
    fun namidaSoloApareceParaDecirQueQuedaFuera() {
        // Namida es una EULA propietaria: prohíbe redistribuir, reempaquetar y usar
        // partes del código. Nombrarlo para explicar por qué no se usa es justo lo
        // que hay que hacer; nombrarlo como fuente del propio código es lo que se
        // quitó. La prueba exige que toda mención venga con la razón.
        val sinExplicar = todosLosKotlin()
            .map { it to it.readText() }
            .filter { par -> par.second.contains("Namida", ignoreCase = true) }
            .filter { par -> !par.second.contains("EULA propietaria") }
            .map { par -> par.first.relativeTo(src).path.replace(File.separatorChar, '/') }
        assertEquals("mencionan Namida sin decir por qué queda fuera", emptyList<String>(), sinExplicar)

        val t = leer("modules/theme/ThemeEngineModule.kt")
        assertFalse("la paleta sigue con el id viejo", t.contains("namida_music_video"))
        assertTrue("y no dice de dónde salió", t.contains("cero-X (paleta propia)"))
        assertFalse("el healthCheck sigue listando marcas como si fueran funciones",
            t.contains("supported_designs"))
        assertFalse("sigue habiendo un !! que cierra la pantalla de Diseño",
            t.contains("currentThemeId }!!"))

        val xml = File(main, "res/layout/fragment_theme_switcher.xml")
        assertTrue("falta el layout de la pantalla de Diseño", xml.exists())
        assertFalse("la pantalla sigue ofreciendo un diseño de Namida",
            xml.readText().contains("namida", ignoreCase = true))
    }

    @Test
    fun losHealthChecksYaNoInformanDeFuncionesQueNoExisten() {
        val u = leer("modules/updates/UpdatesModule.kt")
        assertFalse("vuelve el auto-update que no existe", u.contains("auto_update_enabled"))
        assertTrue("y no admite que no hay servidor", u.contains("servidor_de_actualizaciones"))
        assertFalse("la versión sigue escrita a mano", u.contains("0.1.0-beta"))
        val n = leer("modules/updates/NightlyUpdater.kt")
        assertFalse("y aquí también", n.contains("0.2.0-beta-android16"))
        assertTrue("ambos deben leer la versión de verdad", n.contains("versionRealDeLaApp"))
    }

    @Test
    fun elContextoLlegaALosModulosQueLanzanIntents() {
        // VideoModule.play() y UpdatesModule.installUpdate() devolvían false siempre:
        // leían config["context"] y ConfigManager sólo guarda preferencias.
        val g = leer("core/engine/ModuleLifecycleManager.kt")
        assertTrue("el gestor debe inyectar el contexto de la aplicación",
            g.contains("configMap[" + q + "context" + q + "] = context.applicationContext"))
    }
}
