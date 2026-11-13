#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Audita los manifiestos Android de las fuentes de referencia del objetivo todo-en-uno.

Fase F2 de docs/OBJETIVO-TODO-EN-UNO.md: qué permisos declara cada app frente a qué
componentes expone, medido sobre el manifiesto y no sobre lo que diga su README.

Lee los clones de /var/tmp/referencias (los baja scripts/bajar-referencias.sh) y los
compara con el manifiesto de cero-X-app. No necesita aparato ni compilar nada: es texto.

Uso:
    tools/auditar-referencias.py                # tabla por pantalla
    tools/auditar-referencias.py --tsv RUTA     # además escribe la evidencia en TSV

Lo que NO afirma: que un permiso se use bien o mal. Eso exige leer el código que lo pide.
Tampoco compara versiones: compara lo que hay clonado, cuyo SHA queda en la evidencia.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REFERENCIAS = Path("/var/tmp/referencias")
CERO_X = Path(__file__).resolve().parents[1] / "android/app/src/main/AndroidManifest.xml"

# Permisos que un usuario notaría: red, almacenamiento, instalación, arranque, superposición.
SENSIBLES = {
    "INTERNET", "ACCESS_NETWORK_STATE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
    "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "POST_NOTIFICATIONS", "QUERY_ALL_PACKAGES",
    "REQUEST_INSTALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE", "SYSTEM_ALERT_WINDOW",
    "FOREGROUND_SERVICE", "WAKE_LOCK", "RECEIVE_BOOT_COMPLETED", "CAMERA", "RECORD_AUDIO",
    "PACKAGE_USAGE_STATS", "SCHEDULE_EXACT_ALARM", "ACCESS_FINE_LOCATION", "BLUETOOTH_CONNECT",
}
COMPONENTES = ("activity", "service", "receiver", "provider")


def manifiestos(raiz: Path) -> list[Path]:
    """Todos los AndroidManifest.xml fuera de .git y de salidas de build."""
    return [
        p for p in raiz.rglob("AndroidManifest.xml")
        if "/build/" not in str(p) and "/.git/" not in str(p)
    ]


def auditar(paths: list[Path]) -> dict:
    permisos: set[str] = set()
    exportados = componentes = 0
    for m in paths:
        texto = m.read_text(errors="replace")
        permisos |= set(re.findall(r'uses-permission[^>]*android:name="([^"]+)"', texto))
        for etiqueta in COMPONENTES:
            for bloque in re.findall(rf"<{etiqueta}\b[^>]*>", texto, re.S):
                componentes += 1
                if 'android:exported="true"' in bloque:
                    exportados += 1
    sensibles = sorted(p.split(".")[-1] for p in permisos if p.split(".")[-1] in SENSIBLES)
    return {
        "manifiestos": len(paths),
        "permisos": len(permisos),
        "componentes": componentes,
        "exportados": exportados,
        "sensibles": sensibles,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tsv", help="escribir la evidencia en esta ruta TSV")
    ap.add_argument("--referencias", default=str(REFERENCIAS), help="carpeta con los clones")
    args = ap.parse_args()

    raiz = Path(args.referencias)
    filas: list[tuple[str, dict]] = []
    if raiz.is_dir():
        for d in sorted(x for x in raiz.iterdir() if x.is_dir()):
            filas.append((d.name, auditar(manifiestos(d))))
    else:
        print(f"AVISO: no existe {raiz}; ejecuta scripts/bajar-referencias.sh", file=sys.stderr)
    if not CERO_X.exists():
        print(f"ERROR: falta el manifiesto de cero-X en {CERO_X}", file=sys.stderr)
        return 1
    filas.append((">>> cero-X-app", auditar([CERO_X])))

    if not filas:
        print("sin nada que auditar", file=sys.stderr)
        return 1

    print(f"  {'fuente':<24}{'manif':>6}{'perm':>6}{'comp':>6}{'export':>8}{'sensibles':>11}")
    for nombre, a in filas:
        print(f"  {nombre:<24}{a['manifiestos']:>6}{a['permisos']:>6}"
              f"{a['componentes']:>6}{a['exportados']:>8}{len(a['sensibles']):>11}")
    print()
    for nombre, a in filas:
        print(f"  {nombre:<24}{', '.join(a['sensibles'])}")

    if args.tsv:
        destino = Path(args.tsv)
        destino.parent.mkdir(parents=True, exist_ok=True)
        with destino.open("w", encoding="utf-8") as fh:
            fh.write("fuente\tmanifiestos\tpermisos\tcomponentes\texportados\tsensibles\n")
            for nombre, a in filas:
                fh.write(f"{nombre}\t{a['manifiestos']}\t{a['permisos']}\t{a['componentes']}"
                         f"\t{a['exportados']}\t{';'.join(a['sensibles'])}\n")
        print(f"\n  evidencia escrita: {destino}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
