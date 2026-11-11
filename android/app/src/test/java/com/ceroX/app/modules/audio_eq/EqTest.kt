package com.ceroX.app.modules.audio_eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El ecualizador hace lo que dice.
 *
 * Las tres primeras pruebas son de verdad: `Perfiles` no toca nada de Android, así
 * que se ejecutan las curvas y la interpolación en el JVM. El resto comprueba el
 * cableado, que es lo que se rompe en silencio: un módulo que no se registra no
 * falla, simplemente no existe.
 *
 * La parte que no se puede probar aquí es la que depende del DSP: adjuntar efectos
 * sólo tiene sentido en un dispositivo. Por eso `Ecualizador` devuelve motivos en
 * vez de booleanos, y las pruebas de abajo exigen que esos motivos existan.
 */
class EqTest {

    private val raiz = File("src/main/java/com/ceroX/app")

    private fun fuente(rel: String): String {
        val f = File(raiz, rel)
        assertTrue("falta $rel", f.isFile)
        return f.readText()
    }

    @Test
    fun `los perfiles son curvas reales y no adornos`() {
        val perfiles = Perfiles.todos
        assertTrue("hay menos perfiles de los que se enseñan", perfiles.size >= 10)

        for (perfil in perfiles) {
            assertEquals(
                "${perfil.nombre}: la curva tiene que cubrir las diez frecuencias ISO",
                10, perfil.puntos.size,
            )
            val frecuencias = perfil.puntos.map { it.first }
            assertEquals(
                "${perfil.nombre}: las frecuencias tienen que subir",
                frecuencias.sorted(), frecuencias,
            )
            assertEquals(
                "${perfil.nombre}: frecuencias repetidas",
                frecuencias.size, frecuencias.distinct().size,
            )
            for ((hz, db) in perfil.puntos) {
                assertTrue("${perfil.nombre}: $hz Hz no es una frecuencia ISO", hz in 20..20000)
                assertTrue(
                    "${perfil.nombre}: $db dB a $hz Hz se sale de ±12, que es donde empieza el recorte audible",
                    db >= -12f && db <= 12f,
                )
            }
        }

        val plano = Perfiles.porNombre("Plano")
        assertNotNull("falta el perfil Plano", plano)
        assertTrue(
            "el perfil Plano tiene que dejar todo a cero",
            plano!!.puntos.all { it.second == 0f },
        )
        assertNull("un perfil que no existe tiene que dar null", Perfiles.porNombre("Inventado"))
    }

    @Test
    fun `la interpolacion es logaritmica y no se cae fuera de rango`() {
        val graves = Perfiles.porNombre("Graves")!!

        // En un punto exacto de la curva, el valor es el de la curva.
        // JUnit 4 no tiene assertEquals(float, float, float) y Kotlin no convierte
        // Float en Double por su cuenta: se pasa a Double a proposito.
        assertEquals(6.0, Perfiles.interpolar(graves, 31).toDouble(), 0.001)
        assertEquals(0.0, Perfiles.interpolar(graves, 16000).toDouble(), 0.001)

        // Entre dos puntos, en medio: 31 Hz son 6 dB y 62 Hz son 5 dB, y en la
        // escala logarítmica 43-45 Hz cae cerca del punto medio.
        val medio = Perfiles.interpolar(graves, 44)
        assertTrue("44 Hz dio $medio dB, fuera del tramo 5..6", medio > 5f && medio < 6f)

        // Fuera de rango va el valor del extremo, no cero: poner cero crearía un
        // escalón en el borde de la curva.
        assertEquals(6.0, Perfiles.interpolar(graves, 10).toDouble(), 0.001)
        assertEquals(0.0, Perfiles.interpolar(graves, 40000).toDouble(), 0.001)

        // Y el perfil plano da cero en cualquier frecuencia.
        val plano = Perfiles.porNombre("Plano")!!
        for (hz in listOf(20, 100, 999, 12345, 20000)) {
            assertEquals("Plano a $hz Hz", 0.0, Perfiles.interpolar(plano, hz).toDouble(), 0.0001)
        }
    }

    @Test
    fun `la curva se traduce a milibelios enteros para el DSP`() {
        // Tres bandas inventadas con centros que no caen en las frecuencias ISO,
        // que es lo normal en un dispositivo real.
        val bandas = listOf(
            Banda(0, 60, 31, 100, 0, -1500, 1500),
            Banda(1, 700, 400, 1200, 0, -1500, 1500),
            Banda(2, 9000, 6000, 14000, 0, -1500, 1500),
        )
        val graves = Perfiles.porNombre("Graves")!!
        val curva = Perfiles.curvaPara(graves, bandas)
        assertEquals(3, curva.size)
        assertTrue("60 Hz tendria que ir reforzado, dio ${curva[0]}", curva[0] > 400)
        assertTrue("9 kHz no lleva realce de graves, dio ${curva[2]}", curva[2] == 0)

        val agudos = Perfiles.porNombre("Agudos")!!
        val curvaAgudos = Perfiles.curvaPara(agudos, bandas)
        assertTrue("9 kHz tendria que ir reforzado, dio ${curvaAgudos[2]}", curvaAgudos[2] > 400)
        assertEquals(0, curvaAgudos[0])
    }

