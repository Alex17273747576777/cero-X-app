package com.ceroX.app.modules.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El motor de rendimiento es una función pura, así que se prueba entera sin
 * dispositivo. Se prueban las **decisiones**, no que "no explote".
 */
class PerformanceEngineTest {

    private fun state(
        battery: Int = 80,
        charging: Boolean = false,
        thermal: ThermalStatus = ThermalStatus.NONE,
        network: NetworkClass = NetworkClass.WIFI,
        ram: Long = 2_048,
        lowRam: Boolean = false,
    ) = DeviceState(battery, charging, thermal, network, ram, lowRam)

    // ── Contrato básico: todo perfil declara qué pierde ──────────────────────

    @Test
    fun `ningun perfil se presenta como gratis`() {
        PerformanceProfile.entries.forEach { p ->
            assertTrue("${p.id} no dice que gana", p.gains.isNotBlank())
            assertTrue("${p.id} no dice que pierde", p.costs.isNotBlank())
        }
    }

    // ── El orden de NetworkClass es semántico, no alfabético ─────────────────

    @Test
    fun `el orden de las redes va de peor a mejor`() {
        assertTrue(NetworkClass.OFFLINE < NetworkClass.CELLULAR_SLOW)
        assertTrue(NetworkClass.CELLULAR_SLOW < NetworkClass.CELLULAR)
        assertTrue(NetworkClass.CELLULAR < NetworkClass.WIFI)
        assertTrue(NetworkClass.WIFI < NetworkClass.ETHERNET)
    }

    @Test
    fun `solo wifi o cable permiten prefetch`() {
        assertFalse(NetworkClass.CELLULAR.canPrefetch)
        assertTrue(NetworkClass.WIFI.canPrefetch)
        assertEquals(0, NetworkClass.OFFLINE.prefetchPages)
        assertEquals(1, NetworkClass.CELLULAR.prefetchPages)
        assertEquals(3, NetworkClass.WIFI.prefetchPages)
    }

    // ── Ahorro: lo que dice que hace, lo hace ────────────────────────────────

    @Test
    fun `ahorro apaga la IA de imagen y limita a 30 fps`() {
        val d = PerformanceEngine.decide(PerformanceProfile.POWER_SAVER, state())
        assertEquals(ImagePolicy.NONE, d.imageModel)
        assertEquals(30, d.maxFps)
        assertFalse(d.onDeviceAi)
        assertEquals(1, d.concurrentDownloads)
        assertFalse(d.networkAcceleration)
    }

    // ── Calidad: declara el coste real medido ────────────────────────────────

    @Test
    fun `calidad usa el modelo lento y lo dice`() {
        val d = PerformanceEngine.decide(PerformanceProfile.QUALITY, state())
        assertEquals(ImagePolicy.UPSCALE_MAX, d.imageModel)
        assertTrue("el motivo tiene que avisar de los 24,6 s", d.reason.contains("24,6"))
    }

    @Test
    fun `rendimiento usa el modelo rapido, no el lento`() {
        val d = PerformanceEngine.decide(PerformanceProfile.PERFORMANCE, state())
        assertEquals(ImagePolicy.UPSCALE_FAST, d.imageModel)
        assertNotEquals(ImagePolicy.UPSCALE_MAX, d.imageModel)
    }

    // ── FPS y Hz: lo que el usuario pidió, con nombre ────────────────────────

    @Test
    fun `rendimiento fija los fps y no deja que bajen los hz`() {
        val d = PerformanceEngine.decide(PerformanceProfile.PERFORMANCE, state())
        assertEquals(120, d.maxFps)
        assertFalse("FPS estables exige no bajar Hz a media lectura", d.dynamicRefreshRate)
    }

    @Test
    fun `ahorro si permite bajar los hz`() {
        val d = PerformanceEngine.decide(PerformanceProfile.POWER_SAVER, state())
        assertTrue(d.dynamicRefreshRate)
    }

    // ── Adaptativo: el orden de prioridad importa ────────────────────────────

