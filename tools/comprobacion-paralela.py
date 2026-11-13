#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
"""Comprobaciones de calidad del repo, en paralelo y con veredicto por separado.

Para qué existe
---------------
Las comprobaciones baratas del repo (sintaxis, YAML, JSON, cabeceras, numeración
de reglas, secretos) se hacían en serie y a mano, cada una en su turno, y varias
no se hacían: el SPDX se declaró «al 100 %» el 2026-11-13 cuando cubría 4
ficheros de 20, y nadie lo habría notado hasta leerlos uno a uno. Aquí corren
todas a la vez y cada una da su propio veredicto, así que el coste de comprobar
más cosas deja de ser tiempo de espera.

Qué comprueba
-------------
entorno           tools/comprobar_entorno.py: presupuesto de workspace, JDK, SDK,
                  y las cachés que no deben vivir dentro (~/.gradle, ~/.m2)
latido            tools/latido.py: árbol de Git, evidencia citada, fechas de corte,
                  pendientes con criterio de salida y remoto de respaldo
sintaxis-python   compila en memoria cada .py versionado; no escribe .pyc, que
                  dentro del workspace comería presupuesto de snapshot
sintaxis-shell    bash -n sobre cada .sh versionado
yaml-workflows    los workflows parsean y declaran disparador y algún job
json-datos        cada .json versionado parsea
spdx              todo fichero versionado de tools/ y scripts/ lleva
                  SPDX-License-Identifier, y lista los identificadores distintos
reglas            AGENTS.md numera sin saltos ni duplicados, y toda cita
                  «regla N» del repo apunta a una regla que existe
sin-secretos      ningún fichero versionado contiene un token con forma de GitHub

Qué NO comprueba
----------------
· No compila Kotlin ni ejecuta pruebas: eso es Gradle, cuesta minutos y tiene su
  propio banco (tools/medir-build.py). Mezclarlo aquí convertiría una
  comprobación de segundos en una de cuatro minutos.
· No duplica al latido: lo invoca. El latido es quien mira el estado (árbol,
  evidencia, fechas, remoto); esto mira la forma de los ficheros.
· No hace llamadas de red, así que no puede fallar por un corte.
· `sin-secretos` busca formas conocidas de token. Que no encuentre ninguna no
  demuestra que no haya secretos: demuestra que no hay secretos con esa forma.

Salidas
-------
0  todo OK
1  alguna comprobación FALLA
2  no se ejecutó NINGUNA: un comprobador que pasa por no tener nada que comprobar
   es el mismo cero silencioso que las fallas C1-C7 del registro de edt
3  con --estricto, alguna quedó OMITIDA (falta la herramienta o el fichero que
   necesita). Sin la bandera se informa y se sale 0, pero diciéndolo.

Uso
---
    python3 tools/comprobacion-paralela.py                 # todas
    python3 tools/comprobacion-paralela.py --listar
    python3 tools/comprobacion-paralela.py --solo spdx --solo reglas
    python3 tools/comprobacion-paralela.py --estricto --jobs 4
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OK, FALLA, OMITIDA = "OK", "FALLA", "OMITIDA"


def versionados(*patrones: str) -> list[Path]:
    """Ficheros bajo control de versiones: lo que no está versionado no es el repo."""
    salida: list[Path] = []
    for patron in patrones:
        crudo = subprocess.run(["git", "ls-files", "-z", patron], cwd=ROOT,
                               capture_output=True, text=True, check=False).stdout
        salida += [ROOT / p for p in crudo.split("\0") if p]
    return sorted(set(salida))


def leer(ruta: Path) -> str:
    return ruta.read_text(encoding="utf-8", errors="replace")


def de_script(nombre: str, *argumentos: str) -> tuple[str, str, str]:
    """Ejecuta una herramienta del repo y devuelve (estado, resumen, detalle)."""
    ruta = ROOT / "tools" / nombre
    if not ruta.is_file():
        return OMITIDA, f"falta tools/{nombre}", ""
    proceso = subprocess.run([sys.executable, str(ruta), *argumentos], cwd=ROOT,
                             capture_output=True, text=True, check=False)
    detalle = (proceso.stdout + proceso.stderr).strip()
    lineas = [l for l in detalle.splitlines() if l.strip()]
    resumen = lineas[-1] if lineas else "sin salida"
    return (OK if proceso.returncode == 0 else FALLA), resumen, detalle


def comprobar_sintaxis_python() -> tuple[str, str, str]:
    """compila en memoria cada .py versionado, sin escribir .pyc."""
    ficheros = versionados("*.py")
    if not ficheros:
        return OMITIDA, "ningún .py versionado", ""
    malos = []
    for f in ficheros:
        try:
            # compile() en memoria: py_compile escribiría __pycache__ dentro del
            # workspace, y el presupuesto de snapshot es de 110 MiB.
            compile(leer(f), str(f.relative_to(ROOT)), "exec")
        except SyntaxError as e:
            malos.append(f"{f.relative_to(ROOT)}:{e.lineno}: {e.msg}")
    if malos:
        return FALLA, f"{len(malos)} de {len(ficheros)} .py no compilan", "\n".join(malos)
    return OK, f"{len(ficheros)} .py compilan", ""


def comprobar_sintaxis_shell() -> tuple[str, str, str]:
    """bash -n sobre cada .sh versionado."""
    ficheros = versionados("*.sh")
    if not ficheros:
        return OMITIDA, "ningún .sh versionado", ""
    malos = []
    for f in ficheros:
        proceso = subprocess.run(["bash", "-n", str(f)], cwd=ROOT, capture_output=True, text=True, check=False)
        if proceso.returncode != 0:
            malos.append(f"{f.relative_to(ROOT)}: {proceso.stderr.strip().splitlines()[-1] if proceso.stderr else '?'}")
    if malos:
        return FALLA, f"{len(malos)} de {len(ficheros)} .sh con error de sintaxis", "\n".join(malos)
    return OK, f"{len(ficheros)} .sh pasan bash -n", ""


def comprobar_yaml_workflows() -> tuple[str, str, str]:
    """los workflows parsean y declaran disparador y algún job."""
    try:
        import yaml
    except ImportError:
        return OMITIDA, "PyYAML no está instalado; los workflows no se validaron", ""
    ficheros = sorted((ROOT / ".github/workflows").glob("*.yml")) if (ROOT / ".github/workflows").is_dir() else []
    if not ficheros:
        return OMITIDA, "no hay .github/workflows/*.yml", ""
    problemas = []
    for f in ficheros:
        try:
            doc = yaml.safe_load(leer(f))
        except yaml.YAMLError as e:
            problemas.append(f"{f.name}: no parsea ({str(e).splitlines()[0][:80]})")
            continue
        if not isinstance(doc, dict):
            problemas.append(f"{f.name}: la raíz no es un mapa")
            continue
        # PyYAML convierte la clave `on:` en True: es YAML 1.1, no un workflow roto.
        disparadores = doc.get("on", doc.get(True))
        if not disparadores:
            problemas.append(f"{f.name}: sin disparadores (`on:`)")
        if not doc.get("jobs"):
            problemas.append(f"{f.name}: sin `jobs:`")
    if problemas:
        return FALLA, f"{len(problemas)} problema(s) en {len(ficheros)} workflows", "\n".join(problemas)
    return OK, f"{len(ficheros)} workflows parsean, con disparador y jobs", ""


def comprobar_json() -> tuple[str, str, str]:
    """cada .json versionado parsea."""
    ficheros = versionados("*.json")
    if not ficheros:
        return OMITIDA, "ningún .json versionado", ""
    malos = []
    for f in ficheros:
        try:
            json.loads(leer(f))
        except json.JSONDecodeError as e:
            malos.append(f"{f.relative_to(ROOT)}:{e.lineno}: {e.msg}")
    if malos:
        return FALLA, f"{len(malos)} de {len(ficheros)} .json no parsean", "\n".join(malos)
    return OK, f"{len(ficheros)} .json parsean", ""


def comprobar_spdx() -> tuple[str, str, str]:
    """tools/ y scripts/ llevan SPDX-License-Identifier; lista los identificadores."""
    ficheros = versionados("tools/*", "scripts/*")
    if not ficheros:
        return OMITIDA, "no hay ficheros versionados en tools/ ni scripts/", ""
    sin_cabecera, identificadores = [], {}
    for f in ficheros:
        texto = leer(f)
        # El prefijo de comentario es obligatorio de admitir: en .py y .sh la línea es
        # «# SPDX-…» y en .gradle «// SPDX-…». Sin él, la comprobación decía «20 de 20
        # ficheros sin SPDX» sobre ficheros que lo llevan todos —un verificador que
        # miente, que es exactamente lo que esta herramienta existe para cazar.
        m = re.search(r"^[#/*;\-]*\s*SPDX-License-Identifier:\s*(\S+)", texto, re.M)
        if not m:
            sin_cabecera.append(str(f.relative_to(ROOT)))
        else:
            identificadores[m.group(1)] = identificadores.get(m.group(1), 0) + 1
    resumen_ids = ", ".join(f"{k}×{v}" for k, v in sorted(identificadores.items()))
    if sin_cabecera:
        return (FALLA,
                f"{len(sin_cabecera)} de {len(ficheros)} ficheros sin SPDX-License-Identifier ({resumen_ids})",
                "\n".join(sin_cabecera))
    return OK, f"{len(ficheros)} ficheros con SPDX ({resumen_ids})", ""


def comprobar_reglas() -> tuple[str, str, str]:
    """AGENTS.md numera sin saltos y toda cita «regla N» existe."""
    agents = ROOT / "AGENTS.md"
    if not agents.is_file():
        return OMITIDA, "no hay AGENTS.md", ""
    texto = leer(agents)
    numeros = [int(n) for n in re.findall(r"^(\d+)\. \*\*", texto, re.M)]
    if not numeros:
        return FALLA, "AGENTS.md no tiene reglas numeradas", ""
    problemas = []
    esperados = list(range(1, len(numeros) + 1))
    faltan = [n for n in esperados if n not in numeros]
    duplicadas = sorted({n for n in numeros if numeros.count(n) > 1})
    if faltan:
        problemas.append(f"faltan las reglas {faltan}")
    if duplicadas:
        problemas.append(f"numeración duplicada: {duplicadas}")
    if numeros != sorted(numeros):
        problemas.append("la numeración no está en orden")
    # Toda cita «regla N» del repo tiene que apuntar a una regla que exista. Una cita
    # colgando es lo que pasa al renumerar sin mirar quién cita: el 2026-11-13 se
    # midió antes de mover las reglas 16-19 y ninguna estaba citada.
    maximo = max(numeros) if numeros else 0
    citas = subprocess.run(["git", "grep", "-noIE", r"[Rr]eglas? [0-9]+", "--", ".", ":!work"],
                           cwd=ROOT, capture_output=True, text=True, check=False).stdout
    colgadas = []
    for linea in citas.splitlines():
        try:
            fichero, num_linea, encontrado = linea.split(":", 2)
        except ValueError:
            continue
        for n in re.findall(r"[0-9]+", encontrado):
            if int(n) > maximo:
                colgadas.append(f"{fichero}:{num_linea} cita «regla {n}» y sólo hay {maximo}")
    if colgadas:
        problemas += sorted(set(colgadas))
    if problemas:
        return FALLA, f"{len(problemas)} problema(s) en {len(numeros)} reglas", "\n".join(problemas)
    citadas = len({n for n in re.findall(r"[Rr]egla ([0-9]+)", citas) })
    return OK, f"{len(numeros)} reglas numeradas 1..{maximo} sin saltos; {citadas} números citados en el repo, todos existen", ""


def comprobar_sin_secretos() -> tuple[str, str, str]:
    """ningún fichero versionado tiene forma de token o clave privada."""
    patrones = [r"ghp_[A-Za-z0-9]{20,}", r"github_pat_[A-Za-z0-9_]{20,}",
                r"gho_[A-Za-z0-9]{20,}", r"x-access-token:[A-Za-z0-9_]{10,}",
                r"-----BEGIN [A-Z ]*PRIVATE KEY-----"]
    proceso = subprocess.run(
        ["git", "grep", "-noIE", "|".join(patrones), "--", ".", ":!work"],
        cwd=ROOT, capture_output=True, text=True, check=False)
    hallazgos = [l for l in proceso.stdout.splitlines() if l.strip()]
    if hallazgos:
        # No se imprime el secreto: sólo dónde está. Imprimirlo lo copiaría a cualquier
        # registro de CI, que es exactamente la fuga que la regla de tokens prohíbe.
        sitios = sorted({l.split(":", 2)[0] + ":" + l.split(":", 2)[1] for l in hallazgos if l.count(":") >= 2})
        return FALLA, f"{len(hallazgos)} coincidencia(s) con forma de secreto en {len(sitios)} sitio(s)", "\n".join(sitios)
    return OK, f"0 coincidencias con {len(patrones)} formas de secreto (no demuestra ausencia de secretos, sólo de estas formas)", ""


def comprobar_entorno() -> tuple[str, str, str]:
    """presupuesto de workspace, JDK, SDK y las cachés que no deben vivir dentro."""
    return de_script("comprobar_entorno.py")


def comprobar_latido() -> tuple[str, str, str]:
    """árbol de Git, evidencia citada, fechas de corte, pendientes y remoto de respaldo."""
    return de_script("latido.py")


COMPROBACIONES = {
    "entorno": comprobar_entorno,
    "latido": comprobar_latido,
    "sintaxis-python": comprobar_sintaxis_python,
    "sintaxis-shell": comprobar_sintaxis_shell,
    "yaml-workflows": comprobar_yaml_workflows,
    "json-datos": comprobar_json,
    "spdx": comprobar_spdx,
    "reglas": comprobar_reglas,
    "sin-secretos": comprobar_sin_secretos,
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--solo", action="append", default=[], metavar="NOMBRE",
                        help="ejecutar sólo esta comprobación (repetible)")
    parser.add_argument("--jobs", type=int, default=0, help="hilos; 0 = tantos como CPUs")
    parser.add_argument("--estricto", action="store_true",
                        help="una comprobación OMITIDA cuenta como fallo (sale 3)")
    parser.add_argument("--listar", action="store_true", help="lista las comprobaciones y sale")
    parser.add_argument("--detallado", action="store_true", help="imprime el detalle de cada fallo")
    args = parser.parse_args()

    if args.listar:
        for nombre, funcion in COMPROBACIONES.items():
            primera = (funcion.__doc__ or "").strip().splitlines()
            print(f"  {nombre:<16} {primera[0] if primera else ''}")
        return 0

    seleccion = args.solo or list(COMPROBACIONES)
    desconocidas = [s for s in seleccion if s not in COMPROBACIONES]
    if desconocidas:
        print(f"ERROR comprobaciones inexistentes: {desconocidas}. Disponibles: {list(COMPROBACIONES)}", file=sys.stderr)
        return 2
    jobs = args.jobs or max(2, os.cpu_count() or 2)
    if not seleccion:
        print("COMPROBACION_NINGUNA: no se ejecutó ninguna comprobación; eso no es un éxito", file=sys.stderr)
        return 2

    inicio = time.time()
    def ejecutar(nombre: str) -> tuple[str, str, str, float]:
        # El cronómetro va dentro del hilo: fuera se empieza a contar cuando el futuro ya
        # terminó, y la primera versión imprimía 0,0 s por comprobación y una comparación
        # «en serie habrían costado 0,0 s» que no significaba nada.
        t0 = time.time()
        try:
            estado, resumen, detalle = COMPROBACIONES[nombre]()
        except Exception as e:  # una comprobación que revienta no puede contar como OK
            estado, resumen, detalle = FALLA, f"la comprobación reventó: {type(e).__name__}: {e}", ""
        return estado, resumen, detalle, round(time.time() - t0, 2)

    resultados: dict[str, tuple[str, str, str, float]] = {}
    with cf.ThreadPoolExecutor(max_workers=jobs) as executor:
        futuros = {executor.submit(ejecutar, nombre): nombre for nombre in seleccion}
        for futuro in cf.as_completed(futuros):
            resultados[futuros[futuro]] = futuro.result()

    ancho = max(len(n) for n in resultados)
    for nombre in seleccion:
        estado, resumen, detalle, _ = resultados[nombre]
        print(f"  {estado:<7} {nombre:<{ancho}} {resultados[nombre][3]:>5.2f} s  {resumen[:140]}")
        if args.detallado and detalle and estado != OK:
            for linea in detalle.splitlines()[:12]:
                print(f"            {linea[:160]}")

    fallas = [n for n, r in resultados.items() if r[0] == FALLA]
    omitidas = [n for n, r in resultados.items() if r[0] == OMITIDA]
    total = time.time() - inicio
    print(f"  {len(resultados)} comprobaciones en {total:.1f} s con {jobs} hilos "
          f"(en serie habrían costado {sum(r[3] for r in resultados.values()):.1f} s)")
    if fallas:
        print(f"COMPROBACION_FALLIDA: {len(fallas)} → {', '.join(sorted(fallas))}")
        return 1
    if omitidas:
        if args.estricto:
            print(f"COMPROBACION_INCOMPLETA: {len(omitidas)} omitida(s) → {', '.join(sorted(omitidas))}")
            print("  En modo estricto eso sale 3: un verde que no dice qué no comprobó es un verde hueco.")
            return 3
        print(f"COMPROBACION_PARCIAL: OK en {len(resultados) - len(omitidas)}, omitida(s) {', '.join(sorted(omitidas))}")
        print("  AVISO, no error: sin --estricto se sale 0 diciéndolo. Con --estricto esto sale 3.")
        return 0
    print(f"COMPROBACION_OK: {len(resultados)} comprobaciones en verde")
    return 0


if __name__ == "__main__":
    sys.exit(main())
