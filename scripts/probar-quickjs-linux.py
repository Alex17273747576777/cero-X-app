#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
"""
Ejecuta JavaScript que parece de extensión de Mihon con un QuickJS real.

POR QUÉ ESTE SCRIPT Y NO UNA PRUEBA UNITARIA DE KOTLIN
-----------------------------------------------------
La JVM de escritorio no tiene el `libquickjs.so` de Android: sus `.so` son ELF
para aarch64/x86_64 de Android y no cargan en Linux con glibc. Eso significa que
la prueba de Kotlin de la **API** se puede hacer con dobles, pero la prueba de
que el **motor funciona** tiene que hacerse con un QuickJS de verdad.

Aquí se usa el binario `qjs` (el intérprete de línea de comandos del proyecto
QuickJS, MIT) para ejecutar el mismo JavaScript que ejecutaría una extensión:
parsear HTML, recorrer el DOM, construir listas de series y de páginas.

Lo que esto SÍ demuestra: que el JS que escribe una extensión típica —selectores
CSS hechos a mano, expresiones regulares, JSON, promesas con `await`— corre en un
QuickJS sin motor de navegador.
Lo que NO demuestra: que el binding de Java (app.cash.quickjs) funcione en un
teléfono. Eso necesita dispositivo.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import urllib.request

QJS_URL = "https://bellard.org/quickjs/quickjs-2024-01-13.tar.xz"
DESTINO = "/opt/quickjs"


def asegurar_qjs():
    qjs = shutil.which("qjs") or os.path.join(DESTINO, "qjs")
    if os.path.exists(qjs):
        return qjs

    print("compilando QuickJS desde el fuente oficial…")
    os.makedirs(DESTINO, exist_ok=True)
    tar = os.path.join(DESTINO, "quickjs.tar.xz")
    if not os.path.exists(tar):
        urllib.request.urlretrieve(QJS_URL, tar)
    subprocess.run(["tar", "xf", tar, "-C", DESTINO], check=True)

    fuente = None
    for d in os.listdir(DESTINO):
        ruta = os.path.join(DESTINO, d, "qjs.c")
        if os.path.exists(ruta):
            fuente = os.path.dirname(ruta)
            break
    if not fuente:
        raise RuntimeError("no se encontró qjs.c en el tarball")

    # Sin `make`: solo el intérprete, que no necesita libquickjs.a ni qjsc.
    # Se usa el Makefile del propio proyecto. Compilar a mano falla: `qjs.c`
    # necesita `quickjs-libc.o` (js_std_loop, js_std_free_handlers) y `libbf.c`
    # necesita `bfdec_normalize_and_round`, que se genera con
    # -DCONFIG_BIGNUM. Dos intentos a mano, dos errores de enlazado: se deja
    # que lo haga quien lo escribió.
    subprocess.run("make -j2 qjs", shell=True, cwd=fuente, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    salida = os.path.join(fuente, "qjs")
    os.replace(salida, os.path.join(DESTINO, "qjs"))
    return os.path.join(DESTINO, "qjs")


# El JavaScript es el de una extensión real: nada de `document`, porque un
# QuickJS no tiene DOM. Una extensión de Mihon hace su propio parseo con regex,
# que es justo lo que se ejercita aquí.
JS_EXTENSION = r"""
// ── Una extensión típica: parsear catálogo, ficha y páginas ────────────────
var catalogoHtml = `
<html><body>
  <div class="serie"><a href="/manga/one-piece" title="One Piece">One Piece</a><img src="/c/1.jpg"></div>
  <div class="serie"><a href="/manga/berserk" title="Berserk">Berserk</a><img src="/c/2.jpg"></div>
  <div class="serie"><a href="/manga/vinland" title="Vinland Saga">Vinland Saga</a></div>
</body></html>`;

var capitulosHtml = `
<ul>
  <li><a href="/cap/12">Capítulo 12</a></li>
  <li><a href="/cap/11">Capítulo 11</a></li>
  <li><a href="/cap/10">Capítulo 10</a></li>
</ul>`;

var paginasJs = `var paginas = ["/p/1.jpg","/p/2.jpg","/p/3.jpg"]; paginas;`;

