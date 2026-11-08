#!/usr/bin/env python3
"""
¿El OCR empaquetado lee de verdad, o solo devuelve tokens con buena pinta?

Este script dibuja texto japonés en una imagen usando una fuente real, se la
pasa al modelo y compara lo que devuelve. Es la diferencia entre «el pipeline
corre» y «el pipeline lee», y esa diferencia es exactamente la que en este
repositorio ya se coló una vez.

Límites que hay que decir en voz alta:
  - Se dibuja con una fuente del sistema, no con un escaneo de manga. La
    tipografía, el ruido y los balones no se prueban aquí.
  - Solo se prueban frases cortas; el modo lectura de página completa necesita
    detección de cajas, que es otro paso.
Lo que sí se prueba: que el vocabulario está bien enlazado, que el decodificador
termina en [SEP] y que el texto que sale tiene sentido japonés.
"""
import json
import sys
import time

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageFont

MODELOS = "/opt/work/cerox/android/app/src/main/assets/models"
VOCAB = "/tmp/vocab.txt"
PAD, CLS, SEP, UNK = 0, 2, 3, 1
MAX_TOKENS = 64


def cargar_vocab():
    with open(VOCAB, encoding="utf-8") as f:
        return [linea.rstrip("\n") for linea in f]


def buscar_fuente():
    """
    Una fuente que **dibuje japonés de verdad**.

    La primera versión de este script usó DejaVuSans sin comprobarlo: no tiene
    glifos japoneses, así que el modelo recibió cuadros vacíos y devolvió
    «それは、» para media docena de entradas distintas. Ese resultado no probaba
    que el OCR fallara; probaba que la prueba estaba mal montada. Ahora se
    comprueba que la fuente dibuje algo con forma antes de usarla.
    """
    import glob
    candidatas = []
    for patron in (
        "/usr/share/fonts/opentype/noto/*CJK*.ttc",
        "/usr/share/fonts/**/*JP*.otf",
        "/usr/share/fonts/**/*.ttf",
        "/usr/share/fonts/**/*.otf",
    ):
        candidatas += sorted(glob.glob(patron, recursive=True))

    for ruta in candidatas:
        for tamano in (40, 48, 56):
            try:
                fuente = ImageFont.truetype(ruta, tamano)
                # Se dibuja «あ» sobre blanco y se mide cuántos píxeles oscuros
                # hay. Un cuadro vacío o un rectángulo de «falta el glifo» da
                # una proporción muy distinta a una letra real.
                lienzo = Image.new("L", (96, 96), 255)
                ImageDraw.Draw(lienzo).text((8, 8), "あ", fill=0, font=fuente)
                pixeles = np.asarray(lienzo)
                oscuros = int((pixeles < 128).sum())
                if 120 < oscuros < 3800:
                    return ruta, fuente
            except Exception:
                continue
    return None, None


def preparar(texto, fuente, ancho=512, alto=128):
    img = Image.new("RGB", (ancho, alto), "white")
    d = ImageDraw.Draw(img)
    d.text((10, 30), texto, fill="black", font=fuente)
    img = img.resize((224, 224), Image.BICUBIC)
    x = np.asarray(img).astype(np.float32) / 255.0
    x = (x - 0.5) / 0.5          # mean y std 0.5, como preprocessor_config.json
    return x.transpose(2, 0, 1)[None, ...]


def leer(enc, dec, vocab, pixel_values):
    t0 = time.time()
    hidden = enc.run(None, {"pixel_values": pixel_values})[0]
    t_enc = (time.time() - t0) * 1000
    ids = [CLS]
    t0 = time.time()
    for _ in range(MAX_TOKENS):
        logits = dec.run(
            None,
            {
                "input_ids": np.array([ids], dtype=np.int64),
                "encoder_hidden_states": hidden,
            },
        )[0]
        siguiente = int(np.argmax(logits[0, -1]))
        if siguiente == SEP:
            break
        ids.append(siguiente)
    t_dec = (time.time() - t0) * 1000
    texto = "".join(vocab[i] for i in ids if i not in (PAD, CLS, SEP, UNK))
    return texto, t_enc, t_dec, len(ids) - 1


def main():
    ruta_fuente, fuente = buscar_fuente()
    if fuente is None:
        print("NO HAY FUENTE CON JAPONÉS en este entorno: no se puede probar lectura real.")
        return 2
    print("fuente usada:", ruta_fuente)

    vocab = cargar_vocab()
    so = ort.SessionOptions()
    so.log_severity_level = 3
    enc = ort.InferenceSession(f"{MODELOS}/encoder_fp16.onnx", so, providers=["CPUExecutionProvider"])
    dec = ort.InferenceSession(f"{MODELOS}/decoder_model_quantized.onnx", so,
                               providers=["CPUExecutionProvider"])

    casos = [
        "こんにちは",
        "ありがとう",
        "まんが",
        "テスト",
        "おはようございます",
    ]

    filas = []
    aciertos = 0
    for esperado in casos:
        x = preparar(esperado, fuente)
        obtenido, t_enc, t_dec, tokens = leer(enc, dec, vocab, x)
        ok = obtenido.strip() == esperado
        aciertos += 1 if ok else 0
        print(f"  esperado «{esperado}»  obtenido «{obtenido}»  "
              f"{'✔' if ok else '✘'}  enc {t_enc:.0f} ms · dec {t_dec:.0f} ms · {tokens} tokens")
        filas.append({
            "esperado": esperado, "obtenido": obtenido, "exacto": ok,
            "encoder_ms": round(t_enc, 1), "decoder_ms": round(t_dec, 1), "tokens": tokens,
        })

    print(f"\n{aciertos}/{len(casos)} exactos")
    with open("/opt/work/medicion-ocr-real.json", "w") as f:
        json.dump({
            "fuente": ruta_fuente,
            "casos": filas,
            "exactos": aciertos,
            "total": len(casos),
            "nota": "texto sintetico con fuente del sistema; no es un escaneo de manga",
        }, f, indent=2, ensure_ascii=False)
    return 0 if aciertos == len(casos) else 1


if __name__ == "__main__":
    sys.exit(main())