    @Test
    fun `la app registra el modulo de ecualizador en las dos listas`() {
        val app = fuente("core/CeroXApplication.kt")
        assertTrue(
            "audio-eq no esta en la lista de metadatos: sin ella el gestor no lo registra",
            app.contains("ModuleMetadata(\"audio-eq\""),
        )
        assertTrue(
            "audio-eq no tiene clase asignada: el bucle de registro lo saltaria en silencio",
            app.contains("\"audio-eq\" to com.ceroX.app.modules.audio_eq.EcualizadorModule::class.java"),
        )
    }

    @Test
    fun `el manifiesto pide el permiso que hace falta para la mezcla de salida`() {
        val manifiesto = File("src/main/AndroidManifest.xml")
        assertTrue("falta el manifiesto", manifiesto.isFile)
        val t = manifiesto.readText()
        assertTrue(
            "sin MODIFY_AUDIO_SETTINGS no se puede adjuntar efectos a la sesion 0, " +
                "que es lo que hace un ecualizador independiente",
            t.contains("android.permission.MODIFY_AUDIO_SETTINGS"),
        )
    }

    @Test
    fun `el reproductor expone su sesion de audio`() {
        val audio = fuente("modules/audio/AudioModule.kt")
        assertTrue(
            "AudioModule no expone su id de sesion, asi que el ecualizador no puede " +
                "adjuntarse a la reproduccion de la app",
            audio.contains("fun audioSessionId(): Int?"),
        )
        assertTrue(
            "getAudioSessionId lanza IllegalStateException con el reproductor en un " +
                "estado invalido; sin capturarlo revienta quien pregunte",
            audio.contains("catch (e: IllegalStateException)"),
        )
        val modulo = fuente("modules/audio_eq/EcualizadorModule.kt")
        assertTrue(
            "el modulo no sabe pedirle la sesion al reproductor",
            modulo.contains("audioSessionId()"),
        )
    }

    @Test
    fun `el envoltorio de efectos no devuelve exitos fabricados`() {
        val t = fuente("modules/audio_eq/Ecualizador.kt")
        assertEquals(
            "un metodo que devuelve un motivo no necesita devolver true; si aparece, " +
                "hay un exito afirmado sin comprobacion",
            0, t.split("\n").count { it.trim() == "return true" },
        )
        for (efecto in listOf("Equalizer(", "BassBoost(", "Virtualizer(", "LoudnessEnhancer(")) {
            assertTrue("no se construye $efecto", t.contains(efecto))
        }
        assertTrue(
            "adjuntar efectos lanza en dispositivos sin DSP; sin try no se informa",
            t.contains("catch (e: Exception)"),
        )
        assertTrue(
            "el resultado de adjuntar tiene que poder decir por que no hay nada",
            t.contains("val motivo: String?"),
        )
        assertTrue(
            "los efectos del sistema son recursos compartidos: hay que soltarlos",
            t.contains("release()"),
        )
        assertTrue(
            "el nivel de banda se tiene que recortar al rango que declara el dispositivo",
            t.contains("coerceIn(rango[0].toInt(), rango[1].toInt())"),
        )
    }

    @Test
    fun `el health check cuenta lo que hay adjunto de verdad`() {
        val t = fuente("modules/audio_eq/EcualizadorModule.kt")
        for (clave in listOf(
            "efectos_adjuntos", "sesion", "bandas", "perfil_activo",
            "motivo_sin_efectos", "intentos_de_adjuntar",
        )) {
            assertTrue("el health check no informa de $clave", t.contains("\"$clave\" to"))
        }
        assertTrue(
            "initialize no puede adjuntar efectos a una sesion que aun no existe; " +
                "si lo hiciera, estaria afirmando un estado que no comprobo",
            !t.contains("override fun initialize(config: Map<String, Any>): Boolean {\n        adjuntar"),
        )
        assertTrue(
            "sin contexto no hay AudioManager, y el modulo tiene que decirlo en vez " +
                "de devolver un estado con ceros",
            t.contains("fun volumen(): EstadoVolumen?"),
        )
        assertTrue(
            "al parar el modulo hay que soltar los efectos",
            t.contains("ecualizador.liberar()"),
        )
    }

    @Test
    fun `los pasos de volumen leen el sistema y no prometen lo que no pueden`() {
        val t = fuente("modules/audio_eq/PasosDeVolumen.kt")
        for (llamada in listOf("getStreamMaxVolume", "getStreamVolume", "setStreamVolume")) {
            assertTrue("no se llama a AudioManager.$llamada", t.contains(llamada))
        }
        assertTrue(
            "la ganancia fina va por LoudnessEnhancer, que es lo unico que puede " +
                "hacerse sin modificar el framework",
            t.contains("LoudnessEnhancer"),
        )
        assertTrue(
            "Android 11+ lanza SecurityException con AVRCP absoluto; sin capturarlo " +
                "muerde al usuario con auriculares Bluetooth",
            t.contains("catch (e: Exception)"),
        )
        // Ojo con lo que se busca aqui: la version anterior de esta prueba buscaba
        // una frase que el salto de linea del KDoc partia en dos ("...una app no
        // puede" terminaba una linea y "cambiarlo..." empezaba la siguiente), y por
        // eso fallaba aunque el documento dijera exactamente lo que se exigia. Las
        // dos cadenas de abajo viven cada una entera en su linea.
        assertTrue(
            "el documento del fichero tiene que decir que una app no puede cambiar " +
                "los pasos del volumen del sistema",
            t.contains("una app no puede"),
        )
        assertTrue(
            "y tiene que decir que aqui no se reescribe el framework, que es lo " +
                "unico que permitiria cambiarlos",
            t.contains("no lleva nada que reescriba el framework"),
        )
    }
}
