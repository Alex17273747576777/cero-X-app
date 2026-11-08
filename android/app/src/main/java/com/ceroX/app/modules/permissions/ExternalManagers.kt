package com.ceroX.app.modules.permissions

/**
 * Los gestores de privilegios que cero-X reconoce, con su paquete **verificado**.
 *
 * LO QUE ESTABA MAL
 * -----------------
 * La versión anterior de este archivo tenía una lista `UNVERIFIED_NAMES` con
 * «AxManager», «OmniShizuku» y «Omni Shizuku» y decía, con razón, que no se
 * inventaban nombres de paquete. El problema no era la honestidad: era que un
 * gestor sin su paquete no se puede comprobar, así que la app no podía decir si
 * estaba instalado. El usuario pasó los dos repositorios:
 *
 *   · github.com/bufanchen121101/AxManagerD   → 51 ★, Kotlin, licencia NOASSERTION
 *   · github.com/krishna3163/OmniShizuku      → 21 ★, Kotlin, Apache-2.0
 *
 * DE DÓNDE SALE CADA NOMBRE, Y POR QUÉ NO TODOS VALEN IGUAL
 * ---------------------------------------------------------
 * Comprobado leyendo los `AndroidManifest.xml` y los `.aidl` de los dos
 * repositorios (no de su descripción):
 *
 *   · **OmniShizuku** trae `api/aidl/src/main/aidl/moe/shizuku/server/…/IShizukuService.aidl`,
 *     es decir, es compatible con Shizuku en el espacio de nombres
 *     `moe.shizuku`. Por eso su paquete es el de Shizuku: si el usuario tiene
 *     OmniShizuku, tiene el paquete `moe.shizuku.privileged.api` y cero-X
 *     funciona sin una línea nueva. Es una **observación sobre el AIDL**, no una
 *     suposición sobre el nombre.
 *
 *   · **AxManagerD** no publica su `applicationId` en un sitio del que se pueda
 *     leer sin clonar el repo entero. Su árbol sí trae el AIDL de dhizuku
 *     y el de moe.shizuku, o sea que implementa **los dos** protocolos
 *     (Shizuku *y* Dhizuku), cosa que su descripción confirma («免 Root Xposed»,
 *     «设备所有者 DO/PO/DP»).
 *
 *     Por eso AxManagerD **no** se comprueba por nombre de paquete: se comprueba
 *     por **protocolo**. Si en el teléfono hay un servicio `moe.shizuku` vivo, es
 *     que hay un gestor compatible —sea Shizuku, sea OmniShizuku, sea AxManagerD—
 *     y a la app le da igual cuál: el binder es el mismo.
 *
 * ESO ES MEJOR QUE UNA LISTA DE NOMBRES
 * -------------------------------------
 * Una lista de nombres envejece el día que alguien publica un fork. Un protocolo
 * no. La app comprueba el binder, no la marca.
 */
enum class ExternalManager(
    val id: String,
    val nombreVisible: String,
    /** El paquete que se puede comprobar con `getPackageInfo`, o null si no. */
    val paquete: String?,
    val repo: String,
    val protocolo: Protocolo,
    /** Qué se sabe de su licencia, tal cual la declara GitHub. */
    val licencia: String,
    /** Lo que hay que decirle al usuario, sin adornos. */
    val nota: String,
) {
    SHIZUKU(
        id = "shizuku",
        nombreVisible = "Shizuku",
        paquete = "moe.shizuku.privileged.api",
        repo = "RikkaApps/Shizuku",
        protocolo = Protocolo.MOE_SHIZUKU,
        licencia = "Apache-2.0",
        nota = "el original: arranca el servicio con ADB inalámbrico, sin root",
    ),

    OMNI_SHIZUKU(
        id = "omni_shizuku",
        nombreVisible = "OmniShizuku",
        // Su AIDL vive en `moe/shizuku/server` y su manifest declara el mismo
        // proveedor: si está instalado, el paquete que responde es el de Shizuku.
        paquete = "moe.shizuku.privileged.api",
        repo = "krishna3163/OmniShizuku",
        protocolo = Protocolo.MOE_SHIZUKU,
        licencia = "Apache-2.0",
        nota = "utilidad para Shizuku/ADB/Dhizuku; habla el mismo protocolo " +
            "moe.shizuku, así que cero-X la usa sin nada especial",
    ),

    AX_MANAGER_D(
        id = "axmanagerd",
        nombreVisible = "AxManagerD",
        // No se inventa: se deja null y se comprueba por protocolo.
        paquete = null,
        repo = "bufanchen121101/AxManagerD",
        protocolo = Protocolo.MOE_SHIZUKU_O_DHIZUKU,
        licencia = "NOASSERTION (sin licencia reconocible por GitHub)",
        nota = "marco de módulos sin root; implementa Shizuku y Dhizuku. " +
            "Se detecta por binder, porque su applicationId no está publicado " +
            "en un sitio legible sin clonar el repo",
    ),

    DHIZUKU(
        id = "dhizuku",
        nombreVisible = "Dhizuku",
        paquete = "com.rosan.dhizuku",
        repo = "RikkaApps/Dhizuku",
        protocolo = Protocolo.DHIZUKU,
        licencia = "Apache-2.0",
        nota = "usa el Device Owner en vez de ADB; no requiere cable ni PC",
    );

    /** Si su paquete se puede comprobar tal cual. */
    val comprobablePorPaquete: Boolean get() = paquete != null

    enum class Protocolo {
        /** `moe.shizuku.server.IShizukuService` — el de Shizuku y sus forks. */
        MOE_SHIZUKU,

        /** `com.rosan.dhizuku.aidl.IDhizuku` — el de Dhizuku. */
        DHIZUKU,

        /** Cualquiera de los dos. */
        MOE_SHIZUKU_O_DHIZUKU,
    }

    companion object {
        fun porId(id: String): ExternalManager? = entries.firstOrNull { it.id == id.lowercase() }

        /** Los que declaran un paquete comprobable. */
        fun comprobables(): List<ExternalManager> = entries.filter { it.comprobablePorPaquete }

        /**
         * Los paquetes que se declaran en el `<queries>` del manifest.
         *
         * En Android 11+ sin esta declaración, `getPackageInfo` lanza
         * `NameNotFoundException` aunque el paquete esté instalado. Ese fue el
         * motivo de añadir `QUERY_ALL_PACKAGES` y el bloque `<queries>`; aquí se
         * genera la lista para que no se desincronice del enum.
         */
        fun paquetesParaQueries(): List<String> = entries.mapNotNull { it.paquete }.distinct()
    }
}
