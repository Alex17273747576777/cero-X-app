# Seguridad de extensiones — 2026-11-10

La validación anterior calculaba un SHA-256 y devolvía éxito sin comprobar firma ni confianza. Se sustituyó por `ApkVerifier` oficial y una lista explícita de huellas SHA-256 de certificados aprobados. No se usa TOFU ni se aceptan certificados obtenidos del propio APK como si fueran de confianza.

**Estado actual:** no hay un catálogo autenticado de certificados aprobados ni una interfaz de consentimiento. Por eso los constructores usan una lista vacía y la instalación desde el módulo y la carga dinámica se rechazan por defecto. Esto es contención, no una integración de extensiones terminada. No se inventaron huellas para desbloquearla.

El cargador verifica una copia privada de sólo lectura antes de ejecutar código; la inspección del contrato usa ZIP sin ejecutar DEX. Un ClassLoader separa nombres, no permisos: una extensión aprobada conserva los permisos de la aplicación. Un aislamiento de seguridad real necesita otro proceso/UID y un protocolo limitado, aún pendiente.

Falta una prueba positiva con un APK de extensión auténtico de un proveedor aprobado, más pruebas en dispositivos Android 8 y 14+. Las pruebas añadidas cubren rechazo por ausencia de confianza y por APK corrupto; no certifican compatibilidad con extensiones reales.
