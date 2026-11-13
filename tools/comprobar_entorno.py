#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
"""Audita presupuesto de snapshots y herramientas, sin crear cachés ni procesos."""
from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import sys

HOME = Path.home()
ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT.parent
SDK = Path(os.environ.get("ANDROID_HOME", HOME / ".local/share/cerox-android-sdk"))
JAVA = Path("/usr/lib/jvm/java-21-openjdk-amd64/bin/java")
EXCLUDED = {
    ".arena", ".cache", ".local", ".mypy_cache", ".next", ".nox", ".npm",
    ".nuxt", ".output", ".parcel-cache", ".playwright-cli", ".pytest_cache",
    ".ruff_cache", ".svelte-kit", ".tox", ".turbo", ".venv", ".vite",
    "__pycache__", "build", "coverage", "dist", "node_modules", "out", "target",
}
SENSITIVE = {".git/config", ".git/credentials", ".git-credentials", ".netrc"}


def snapshot_files() -> list[Path]:
    result = []
    for directory, names, files in os.walk(HOME):
        names[:] = [name for name in names if name not in EXCLUDED]
        base = Path(directory)
        for name in files:
            path = base / name
            relative = str(path.relative_to(HOME))
            if relative not in SENSITIVE and not path.is_symlink():
                result.append(path)
    return result


def main() -> int:
    errors: list[str] = []
    files = snapshot_files()
    size = sum(path.stat().st_size for path in files if path.exists())
    mib = size / 1024 / 1024
    print(f"WORKSPACE_PERSISTENTE bytes={size} MiB={mib:.2f} archivos={len(files)}")
    # Aviso antes del límite de Arena: 128 MiB / 10.000 archivos.
    if size > 110 * 1024 * 1024:
        errors.append("El workspace supera el margen operativo de 110 MiB")
    if len(files) > 8_500:
        errors.append("El workspace supera el margen operativo de 8.500 archivos")
    for path in sorted(files, key=lambda item: item.stat().st_size, reverse=True)[:5]:
        print(f"MAYOR bytes={path.stat().st_size} ruta={path}")

    # Cachés que deben vivir FUERA del workspace, con nombre y arreglo. `.gradle` no
    # está en EXCLUDED del snapshot, así que en cuanto Gradle corre sin
    # `source entorno/local.sh` usa su GRADLE_USER_HOME por defecto (~/.gradle) y se
    # come el presupuesto: el 2026-11-13 fueron 659 MiB y 4.600 archivos de una sola
    # corrida que además falló por no tener ANDROID_HOME. El error genérico de los
    # 110 MiB decía que había que limpiar pero no decía qué ni por qué estaba ahí.
    for nombre, arreglo in (
        (".gradle", "Gradle corrió sin `source entorno/local.sh`. Borrar con "
                    "`rm -rf ~/.gradle` y cargar el entorno antes de cada gradlew "
                    "(GRADLE_USER_HOME canónico: /opt/cerox/gradle)"),
        (".m2", "Maven resolvió dentro del workspace. Borrar con `rm -rf ~/.m2`; "
                "el repo canónico es /opt/cerox/m2"),
        (".kotlin", "Caché del compilador de Kotlin dentro del workspace; "
                    "`rm -rf ~/.kotlin`"),
    ):
        ruta = HOME / nombre
        if ruta.is_dir():
            peso = sum(f.stat().st_size for f in ruta.rglob("*") if f.is_file())
            errors.append(
                f"Caché {nombre}/ dentro del workspace ({peso / 1024 / 1024:.0f} MiB). {arreglo}"
            )

    # Los directorios de build sí están excluidos del snapshot, pero ocupan disco real
    # y la receta de cierre los borra. Se informan sin fallar: a mitad de una fase es
    # normal que existan, y convertirlo en error daría falsos positivos en rojo.
    for ruta in (ROOT / "android/app/build", ROOT / "android/build", ROOT / "android/.gradle"):
        if ruta.is_dir():
            peso = sum(f.stat().st_size for f in ruta.rglob("*") if f.is_file())
            print(f"BUILD_SIN_LIMPIAR {ruta.relative_to(ROOT)} MiB={peso / 1024 / 1024:.1f}")

    if not JAVA.is_file():
        errors.append("Falta JDK 21; ejecuta scripts/preparar-entorno-android.sh")
    else:
        version = subprocess.run(
            [str(JAVA), "-version"], capture_output=True, text=True, check=False,
        )
        print((version.stderr or version.stdout).splitlines()[0])
    for relative in (
        "platforms/android-36/android.jar",
        "build-tools/36.0.0/aapt",
        "platform-tools/adb",
    ):
        if not (SDK / relative).is_file():
            errors.append(f"Falta Android SDK: {relative}")
    if not os.access(ROOT / "android/gradlew", os.X_OK):
        errors.append("android/gradlew no es ejecutable")
    if shutil.disk_usage(HOME).free < 3 * 1024**3:
        errors.append("Quedan menos de 3 GiB libres en disco")

    if errors:
        for error in errors:
            print(f"ERROR {error}", file=sys.stderr)
        return 1
    print("ENTORNO_OK sin VM, sin modelos y sin cachés dentro del workspace")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
