#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
"""Banco de medición de builds: tiempo, memoria y resultado, en una sola corrida.

Para qué existe
---------------
Decidir si el paralelismo ayuda exige medir, y medir un build de Gradle a mano
invita a dos errores que este proyecto ya cometió: quedarse con la etiqueta
`BUILD SUCCESSFUL` sin contar las pruebas (regla 9), y comparar variantes que no
partieron del mismo estado (regla 12: los hashes de APK sólo son comparables
entre builds del mismo modo).

Qué mide
--------
· tiempo de pared de la orden completa, en segundos
· código de salida real, sin pipe que se lo coma (falla C1 del registro edt)
· pico de RSS **sumado** de todos los procesos java vivos durante la corrida:
  demonio de Gradle, workers de pruebas, demonio de Kotlin. Es la cifra que
  decide si una variante cabe en esta máquina, no el -Xmx configurado
· mínimo de MemAvailable del sistema: cuánto margen llegó a quedar
· si la orden produjo pruebas, el recuento desde los XML (suites, pruebas,
  fallos, errores, omitidas) y la edad del XML más viejo, para distinguir una
  corrida real de un UP-TO-DATE con resultados de la anterior (regla 18)
· si produjo APK, tamaño y sha256: dos variantes que dan el mismo hash son la
  prueba de que el paralelismo no cambió el producto

Qué NO mide
-----------
· No mide el tiempo de descarga de dependencias. La primera corrida tras un
  reinicio del sandbox baja Gradle y los artefactos desde cero, así que una
  variante medida en frío no es comparable con una medida en caliente. El
  resultado guarda `opt_cerox_mib` al empezar para que se vea cuál fue cuál.
· No atribuye memoria a un proceso concreto: suma RSS, que además cuenta
  páginas compartidas más de una vez. Sirve para comparar variantes entre sí,
  no como cifra absoluta de consumo.
· No decide nada solo: imprime una fila y la añade al histórico. Quien compara
  es quien lee, y dos filas sólo son comparables si `variante_base` coincide.

Uso
---
    python3 tools/medir-build.py NOMBRE -- TAREAS...
    python3 tools/medir-build.py cache --prop org.gradle.caching=true -- clean :app:assembleDebug

`--prop k=v` se pasa como `-Pk=v` y además por `-D` cuando la clave empieza por
`org.gradle.` o `kotlin.`, que es como Gradle las lee de verdad. Con `--historico
RUTA` se indica dónde añadir la fila (por defecto work/ci/paralelismo.tsv).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android"
RESULTADOS = ANDROID / "app/build/test-results/testDebugUnitTest"
APKS = {
    "app": ANDROID / "app/build/outputs/apk/debug/app-debug.apk",
    "androidTest": ANDROID / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
}


def meminfo_mb() -> float:
    """MemAvailable en MB; la que decide si el OOM killer va a intervenir."""
    for linea in Path("/proc/meminfo").read_text().splitlines():
        if linea.startswith("MemAvailable:"):
            return int(linea.split()[1]) / 1024
    return 0.0


def rss_java_mb() -> float:
    """RSS sumado de todos los procesos java vivos, en MB."""
    total = 0
    for pid in (p for p in Path("/proc").iterdir() if p.name.isdigit()):
        try:
            if "java" not in (pid / "comm").read_text():
                continue
            for campo in (pid / "status").read_text().splitlines():
                if campo.startswith("VmRSS:"):
                    total += int(campo.split()[1])
        except OSError:
            continue  # el proceso terminó entre leer el directorio y leer su estado
    return total / 1024


def du_mb(ruta: Path) -> float:
    if not ruta.exists():
        return 0.0
    return sum(f.stat().st_size for f in ruta.rglob("*") if f.is_file()) / 1024 / 1024


def contar_pruebas() -> dict:
    """Recuento desde los XML, con la edad del más viejo para cazar UP-TO-DATE."""
    xmls = sorted(RESULTADOS.glob("TEST-*.xml"))
    if not xmls:
        return {"suites": 0}
    ahora = time.time()
    suites = pruebas = fallos = errores = omitidas = 0
    for archivo in xmls:
        raiz = ET.parse(archivo).getroot()
        suites += 1
        pruebas += int(raiz.get("tests", 0))
        fallos += int(raiz.get("failures", 0))
        errores += int(raiz.get("errors", 0))
        omitidas += int(raiz.get("skipped", 0))
    return {
        "suites": suites, "pruebas": pruebas, "fallos": fallos,
        "errores": errores, "omitidas": omitidas,
        "xml_mas_viejo_s": round(max(ahora - f.stat().st_mtime for f in xmls)),
    }


def medir_apks() -> dict:
    salida = {}
    for nombre, ruta in APKS.items():
        if ruta.is_file():
            datos = ruta.read_bytes()
            salida[nombre] = {"bytes": len(datos), "sha256": hashlib.sha256(datos).hexdigest()}
    return salida


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("nombre", help="nombre de la variante, p. ej. base, cache, forks2")
    parser.add_argument("--prop", action="append", default=[], metavar="K=V",
                        help="propiedad Gradle de la variante; se aplica como -P y, si es org.gradle.*/kotlin.*, también como -D")
    parser.add_argument("--gradle-args", default="-I ../tools/low-memory.gradle --console=plain",
                        help="argumentos fijos de gradlew (por defecto los del proyecto)")
    parser.add_argument("--historico", default=None, help="fichero TSV donde añadir la fila")
    parser.add_argument("tareas", nargs="+", help="tareas Gradle, p. ej. clean :app:testDebugUnitTest")
    args = parser.parse_args()

    if not (ANDROID / "gradlew").exists():
        print("ERROR falta android/gradlew; ejecuta scripts/preparar-entorno-android.sh", file=sys.stderr)
        return 2
    if not os.environ.get("ANDROID_HOME"):
        print("ERROR ANDROID_HOME vacío: la corrida fallaría y además escribiría ~/.gradle dentro del "
              "workspace (659 MiB medidos el 2026-11-13). source entorno/local.sh antes.", file=sys.stderr)
        return 2

    orden = ["./gradlew"] + args.gradle_args.split()
    for prop in args.prop:
        clave = prop.split("=", 1)[0]
        orden.append(f"-P{prop}")
        if clave.startswith(("org.gradle.", "kotlin.")):
            orden.append(f"-D{prop}")
    orden += args.tareas

    estado_inicial = {
        "opt_cerox_mib": round(du_mb(Path(os.environ.get("GRADLE_USER_HOME", "/opt/cerox/gradle"))), 1),
        "cpus": os.cpu_count(),
        "ram_mb": round(meminfo_mb()),
        "swap_activo": len(Path("/proc/swaps").read_text().splitlines()) - 1,
        "head": subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT,
                               capture_output=True, text=True).stdout.strip(),
    }

    pico_rss = 0.0
    minimo_disponible = meminfo_mb()
    paro = threading.Event()

    def vigilar() -> None:
        nonlocal pico_rss, minimo_disponible
        while not paro.is_set():
            pico_rss = max(pico_rss, rss_java_mb())
            minimo_disponible = min(minimo_disponible, meminfo_mb())
            time.sleep(0.5)

    # La salida de Gradle va a un fichero, no a /dev/null: el 2026-11-13 tres
    # comprobaciones no midieron nada porque un BUILD FAILED por ANDROID_HOME se leyó
    # como verde al mirar sólo los XML de la corrida anterior.
    registro = Path(f"/tmp/medir-build-{args.nombre}.log")
    vigilante = threading.Thread(target=vigilar, daemon=True)
    print(f"[{args.nombre}] {' '.join(orden)}\n  registro: {registro}", flush=True)
    inicio = time.time()
    vigilante.start()
    # Sin shell y sin pipe: el código de salida es el de Gradle, no el de un intermedio.
    with registro.open("w", encoding="utf-8", errors="replace") as salida_gradle:
        completado = subprocess.run(orden, cwd=ANDROID, stdout=salida_gradle, stderr=subprocess.STDOUT)
    segundos = time.time() - inicio
    paro.set()
    vigilante.join(timeout=2)

    fila = {
        "variante": args.nombre,
        "segundos": round(segundos, 1),
        "exit": completado.returncode,
        "pico_rss_java_mb": round(pico_rss),
        "min_mem_available_mb": round(minimo_disponible),
        "propiedades": args.prop,
        "args_gradle": args.gradle_args,
        "tareas": args.tareas,
        "pruebas": contar_pruebas(),
        "apks": medir_apks(),
        "inicio": estado_inicial,
        "cuando": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }

    print(json.dumps(fila, ensure_ascii=False, indent=2))
    if completado.returncode != 0:
        print(f"AVISO la corrida terminó con {completado.returncode}: las cifras de tiempo y memoria "
              "son de un build que no terminó. No comparar con variantes verdes.", file=sys.stderr)
        cola = registro.read_text(encoding="utf-8", errors="replace").splitlines()
        utiles = [l for l in cola if not l.startswith(("Download", " ", "\tat"))][-40:]
        print("--- últimas líneas útiles del registro ---", file=sys.stderr)
        for linea in utiles:
            print("  " + linea[:200], file=sys.stderr)

    historico = Path(args.historico) if args.historico else ROOT.parent / "ci" / "paralelismo.tsv"
    if historico.parent.is_dir():
        nuevo = not historico.exists()
        columnas = ["cuando", "variante", "head", "segundos", "exit", "pico_rss_java_mb",
                    "min_mem_available_mb", "suites", "pruebas", "fallos", "omitidas",
                    "xml_mas_viejo_s", "apk_app_bytes", "apk_app_sha256", "propiedades", "args_gradle"]
        with historico.open("a", encoding="utf-8") as salida:
            if nuevo:
                salida.write("\t".join(columnas) + "\n")
            pruebas = fila["pruebas"]
            apk = fila["apks"].get("app", {})
            salida.write("\t".join(str(x) for x in [
                fila["cuando"], args.nombre, estado_inicial["head"], fila["segundos"],
                completado.returncode, fila["pico_rss_java_mb"], fila["min_mem_available_mb"],
                pruebas.get("suites", 0), pruebas.get("pruebas", 0), pruebas.get("fallos", 0),
                pruebas.get("omitidas", 0), pruebas.get("xml_mas_viejo_s", ""),
                apk.get("bytes", ""), (apk.get("sha256") or "")[:16], ",".join(args.prop),
                args.gradle_args,
            ]) + "\n")
        print(f"histórico: {historico}", file=sys.stderr)
    return completado.returncode


if __name__ == "__main__":
    sys.exit(main())
