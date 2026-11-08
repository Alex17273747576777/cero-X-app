# El APK, medido por dentro

Reconstruido el **2026-11-08** sobre el commit `9675065`. No es una estimación:
se abrió el APK y se contaron las entradas.

## Ficha

| campo | valor |
|---|---|
| archivo | `cero-X-1.0.0-beta-universal-release.apk` |
| bytes | 475 523 392 (453,5 MiB) |
| contenido descomprimido | 482,8 MiB en 881 entradas |
| sha256 | `1cc95a8a90b862f80b3c027bb5f61c9f8cc437b22e87976812b6505fe87559a9` |

## Firma

`apksigner verify` → **Verifies**. Esquema **v2: true · v3: true · v1: false**.
Certificado `CN=cero-X, O=La Bandita, C=DO`, RSA 4096, huella SHA-256
`f03505ce861af5ddbf1a2c31fe308a5d7ae822c40689cf5893a8263b81232fb3`.

`v1: false` **no es un fallo**: el esquema v2 existe desde Android 7.0 (API 24) y
`minSdk` es 26. Un comentario del `build.gradle` afirmaba lo contrario y se
corrigió.

## Contenido por tipo

| tipo | entradas | MiB |
|---|---|---|
| MODELOS (`.onnx`) | 5 | 378,3 |
| NATIVO (`.so`) | 12 | 62,3 |
| DEX | 3 | 39,9 |
| otros | 77 | 1,7 |
| recursos | 784 | 0,6 |

**Cero relleno.** Los 378,3 MiB de modelos son los cinco que la app carga, con
su tamaño real:

```
163,89 MiB  assets/models/encoder_fp16.onnx
117,26 MiB  assets/models/colorize.onnx
 63,95 MiB  assets/models/esrgan_x4.onnx
 28,26 MiB  assets/models/decoder_model_quantized.onnx
  4,92 MiB  assets/models/anime4x.onnx
```

Ortogonalidad de arquitecturas comprobada: **4 ABIs × 3 `.so`** =
`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`. Es un APK universal de verdad.

## Lo que este APK no trae

- **No trae runtime de traducción.** El OCR (`manga-ocr`) lee; traducir no.
- **No trae inpainting LaMa.**
- **No se ha instalado en un dispositivo.** El sandbox no tiene `/dev/kvm`, así
  que no hay emulador. Que compile y firme no es lo mismo que que arranque.

## La lección del heap

R8 necesita memoria y la máquina tiene 1982 MiB. Medido:

| heap | resultado |
|---|---|
| `-Xmx1024m` | `R8: OutOfMemoryError` explícito |
| `-Xmx1400m` | el daemon muere **sin mensaje** (OOM killer) |
| `-Xmx2048m` | termina en 15m 05s |

Bajar el heap no acelera: convierte un fallo visible en uno silencioso. Se quedó
en 2048m, documentado en `gradle.properties` para que nadie lo "optimice" otra vez.
