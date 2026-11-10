# Firma de publicaciones — contención del 2026-11-10

La release `apk-idiomas-2026-11-09` se convirtió a borrador, preservando su APK, porque su nota reconoce el uso del certificado de depuración `726314D8…` cuya clave privada era pública en `android/debug.keystore`. No se borró el tag ni el historial.

Se eliminó la clave del árbol actual y su configuración explícita. Esto NO revoca la clave, NO elimina las copias históricas y NO protege un APK ya instalado con ella. Nunca se debe publicar un APK de depuración como actualización de producción.

La clave de publicación declarada para `v1.0.0-beta` tiene huella `f03505ce861af5ddbf1a2c31fe308a5d7ae822c40689cf5893a8263b81232fb3`. No hay secretos de Actions configurados en este repositorio a la fecha de revisión. No se generó una clave sustitutiva ni se prometió compatibilidad de actualización con otra firma.

Pendiente: recuperar la clave privada de publicación desde su custodio, instalarla como secreto en un entorno remoto controlado y reconstruir/verificar el APK. Sin esa clave no puede producirse una actualización compatible con ese certificado. Para usuarios de la compilación comprometida hace falta un plan explícito de migración y respaldo, no pedir que desinstalen a ciegas.
