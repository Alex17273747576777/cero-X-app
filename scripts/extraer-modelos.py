#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
"""
Copia los modelos de `assets/models` a un directorio de archivos, una sola vez.

POR QUÉ HACE FALTA ESTO Y NO `ModelAssets`
------------------------------------------
`ModelAssets` documenta que `OrtSession.createSession` no acepta un descriptor
de asset, y es verdad: acepta una ruta o un array de bytes. Para el pipeline de
lectura se usa una ruta, así que los archivos tienen que estar una vez en
`filesDir`. La copia se hace contra el tamaño y el sha256 del MANIFEST: si ya
está y coincide, no se copia nada.

El precio es real y se dice: en el teléfono los modelos quedan dos veces (dentro
del APK y en filesDir). Son 378 MiB. Otra opción sería un `AssetFileDescriptor`
mapeado, que ONNX Runtime no soporta en Android 1.17; no se finge que sí.

Uso: python3 scripts/extraer-modelos.py <dir-assets> <dir-destino>
"""
import hashlib
import json
import os
import shutil
import sys


def sha256(ruta, bloque=1 << 20):
    h = hashlib.sha256()
    with open(ruta, "rb") as f:
        while True:
            trozo = f.read(bloque)
            if not trozo:
                break
            h.update(trozo)
    return h.hexdigest()


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    origen, destino = sys.argv[1], sys.argv[2]
    os.makedirs(destino, exist_ok=True)
    with open(os.path.join(origen, "MANIFEST.json"), encoding="utf-8") as f:
        man = json.load(f)

    for m in man["modelos"]:
        src = os.path.join(origen, m["archivo"])
        dst = os.path.join(destino, m["archivo"])
        if not os.path.exists(src):
            print(f"FALTA {m['archivo']}")
            return 1
        if os.path.exists(dst) and os.path.getsize(dst) == m["bytes"]:
            print(f"{m['archivo']}: ya está ({m['bytes']} B)")
            continue
        print(f"{m['archivo']}: copiando {m['bytes']} B…", end=" ", flush=True)
        shutil.copyfile(src, dst + ".tmp")
        os.replace(dst + ".tmp", dst)
        real = sha256(dst)
        if real != m["sha256"]:
            print(f"sha256 NO coincide ({real[:12]} vs {m['sha256'][:12]})")
            return 1
        print("ok, sha256 correcto")
    return 0


if __name__ == "__main__":
    sys.exit(main())