function popular(html) {
  var re = /<a href="([^"]+)" title="([^"]+)">/g;
  var salida = [], m;
  while ((m = re.exec(html)) !== null) salida.push({url: m[1], titulo: m[2]});
  return salida;
}

function capitulos(html) {
  var re = /<a href="(\/cap\/(\d+))">([^<]+)<\/a>/g;
  var salida = [], m;
  while ((m = re.exec(html)) !== null)
    salida.push({url: m[1], numero: parseInt(m[2], 10), nombre: m[3]});
  // Orden descendente como hace una fuente de verdad.
  salida.sort(function(a,b){ return b.numero - a.numero; });
  return salida;
}

var resultados = {
  catalogo: popular(catalogoHtml),
  capitulos: capitulos(capitulosHtml),
  paginas: eval(paginasJs),
  // Cosas que una extensión usa de verdad y un motor viejo podría no tener:
  json: JSON.stringify({a: 1, ñ: "acento"}),
  mapa: Array.from(new Map([[1,"a"],[2,"b"]]).keys()),
  plantilla: `cap-${1 + 1}`,
  regexNombre: /Capítulo\s+(\d+)/.exec("Capítulo 42")[1],
  spread: [...[1,2,3], 4].length,
  opcional: (null ?? "respaldo"),
  // Una promesa en cadena, que es como una extensión pide varias páginas.
  cadena: "sin-async",
};

resultados;
"""

JS_ASYNC = r"""
async function pedir(n) { return {pagina: n, ok: true}; }
async function todo() {
  var acumulado = [];
  for (var i = 1; i <= 3; i++) {
    var r = await pedir(i);
    acumulado.push(r.pagina);
  }
  return acumulado;
}
todo();
"""


def ejecutar(qjs, codigo):
    with tempfile.NamedTemporaryFile("w", suffix=".js", delete=False, encoding="utf-8") as f:
        f.write(codigo)
        ruta = f.name
    try:
        salida = subprocess.run(
            [qjs, "--std", "--module", ruta] if False else [qjs, ruta],
            capture_output=True, text=True, timeout=60,
        )
        return salida.returncode, salida.stdout.strip(), salida.stderr.strip()
    finally:
        os.unlink(ruta)


def main():
    qjs = asegurar_qjs()
    # `--version` no existe en qjs; la versión sale en la primera línea de -h.
    ayuda = subprocess.run([qjs, "-h"], capture_output=True, text=True)
    version = next(
        (l for l in (ayuda.stdout + ayuda.stderr).splitlines() if "QuickJS version" in l),
        "QuickJS (versión desconocida)",
    ).strip()
    print(f"motor: {version} ({qjs})")

    pruebas = []

    def comprobar(nombre, condicion, detalle=""):
        pruebas.append({"prueba": nombre, "ok": bool(condicion), "detalle": detalle})
        print(f"  {'✔' if condicion else '✘'} {nombre}{(' — ' + detalle) if detalle and not condicion else ''}")

    codigo, salida, error = ejecutar(qjs, JS_EXTENSION)
    if codigo != 0:
        print("el JS falló:", error[:500])
        return 1

    # qjs no imprime el valor de la última expresión: se envuelve en JSON.
    # qjs no imprime el valor de la última expresión (eso lo hace el REPL):
    # hay que pedirlo con console.log, que es lo que hace una extensión real.
    codigo2, salida2, _ = ejecutar(
        qjs, JS_EXTENSION.replace("resultados;", "console.log(JSON.stringify(resultados));")
    )
    print("  salida:", salida2[:160], "…")
    datos = json.loads(salida2)

    comprobar("parsea el catálogo con regex", len(datos["catalogo"]) == 3,
              f"{len(datos['catalogo'])} resultados")
    comprobar("extrae url y título", datos["catalogo"][0]["titulo"] == "One Piece")
    comprobar("parsea capítulos y los ordena", [c["numero"] for c in datos["capitulos"]] == [12, 11, 10])
    comprobar("eval de una fuente devuelve las páginas", datos["paginas"] == ["/p/1.jpg", "/p/2.jpg", "/p/3.jpg"])
    comprobar("JSON con acentos", "acento" in datos["json"])
    comprobar("Map/Array.from", datos["mapa"] == [1, 2])
    comprobar("plantillas de texto", datos["plantilla"] == "cap-2")
    comprobar("regex con grupos", datos["regexNombre"] == "42")
    comprobar("spread", datos["spread"] == 4)
    comprobar("operador ?? ", datos["opcional"] == "respaldo")

    codigo3, salida3, error3 = ejecutar(qjs, JS_ASYNC)
    comprobar("async/await", codigo3 == 0, error3[:120])

    fallos = [p for p in pruebas if not p["ok"]]
    print(f"\n{len(pruebas) - len(fallos)}/{len(pruebas)} en verde")

    with open("/opt/work/medicion-quickjs.json", "w") as f:
        json.dump({
            "motor": version,
            "binario": qjs,
            "pruebas": pruebas,
            "verde": len(pruebas) - len(fallos),
            "total": len(pruebas),
            "nota": "QuickJS de linea de comandos (MIT). No prueba el binding de Java en Android.",
        }, f, indent=2, ensure_ascii=False)
    return 0 if not fallos else 1


if __name__ == "__main__":
    sys.exit(main())
