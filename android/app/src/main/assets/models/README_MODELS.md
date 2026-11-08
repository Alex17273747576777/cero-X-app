# Modelos de cero-X

Los cuatro modelos de este directorio están **empaquetados dentro del APK** y
**verificados con ONNX Runtime 1.17 ejecutando una inferencia real en CPU**
antes de entrar. Lo que no pasó la prueba, no está aquí.

## Qué hay

| archivo | tamaño | qué hace | licencia |
|---|---:|---|---|
| `encoder_fp16.onnx` | 163,89 MiB | visión de manga-ocr: imagen → tokens (ViT, 224×224) | Apache-2.0 |
| `decoder_model_quantized.onnx` | 28,26 MiB | texto de manga-ocr: tokens → caracteres (vocabulario 6144) | Apache-2.0 |
| `colorize.onnx` | 117,26 MiB | colorización de páginas en blanco y negro (entrada 5 canales) | Apache-2.0 |
| `esrgan_x4.onnx` | 63,95 MiB | reescalado ×4 (Real-ESRGAN) | BSD-3-Clause |

Total: **373,36 MiB**. Todos `Stored` (sin comprimir) en el APK: el runtime los
mapea desde el archivo en vez de descomprimir cientos de MB en memoria.

## Origen y verificación

- `manga-ocr`: `onnx-community/manga-ocr-base-ONNX` (Apache-2.0).
- `colorize`: `ifritraen/manga-colorization-v2-fp32` (Apache-2.0).
- `esrgan_x4`: `SceneWorks/real-esrgan-onnx` (BSD-3-Clause).

Cada hash en `MANIFEST.json` corresponde al de la descarga original y al del
archivo dentro del APK, comprobado con `sha256sum` tras descomprimir.

## Lo que se descartó, y por qué

- `encoder_model_int8.onnx` y `decoder_model_int8.onnx`: **no cargan**. Usan el
  operador `ConvInteger`, que ONNX Runtime no implementa en CPU:
  `ORT_NOT_IMPLEMENTED: Could not find an implementation for ConvInteger(10)`.
  Habrían sido 111 MB de peso muerto.
- `decoder_model_fp16.onnx`: no carga. Error de tipos al construir el grafo
  (`tensor(float16)` donde espera `tensor(float)`).
- `decoder_model.onnx` (fp32, 112 MiB): funciona, pero `decoder_model_quantized.onnx`
  hace lo mismo con 28 MiB. Se prefiere el pequeño.

## Tiempos medidos en CPU (no en móvil)

| modelo | entrada | salida | tiempo |
|---|---|---|---:|
| `encoder_fp16` | 3×224×224 | [1,197,768] | 715–824 ms |
| `decoder_model_quantized` | 4 tokens | [1,4,6144] | 7–19 ms |
| `colorize` | 5×256×256 | [1,3,256,256] | 1 490 ms |
| `esrgan_x4` | 3×256×256 | [1,3,1024,1024] | 24 587 ms |

Medido en un contenedor x86 de 2 núcleos. Un móvil ARM con NNAPI será más
rápido en colorización y reescalado, y parecido en el codificador.
