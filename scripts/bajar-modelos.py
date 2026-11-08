#!/usr/bin/env python3
"""
Baja los modelos ONNX que cero-X empaqueta.

POR QUÉ ESTE SCRIPT EXISTE
--------------------------
Los modelos pesan 373 MiB entre los cuatro. GitHub rechaza cualquier archivo
de más de 100 MB, y tres de ellos los superan, así que no pueden vivir en el
árbol de git. Se bajan aquí, se comprueba el sha256 y se dejan en su sitio.

Sin la comprobación de hash esto sería un script que instala lo que le den.
Con ella, si el archivo que llega no es el que se verificó ejecutándolo en
ONNX Runtime, se borra y el build falla.

Uso:
    python3 scripts/bajar-modelos.py

Deja los archivos en android/app/src/main/assets/models/
"""
import hashlib
import sys
import urllib.request
from pathlib import Path

DESTINO = Path(__file__).resolve().parent.parent / "android/app/src/main/assets/models"

# url, sha256, bytes. Los hashes son los de los archivos que se ejecutaron en
# ONNX Runtime 1.17 y que están en MANIFEST.json.
MODELOS = [
    (
        "encoder_fp16.onnx",
        "https://huggingface.co/onnx-community/manga-ocr-base-ONNX/resolve/main/onnx/encoder_model_fp16.onnx",
        "1a6a57bc3608195c4577b13ac3aadab810dce42fa22c5a3acf0570bffc013b60",
        171_855_254,
    ),
    (
        "decoder_model_quantized.onnx",
        "https://huggingface.co/onnx-community/manga-ocr-base-ONNX/resolve/main/onnx/decoder_model_quantized.onnx",
        "2e7177d2b0a59f1c612b694ed70c13971bee765cc2b2bc7bc9376e4753652f27",
        29_631_616,
    ),
    (
        "colorize.onnx",
        "https://huggingface.co/ifritraen/manga-colorization-v2-fp32/resolve/main/manga_colorization_v2_fp32.onnx",
        "268f8d2ae0333eaf687132e68a6ba83fe0e740e8fd952b325c4660f103757b29",
        122_961_127,
    ),
    (
        "esrgan_x4.onnx",
        "https://huggingface.co/SceneWorks/real-esrgan-onnx/resolve/main/real_esrgan_x4.onnx",
        "5c586662929cbc686c1a5c38d9c060dbdb4ea5863a1f7672b8c0761e6b89c033",
        67_065_453,
    ),
]


def sha256(ruta: Path) -> str:
    h = hashlib.sha256()
    with ruta.open("rb") as f:
        for bloque in iter(lambda: f.read(1 << 20), b""):
            h.update(bloque)
    return h.hexdigest()


def main() -> int:
    DESTINO.mkdir(parents=True, exist_ok=True)
    fallos = 0

    for nombre, url, esperado, tam in MODELOS:
        destino = DESTINO / nombre

        if destino.exists() and sha256(destino) == esperado:
            print(f"  ya está y coincide  {nombre}")
            continue

        print(f"  bajando {nombre} ({tam / 1048576:.2f} MiB)…")
        temporal = destino.with_suffix(".parcial")
        try:
            with urllib.request.urlopen(url, timeout=300) as r, temporal.open("wb") as f:
                while True:
                    bloque = r.read(1 << 20)
                    if not bloque:
                        break
                    f.write(bloque)
        except Exception as e:
            print(f"    FALLO al bajar: {e}")
            temporal.unlink(missing_ok=True)
            fallos += 1
            continue

        obtenido = sha256(temporal)
        if obtenido != esperado:
            print(f"    HASH NO COINCIDE en {nombre}")
            print(f"      esperado {esperado}")
            print(f"      obtenido {obtenido}")
            print("    El archivo se borra: no es el modelo que se verificó.")
            temporal.unlink()
            fallos += 1
            continue

        temporal.rename(destino)
        print(f"    ok, sha256 coincide")

    if fallos:
        print(f"\n{fallos} modelo(s) sin colocar. El build va a fallar sin ellos.")
        return 1
    print("\nLos 4 modelos están en su sitio y coinciden con lo verificado.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