    @Test
    fun `termico severo manda sobre todo lo demas`() {
        val d = PerformanceEngine.decide(
            PerformanceProfile.ADAPTIVE,
            state(battery = 99, charging = true, thermal = ThermalStatus.SEVERE, ram = 4_096),
        )
        assertEquals(30, d.maxFps)
        assertEquals(ImagePolicy.NONE, d.imageModel)
        assertTrue(d.reason.contains("térmico"))
    }

    @Test
    fun `bateria critica sin cargar va a ahorro`() {
        val d = PerformanceEngine.decide(
            PerformanceProfile.ADAPTIVE,
            state(battery = 10, charging = false),
        )
        assertEquals(30, d.maxFps)
        assertTrue(d.reason.contains("ahorro"))
    }

    @Test
    fun `bateria critica pero cargando no obliga a ahorro`() {
        val d = PerformanceEngine.decide(
            PerformanceProfile.ADAPTIVE,
            state(battery = 10, charging = true, thermal = ThermalStatus.LIGHT, ram = 2_048),
        )
        assertNotEquals(ImagePolicy.NONE, d.imageModel)
        assertTrue(d.reason.contains("rendimiento"))
    }

    @Test
    fun `cargando con buen estado va a rendimiento`() {
        val d = PerformanceEngine.decide(
            PerformanceProfile.ADAPTIVE,
            state(battery = 60, charging = true, thermal = ThermalStatus.NONE, ram = 3_000),
        )
        assertEquals(120, d.maxFps)
        assertEquals(ImagePolicy.UPSCALE_FAST, d.imageModel)
    }

    @Test
    fun `cargando pero con poca red no acelera`() {
        val d = PerformanceEngine.decide(
            PerformanceProfile.ADAPTIVE,
            state(charging = true, network = NetworkClass.CELLULAR, ram = 3_000),
        )
        // El escalón de rendimiento exige wifi o cable; con cellular cae al default.
        assertNotEquals(120, d.maxFps)
    }

    @Test
    fun `ram justa lleva a ahorro aunque la bateria este bien`() {
        val d = PerformanceEngine.decide(
            PerformanceProfile.ADAPTIVE,
            state(battery = 90, ram = 300),
        )
        assertEquals(30, d.maxFps)
        assertTrue(d.reason.contains("MB libres"))
    }

    @Test
    fun `el adaptativo nunca elige calidad por su cuenta`() {
        // Ni con las mejores condiciones posibles.
        val d = PerformanceEngine.decide(
            PerformanceProfile.ADAPTIVE,
            state(battery = 100, charging = true, thermal = ThermalStatus.NONE,
                network = NetworkClass.ETHERNET, ram = 8_192),
        )
        assertNotEquals(
            "24,6 s por imagen no lo decide la app sin avisar",
            ImagePolicy.UPSCALE_MAX, d.imageModel,
        )
    }

    // ── Determinismo: la misma entrada da la misma salida ────────────────────

    @Test
    fun `la decision es reproducible`() {
        val s = state(battery = 42, charging = false, thermal = ThermalStatus.LIGHT)
        val a = PerformanceEngine.decide(PerformanceProfile.ADAPTIVE, s)
        val b = PerformanceEngine.decide(PerformanceProfile.ADAPTIVE, s)
        assertEquals(a, b)
    }

    // ── Todo motivo explica algo, ninguno queda vacío ────────────────────────

    @Test
    fun `todos los perfiles explican su decision`() {
        PerformanceProfile.entries.forEach { p ->
            val d = PerformanceEngine.decide(p, state())
            assertTrue("${p.id} no explica nada", d.reason.isNotBlank())
        }
    }

    // ── Descargas concurrentes: el fallo medido no vuelve ────────────────────

    @Test
    fun `ningun perfil lanza mas de dos descargas a la vez`() {
        PerformanceProfile.entries.forEach { p ->
            val d = PerformanceEngine.decide(p, state(network = NetworkClass.ETHERNET))
            assertTrue(
                "${p.id} pide ${d.concurrentDownloads} descargas; medido: 7 degradaban el hit rate a cero",
                d.concurrentDownloads <= 2,
            )
        }
    }
}
