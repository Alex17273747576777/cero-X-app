package com.cerox.comun

/**
 * Lo que el anfitrión le da a una extensión antes de evaluarla.
 *
 * EL FALLO QUE ESTO ARREGLA
 * -------------------------
 * El sobre termina con `})(ceroX, __http, __dom, __prefs, __log)`. En Android esos
 * cinco objetos los pone la app (`SourceJsApi`), pero en el escritorio y en la
 * web nadie los ponía, y el resultado era:
 *
 *     qjs falló: ReferenceError: 'ceroX' is not defined
 *
 * Eso no se veía compilando ni con pruebas de texto: sólo ejecutando una
 * extensión de verdad. Por eso esto existe, y por eso la prueba que lo cubre
 * **ejecuta `qjs`** y falla si el binario no está.
 *
 * QUÉ SE LE DA Y QUÉ NO
 * ---------------------
 *   · `log`    → escribe en la salida estándar del proceso. Inofensivo.
 *   · `prefs`  → un mapa en memoria. Sin `Context`, sin archivos, sin disco.
 *   · `ceroX`  → la versión y el idioma. Datos, no capacidades.
 *   · `dom`    → un parser mínimo de atributos con expresiones regulares.
 *   · `http`   → **no**. Lanza con un motivo claro.
 *
 * Lo de `http` es deliberado: en este módulo no hay cliente HTTP porque la red
 * la pone cada anfitrión (OkHttp en Android, `fetch` en la web, `HttpClient` en
 * escritorio). Darle a una extensión un cliente sin tope de peticiones, sin
 * límite de respuesta y sin lista de hosts sería exactamente lo contrario de lo
 * que dice `SourceJsLimits` en la app. Una extensión que hoy intente descargar
 * aquí recibe un error explícito en vez de un fallo raro.
 */
object AnfitrionJs {

    /** El texto que se antepone al sobre. Termina en `;` para poder concatenar. */
    fun preludio(): String = """
        var ceroX = { version: "1.0.0", plataforma: "${plataformaReal()}" };
        var __log = function (m) { console.log(String(m)); };
        var __prefs = (function () {
          var datos = {};
          return {
            get: function (k, porDefecto) { return (k in datos) ? datos[k] : porDefecto; },
            set: function (k, v) { datos[k] = v; },
            borrar: function (k) { delete datos[k]; }
          };
        })();
        var __dom = {
          // Sin DOM de verdad no se puede parsear HTML entero: se da lo que una
          // extensión usa de verdad, que son atributos y textos.
          atributos: function (html) {
            var re = /([a-zA-Z-]+)="([^"]*)"/g, m, salida = {};
            while ((m = re.exec(html)) !== null) { salida[m[1]] = m[2]; }
            return salida;
          },
          texto: function (html) { return html.replace(/<[^>]*>/g, "").trim(); }
        };
        var __http = function () {
          throw new Error("http no está disponible en este anfitrión: la red la pone la app");
        };
    """.trimIndent() + "\n"

    /** El script completo: anfitrión + sobre + la extensión. */
    fun scriptCompleto(fuenteDeLaExtension: String): String =
        preludio() + SobreDeExtension.construir(fuenteDeLaExtension) + "\n"

    /**
     * Lo mismo, pero guardando en `SALIDA` lo que devolvió el sobre.
     *
     * El orden importa: el preludio declara `ceroX`, y el sobre **usa** `ceroX`.
     * Antes de esto se construía `"var SALIDA = " + scriptCompleto(...)`, que
     * producía `var SALIDA = var ceroX = …`: un `SyntaxError` que no tiene nada
     * que ver con lo que se estaba probando.
     */
    fun scriptConSalida(fuenteDeLaExtension: String): String =
        preludio() + "var SALIDA = " + SobreDeExtension.construir(fuenteDeLaExtension) + ";\n"
}
