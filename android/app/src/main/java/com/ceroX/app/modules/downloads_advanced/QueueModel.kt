package com.ceroX.app.modules.downloads_advanced

/**
 * Tipo de recurso descargable.
 *
 * `maxBytes` es el tamaño máximo por defecto de **un** recurso de este tipo. No
 * es una sugerencia: el descargador lo aplica antes de escribir el primer byte y
 * aborta si el servidor manda más. Un `Content-Length` mentiroso o un chunked
 * sin tamaño no pueden llenar el disco.
 */
enum class ResourceKind(
    val id: String,
    val displayName: String,
    val maxBytes: Long,
    /** Extensiones que se consideran de este tipo al clasificar. */
    val extensions: Set<String>,
) {
    MANGA(
        id = "manga",
        displayName = "Manga / cómic",
        maxBytes = 512L * 1024 * 1024,   // 512 MiB
        // `epub` NO está aquí a propósito. Es un formato de libro que también se
        // usa para cómics, así que pertenece a los dos tipos. Dejarlo en ambos
        // hacía que `classify` devolviera el primero del enum, es decir, que el
        // resultado dependiera del ORDEN DE DECLARACIÓN. Eso no es una decisión,
        // es un accidente. Se resuelve abajo: epub es BOOK, y un cómic en epub
        // sigue siendo un libro. La prueba lo fija.
        extensions = setOf("cbz", "cbr", "cb7", "cbt", "zip", "rar", "7z", "pdf"),
    ),
    VIDEO(
        id = "video",
        displayName = "Video",
        maxBytes = 8L * 1024 * 1024 * 1024,  // 8 GiB
        extensions = setOf("mp4", "mkv", "webm", "avi", "mov", "ts", "m4v"),
    ),
    BOOK(
        id = "book",
        displayName = "Libro",
        maxBytes = 256L * 1024 * 1024,   // 256 MiB
        extensions = setOf("epub", "fb2", "mobi", "azw3", "djvu", "txt", "md"),
    ),
    AUDIO(
        id = "audio",
        displayName = "Audio",
        maxBytes = 1024L * 1024 * 1024,  // 1 GiB
        extensions = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac"),
    ),
    OTHER(
        id = "otro",
        displayName = "Otro",
        maxBytes = 64L * 1024 * 1024,
        extensions = emptySet(),
    );

    companion object {
        /**
         * Extensiones ambiguas: pertenecen a más de un tipo y se resuelven aquí,
         * a la vista, en vez de depender del orden del enum.
         *
         *   epub → BOOK. Un cómic en epub sigue siendo un contenedor de libro.
         *
         * Si algún día aparece otra, se añade a este mapa. Lo que no se hace es
         * dejar que gane la primera declarada.
         */
        private val AMBIGUOUS = mapOf(
            "epub" to BOOK,
        )

        /**
         * Clasifica por extensión. Sin extensión y sin pista → OTHER, nunca
         * "manga" por defecto: asumir el tipo más permisivo para un archivo
         * desconocido es cómo un `.exe` acaba con un límite de 8 GiB.
         */
        fun classify(fileName: String): ResourceKind {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return OTHER
            AMBIGUOUS[ext]?.let { return it }
            return entries.firstOrNull { ext in it.extensions && it != OTHER } ?: OTHER
        }
    }
}

/**
 * De dónde sale el recurso. Sirve para agrupar y para decidir concurrencia por
 * host: no se lanzan 8 conexiones al mismo servidor aunque el total lo permita.
 */
data class ResourceOrigin(
    val sourceId: String,
    val host: String,
)

/**
 * Un recurso en la cola.
 *
 * `bytesExpected` es lo que dijo el servidor (0 = no lo dijo). `bytesWritten` es
 * lo que hay de verdad en disco. La diferencia entre los dos es el estado real
 * de la descarga, y por eso ambos se guardan: un "100 %" calculado sobre lo
 * esperado es falso si lo esperado era mentira.
 */
data class DownloadResource(
    val id: String,
    val url: String,
    val fileName: String,
    val kind: ResourceKind,
    val bytesExpected: Long = 0,
    val bytesWritten: Long = 0,
    val attempt: Int = 0,
) {
    val complete: Boolean
        get() = bytesExpected > 0 && bytesWritten >= bytesExpected

    val fraction: Double
        get() = if (bytesExpected <= 0) 0.0 else (bytesWritten.toDouble() / bytesExpected).coerceIn(0.0, 1.0)

    /** Se le acabaron los reintentos. */
    fun exhausted(maxAttempts: Int): Boolean = attempt >= maxAttempts
}

/**
 * El tope de la cola.
 *
 * **100 000 recursos** es el número que pidió el usuario y aquí es un límite
 * duro, no un objetivo. Lo importante no es que quepan 100 000: es que 100 000
 * no puedan romper la app. Por eso la cola tiene tres frenos:
 *
 *   1. `MAX_RESOURCES` — no se aceptan más.
 *   2. `MAX_TOTAL_BYTES` — el total declarado no puede pasar de aquí.
 *   3. No se guardan en memoria como objetos: la cola es un índice sobre disco,
 *      y aquí solo se cuentan. Ver [QueueBudget].
 */
object QueueLimits {
    const val MAX_RESOURCES = 100_000

