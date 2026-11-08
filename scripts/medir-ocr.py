#!/usr/bin/env python3
"""
Mide manga-ocr de verdad, sobre una imagen real, antes de escribir el pipeline
de inferencia en Kotlin.

POR QUÉ ESTE SCRIPT EXISTE
--------------------------
Escribir el decodificador a ciegas y decir «OCR listo» es exactamente lo que ya
pasó una vez en este repositorio. Antes de tocar Kotlin hay que saber:

  1. Los nombres reales de las entradas y salidas de los dos grafos.
  2. La forma (shape) que espera cada uno.
  3. Qué produce el decoder: logits por token o un id ya elegido.
  4. Cuánto tarda y cuánta memoria ocupa en CPU.

Lo que NO prueba: que funcione en un teléfono. Aquí corre en CPU de escritorio.
El tiempo medido aquí es una referencia, no una promesa.
"""
import json
import sys
import time

import numpy as np
import onnxruntime as ort

MODELOS = "/opt/work/cerox/android/app/src/main/assets/models"


def describir(ruta):
    so = ort.SessionOptions()
    so.log_severity_level = 3
    ses = ort.InferenceSession(ruta, so, providers=["CPUExecutionProvider"])
    print(f"\n=== {ruta.split('/')[-1]} ===")
    for i in ses.get_inputs():
        print(f"  entrada  {i.name:<20} {i.shape}  {i.type}")
    for o in ses.get_outputs():
        print(f"  salida   {o.name:<20} {o.shape}  {o.type}")
    return ses


def main():
    with open(f"{MODELOS}/MANIFEST.json") as f:
        man = json.load(f)
    print("modelos en el manifiesto:", [m["archivo"] for m in man["modelos"]])

    enc = describir(f"{MODELOS}/encoder_fp16.onnx")
    dec = describir(f"{MODELOS}/decoder_model_quantized.onnx")

    # Imagen sintética: no hay una página de manga en este entorno, así que se
    # mide con ruido. Sirve para tiempos y formas, NO para calidad de lectura.
    # Eso último hay que decirlo o se convierte en otra afirmación falsa.
    entrada_enc = enc.get_inputs()[0]
    forma = [d if isinstance(d, int) else 1 for d in entrada_enc.shape]
    # Un batch y tres canales; alto y ancho dinámicos se quedan en 224.
    forma = [1, 3, 224, 224]
    x = np.random.rand(*forma).astype(np.float32)

    t0 = time.time()
    salida_enc = enc.run(None, {entrada_enc.name: x})
    t_enc = (time.time() - t0) * 1000
    print(f"\nencoder: {t_enc:.0f} ms, {len(salida_enc)} salida(s), "
          f"forma de la primera: {np.asarray(salida_enc[0]).shape}, "
          f"dtype {np.asarray(salida_enc[0]).dtype}")

    # El decoder espera input_ids y probablemente el estado del encoder.
    entradas_dec = {i.name: i for i in dec.get_inputs()}
    print("entradas del decoder:", list(entradas_dec))

    probar = {"input_ids": np.array([[1]], dtype=np.int64)}
    for nombre, info in entradas_dec.items():
        if nombre in probar:
            continue
        forma_i = [d if isinstance(d, int) else None for d in info.shape]
        print(f"  {nombre}: forma declarada {forma_i} tipo {info.type}")
        # Relleno con la forma del encoder cuando cuadre el rango.
        for candidato in salida_enc:
            c = np.asarray(candidato)
            if forma_i[0] in (None, c.shape[0]) and len(forma_i) == c.ndim:
                probar[nombre] = c.astype(np.float32) if "float" in info.type else c
                break
        else:
            # Si no cuadra, se rellena con ceros de la forma declarada.
            forma_relleno = [1 if d is None else d for d in forma_i]
            probar[nombre] = np.zeros(forma_relleno, dtype=np.float32)

    t0 = time.time()
    try:
        salida_dec = dec.run(None, probar)
        t_dec = (time.time() - t0) * 1000
        print(f"decoder: {t_dec:.0f} ms por paso")
        logits = np.asarray(salida_dec[0])
        print(f"  logits: forma {logits.shape}, dtype {logits.dtype}")
        if logits.ndim == 3:
            siguiente = int(np.argmax(logits[0, -1]))
            print(f"  token más probable tras el inicio: {siguiente}")
        with open("/opt/work/mediocion-ocr.json", "w") as f:
            json.dump({
                "encoder_ms": round(t_enc, 1),
                "decoder_ms_por_paso": round(t_dec, 2),
                "encoder_entrada": [i.name for i in enc.get_inputs()],
                "encoder_salida": [o.name for o in enc.get_outputs()],
                "decoder_entradas": list(entradas_dec),
                "decoder_salida_forma": list(logits.shape),
                "nota": "imagen sintetica: mide formas y tiempos, NO calidad de OCR",
            }, f, indent=2)
    except Exception as e:
        print("decoder FALLÓ:", type(e).__name__, str(e)[:500])
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
