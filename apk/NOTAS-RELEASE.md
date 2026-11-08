## cero-X 1.0.0-beta · universal · 453 MiB con modelos

APK **universal** firmado, reconstruido el **2026-11-08** sobre el commit
`9675065`. Las APK anteriores de esta release eran anteriores a `320e878` y
`6c3b7d5` y se retiraron: no reflejaban el código.

### Verificar antes de instalar

```sh
sha256sum -c SHA256SUMS.txt
```

| campo | valor |
|---|---|
| bytes | 475 523 392 |
| sha256 | `1cc95a8a90b862f80b3c027bb5f61c9f8cc437b22e87976812b6505fe87559a9` |
| firma | v2 ✓ v3 ✓ · `CN=cero-X, O=La Bandita, C=DO` · RSA 4096 |
| huella del certificado | `f03505ce861af5ddbf1a2c31fe308a5d7ae822c40689cf5893a8263b81232fb3` |

La huella se comprobó **descargando el asset de esta release y recalculándola**,
no leyendo el tamaño que informa la API.

### Qué lleva dentro (medido, no estimado)

Se abrió el APK y se contaron las entradas: **881 entradas, 482,8 MiB
descomprimidos**.

```
378,3 MiB  MODELOS (5 .onnx, los que la app carga)
 62,3 MiB  NATIVO  (12 .so, 4 ABIs × 3)
 39,9 MiB  DEX     (3)
  1,7 MiB  otros   (77)
  0,6 MiB  recursos (784)
```

**Cero relleno.** Los cinco modelos:

| modelo | MiB |
|---|---|
| `encoder_fp16.onnx` | 163,89 |
| `colorize.onnx` | 117,26 |
| `esrgan_x4.onnx` | 63,95 |
| `decoder_model_quantized.onnx` | 28,26 |
| `anime4x.onnx` | 4,92 |

ABIs presentes: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.

### Lo que este APK NO trae

- **No trae runtime de traducción.** El OCR lee el texto; traducirlo no lo hace.
- **No trae inpainting LaMa.**
- **No trae Activity de lector.** El motor está; la pantalla que muestra una
  página no existe todavía.
- **No se ha probado en un dispositivo.** El entorno de compilación no tiene
  `/dev/kvm`, así que no hay emulador. Compilar y firmar no es arrancar.

Estas cuatro líneas están aquí para que nadie instale esto esperando lo que no
hay. Son los huecos declarados, no un olvido de las notas.

### El debug

`cero-X-1.0.0-beta-universal-debug.apk` (87 401 796 B) se mantiene porque sirve
para depurar con logcat. No es la versión para usar.