    /**
     * 2 TiB de tope total. Un móvil actual tiene entre 64 y 512 GiB libres, así
     * que este límite no se alcanza por accidente: si se alcanza, alguien está
     * pidiendo algo que no cabe, y es mejor saberlo al encolar que a mitad.
     */
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024 * 1024

    /** Reintentos por recurso. Más de 3 es castigar a un servidor caído. */
    const val MAX_ATTEMPTS = 3

    /** Conexiones por host. El total lo decide el perfil de rendimiento. */
    const val MAX_PER_HOST = 2
}

/** Por qué se rechazó un recurso. Vacío = aceptado. */
enum class RejectReason(val id: String, val explanation: String) {
    NONE("", ""),
    QUEUE_FULL("cola_llena", "la cola ya tiene ${QueueLimits.MAX_RESOURCES} recursos"),
    TOO_BIG("demasiado_grande", "supera el tamaño máximo de su tipo"),
    TOTAL_EXCEEDED("total_excedido", "el total de la cola pasaría de 2 TiB"),
    BAD_URL("url_invalida", "la URL no es http ni https"),
    DUPLICATE("duplicado", "esa URL ya está en la cola"),
}

data class Admission(
    val accepted: Boolean,
    val reason: RejectReason,
) {
    val why: String get() = reason.explanation
}

/**
 * Contabilidad de la cola. Puras cuentas, sin efectos: esto es lo que se prueba.
 */
class QueueBudget(
    private val capacity: Int = QueueLimits.MAX_RESOURCES,
    private val maxTotalBytes: Long = QueueLimits.MAX_TOTAL_BYTES,
) {
    var count: Int = 0
        private set
    var totalBytes: Long = 0
        private set
    private val urls = HashSet<String>()

    fun admit(resource: DownloadResource): Admission {
        val scheme = resource.url.substringBefore(':', "").lowercase()
        if (scheme != "http" && scheme != "https") {
            return Admission(false, RejectReason.BAD_URL)
        }
        if (count >= capacity) {
            return Admission(false, RejectReason.QUEUE_FULL)
        }
        if (resource.bytesExpected > resource.kind.maxBytes) {
            return Admission(false, RejectReason.TOO_BIG)
        }
        if (totalBytes + resource.bytesExpected > maxTotalBytes) {
            return Admission(false, RejectReason.TOTAL_EXCEEDED)
        }
        if (!urls.add(resource.url)) {
            return Admission(false, RejectReason.DUPLICATE)
        }
        count++
        totalBytes += resource.bytesExpected
        return Admission(true, RejectReason.NONE)
    }

    fun remainingSlots(): Int = (capacity - count).coerceAtLeast(0)

    fun remainingBytes(): Long = (maxTotalBytes - totalBytes).coerceAtLeast(0)

    /** Fracción de la cola usada, para poder enseñarla. */
    fun usage(): Double = count.toDouble() / capacity
}

/**
 * Cuántas descargas se lanzan a la vez.
 *
 * Medido en cero-X: **7 descargas simultáneas degradaban el hit rate de prefetch
 * a cero**. Por eso el paralelismo total lo pone el perfil de rendimiento (1 o 2)
 * y este planificador añade un segundo freno por host, que es lo que evita que
 * dos archivos del mismo servidor se peleen.
 */
class ConcurrencyPlanner(
    private val globalLimit: Int = 1,
    private val perHostLimit: Int = QueueLimits.MAX_PER_HOST,
) {
    init {
        require(globalLimit >= 1) { "el paralelismo global no puede ser 0" }
        require(perHostLimit >= 1) { "el paralelismo por host no puede ser 0" }
    }

    private var active = 0
    private val activePerHost = HashMap<String, Int>()

    fun canStart(host: String): Boolean =
        active < globalLimit && (activePerHost[host] ?: 0) < perHostLimit

    fun start(host: String): Boolean {
        if (!canStart(host)) return false
        active++
        activePerHost[host] = (activePerHost[host] ?: 0) + 1
        return true
    }

    fun finish(host: String) {
        if (active > 0) active--
        val n = (activePerHost[host] ?: 0) - 1
        if (n <= 0) activePerHost.remove(host) else activePerHost[host] = n
    }

    fun activeCount(): Int = active
    fun activeFor(host: String): Int = activePerHost[host] ?: 0
}

/**
 * Política de reintentos con espera creciente.
 *
 * Se duplica el plazo en cada intento y se pone un techo: 1 s, 2 s, 4 s… hasta
 * un máximo. Sin techo, un archivo que falla 10 veces tendría una espera de
 * media hora antes de rendirse.
 */
object RetryPolicy {
    const val BASE_DELAY_MS = 1_000L
    const val MAX_DELAY_MS = 30_000L

    fun delayFor(attempt: Int): Long {
        if (attempt <= 0) return BASE_DELAY_MS
        // 2^attempt con la protección de desbordamiento antes de multiplicar.
        val shift = attempt.coerceAtMost(20)
        val raw = BASE_DELAY_MS shl shift
        return if (raw <= 0) MAX_DELAY_MS else minOf(raw, MAX_DELAY_MS)
    }

    fun shouldRetry(attempt: Int, maxAttempts: Int = QueueLimits.MAX_ATTEMPTS): Boolean =
        attempt < maxAttempts
}
