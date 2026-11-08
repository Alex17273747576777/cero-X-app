package com.ceroX.app.modules.downloads_advanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cola de 100 000 recursos: lo que importa no es que quepan, es que no
 * puedan romper nada.
 */
class QueueBudgetTest {

    private fun res(
        url: String = "https://example.com/a.cbz",
        kind: ResourceKind = ResourceKind.MANGA,
        bytes: Long = 1_000,
        name: String = "a.cbz",
    ) = DownloadResource(id = url, url = url, fileName = name, kind = kind, bytesExpected = bytes)

    // ── El tope es 100 000 y es duro ─────────────────────────────────────────

    @Test
    fun `el tope son cien mil recursos`() {
        assertEquals(100_000, QueueLimits.MAX_RESOURCES)
    }

    @Test
    fun `la cola rechaza el recurso numero cien mil uno`() {
        val q = QueueBudget(capacity = 100_000)
        for (i in 0 until 100_000) {
            val a = q.admit(res(url = "https://e.com/$i.cbz"))
            assertTrue("falló en $i: ${a.why}", a.accepted)
        }
        assertEquals(100_000, q.count)
        val overflow = q.admit(res(url = "https://e.com/overflow.cbz"))
        assertFalse(overflow.accepted)
        assertEquals(RejectReason.QUEUE_FULL, overflow.reason)
    }

    @Test
    fun `la cola vacia tiene todos los huecos libres`() {
        assertEquals(100_000, QueueBudget().remainingSlots())
    }

    // ── Duplicados: la misma URL no se descarga dos veces ────────────────────

    @Test
    fun `una url repetida se rechaza y no cuenta`() {
        val q = QueueBudget()
        assertTrue(q.admit(res(url = "https://e.com/x.cbz")).accepted)
        val dup = q.admit(res(url = "https://e.com/x.cbz"))
        assertFalse(dup.accepted)
        assertEquals(RejectReason.DUPLICATE, dup.reason)
        assertEquals(1, q.count)
    }

    // ── Límites por tipo: el tipo decide el techo ────────────────────────────

    @Test
    fun `un manga de 600 MiB se rechaza`() {
        val q = QueueBudget()
        val a = q.admit(res(kind = ResourceKind.MANGA, bytes = 600L * 1024 * 1024))
        assertFalse(a.accepted)
        assertEquals(RejectReason.TOO_BIG, a.reason)
    }

    @Test
    fun `un video de 600 MiB se acepta, porque su techo es mayor`() {
        val q = QueueBudget()
        val a = q.admit(res(kind = ResourceKind.VIDEO, bytes = 600L * 1024 * 1024, url = "https://e.com/v.mp4"))
        assertTrue(a.why, a.accepted)
    }

    @Test
    fun `el tipo desconocido cae en OTRO y no en manga`() {
        assertEquals(ResourceKind.OTHER, ResourceKind.classify("instalador.exe"))
        assertEquals(ResourceKind.OTHER, ResourceKind.classify("sin_extension"))
    }

    @Test
    fun `la clasificacion por extension acierta en los cuatro tipos`() {
        assertEquals(ResourceKind.MANGA, ResourceKind.classify("Tomo 01.CBZ"))
        assertEquals(ResourceKind.VIDEO, ResourceKind.classify("cap.mkv"))
        assertEquals(ResourceKind.BOOK, ResourceKind.classify("libro.epub"))
        assertEquals(ResourceKind.AUDIO, ResourceKind.classify("pista.flac"))
    }

    @Test
    fun `epub es libro aunque tambien se use para comics`() {
        // La ambiguedad se resuelve en un mapa a la vista, no por el orden del
        // enum. Sin esto, mover BOOK arriba en el enum cambiaba el resultado.
        assertEquals(ResourceKind.BOOK, ResourceKind.classify("comic.epub"))
        assertEquals(ResourceKind.BOOK, ResourceKind.classify("COMIC.EPUB"))
    }

    @Test
    fun `ninguna extension pertenece a dos tipos a la vez`() {
        val todas = ResourceKind.entries.filter { it != ResourceKind.OTHER }
        val vistas = HashMap<String, ResourceKind>()
        todas.forEach { k ->
            k.extensions.forEach { ext ->
                val previo = vistas.put(ext, k)
                assertTrue(
                    "la extension '$ext' esta en $previo y en $k: el resultado dependeria del orden del enum",
                    previo == null,
                )
            }
        }
    }

    // ── Total: 2 TiB y no se pasa ────────────────────────────────────────────

    @Test
    fun `el total declarado no puede pasar del tope`() {
        val q = QueueBudget(maxTotalBytes = 1_000)
        assertTrue(q.admit(res(url = "https://e.com/1", bytes = 600)).accepted)
        val a = q.admit(res(url = "https://e.com/2", bytes = 600))
        assertFalse(a.accepted)
        assertEquals(RejectReason.TOTAL_EXCEEDED, a.reason)
        assertEquals(600, q.totalBytes)
    }

    @Test
    fun `el tope total por defecto son dos tebibytes`() {
        assertEquals(2L * 1024 * 1024 * 1024 * 1024, QueueLimits.MAX_TOTAL_BYTES)
    }

    // ── URL: solo http y https ───────────────────────────────────────────────

    @Test
    fun `una url que no es http se rechaza`() {
        val q = QueueBudget()
        listOf("ftp://e.com/a", "file:///etc/passwd", "javascript:alert(1)", "/local/a.cbz").forEach { u ->
            val a = q.admit(res(url = u))
            assertFalse("se coló: $u", a.accepted)
            assertEquals(RejectReason.BAD_URL, a.reason)
        }
    }

    @Test
    fun `http y https se aceptan`() {
        val q = QueueBudget()
        assertTrue(q.admit(res(url = "http://e.com/a")).accepted)
        assertTrue(q.admit(res(url = "https://e.com/b")).accepted)
    }

    // ── Rechazado significa no contado ───────────────────────────────────────

    @Test
    fun `un rechazo no ensucia las cuentas`() {
        val q = QueueBudget()
        q.admit(res(url = "ftp://malo"))
        assertEquals(0, q.count)
        assertEquals(0, q.totalBytes)
        assertEquals(100_000, q.remainingSlots())
    }

    // ── Progreso honesto ─────────────────────────────────────────────────────

    @Test
    fun `sin tamano esperado el progreso es cero, no cien`() {
        val r = DownloadResource("1", "https://e.com/a", "a", ResourceKind.MANGA, bytesExpected = 0, bytesWritten = 5_000)
        assertEquals(0.0, r.fraction, 0.0001)
        assertFalse("no se puede declarar completo sin saber el total", r.complete)
    }

    @Test
    fun `completo solo si lo escrito alcanza lo esperado`() {
        val r = DownloadResource("1", "https://e.com/a", "a", ResourceKind.MANGA, bytesExpected = 100, bytesWritten = 100)
        assertTrue(r.complete)
        assertEquals(1.0, r.fraction, 0.0001)
    }

    @Test
    fun `escribir de mas no da mas de cien por ciento`() {
        val r = DownloadResource("1", "https://e.com/a", "a", ResourceKind.MANGA, bytesExpected = 100, bytesWritten = 150)
        assertEquals(1.0, r.fraction, 0.0001)
    }

    // ── Concurrencia: el fallo de las 7 descargas no vuelve ──────────────────

    @Test
    fun `el planificador respeta el limite global`() {
        val p = ConcurrencyPlanner(globalLimit = 1)
        assertTrue(p.start("a.com"))
        assertFalse("con límite 1 no entra la segunda", p.start("b.com"))
        p.finish("a.com")
        assertTrue(p.start("b.com"))
    }

    @Test
    fun `el limite por host impide que dos archivos del mismo servidor se peleen`() {
        val p = ConcurrencyPlanner(globalLimit = 10, perHostLimit = 2)
        assertTrue(p.start("a.com"))
        assertTrue(p.start("a.com"))
        assertFalse(p.start("a.com"))
        assertEquals(2, p.activeFor("a.com"))
        // Otro host sí entra.
        assertTrue(p.start("b.com"))
    }

    @Test
    fun `cerrar mas veces de la cuenta no deja el contador negativo`() {
        val p = ConcurrencyPlanner(globalLimit = 2)
        p.start("a.com")
        p.finish("a.com")
        p.finish("a.com")
        assertEquals(0, p.activeCount())
    }

    @Test
    fun `un limite de cero no se permite`() {
        runCatching { ConcurrencyPlanner(globalLimit = 0) }.let {
            assertTrue("un planificador sin paralelismo no debe construirse", it.isFailure)
        }
    }

    // ── Reintentos con techo ─────────────────────────────────────────────────

    @Test
    fun `la espera crece y tiene techo`() {
        assertEquals(1_000L, RetryPolicy.delayFor(0))
        assertEquals(2_000L, RetryPolicy.delayFor(1))
        assertEquals(4_000L, RetryPolicy.delayFor(2))
        assertEquals(RetryPolicy.MAX_DELAY_MS, RetryPolicy.delayFor(20))
        assertEquals(RetryPolicy.MAX_DELAY_MS, RetryPolicy.delayFor(200))
    }

    @Test
    fun `solo se reintenta hasta el maximo`() {
        assertTrue(RetryPolicy.shouldRetry(0))
        assertTrue(RetryPolicy.shouldRetry(2))
        assertFalse(RetryPolicy.shouldRetry(3))
    }

    @Test
    fun `un recurso con los intentos agotados se marca`() {
        val r = res()
        assertTrue(r.copy(attempt = QueueLimits.MAX_ATTEMPTS).exhausted(QueueLimits.MAX_ATTEMPTS))
        assertFalse(r.copy(attempt = 1).exhausted(QueueLimits.MAX_ATTEMPTS))
    }
}
