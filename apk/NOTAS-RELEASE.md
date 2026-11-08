## cero-X 1.0.0-beta · universal · 453 MiB con modelos

APK **universal** firmado, reconstruido el **2026-11-08** con la licencia dentro y
los módulos nuevos de rendimiento, privilegios, licencia, descargador,
transcripción e instalación.

### Verificar antes de instalar

```sh
sha256sum -c SHA256SUMS.txt
```

| campo | valor |
|---|---|
| bytes | 475 572 663 |
| sha256 | `086876ac89431f30458572f3ede508c5a76dca223180e42e1134f3596f41a8bb` |
| firma | v2 ✓ v3 ✓ · `CN=cero-X, O=La Bandita, C=DO` · RSA 4096 |
| huella del certificado | `f03505ce861af5ddbf1a2c31fe308a5d7ae822c40689cf5893a8263b81232fb3` |

La huella se comprobó **descargando el asset de esta release y recalculándola**.

### Qué lleva dentro (medido, no estimado)

880 entradas. **378,3 MiB de modelos**, 62 de nativo en 4 ABIs, 40 de DEX.

```
163,89 MiB  assets/models/encoder_fp16.onnx
117,26 MiB  assets/models/colorize.onnx
 63,95 MiB  assets/models/esrgan_x4.onnx
 28,26 MiB  assets/models/decoder_model_quantized.onnx
  4,92 MiB  assets/models/anime4x.onnx
```

**Cero relleno.** Cero archivos de relleno; el tamaño es lo que pesa lo que hay.

### La licencia va dentro, no solo en el repo

```
assets/LICENSE    11 202 B
assets/NOTICE      3 218 B
```

Apache-2.0 no obliga a publicar el código modificado —eso es la GPL— pero sí
obliga a que el aviso llegue a quien recibe la app. Un NOTICE en el repositorio
no cumple esa obligación; por eso un task lo empaqueta en `preBuild`, y si falta,
**el build falla** en vez de distribuir sin aviso.

### Lo nuevo en el código, con sus pruebas

| módulo | qué hace | pruebas |
|---|---|---|
| rendimiento | 5 perfiles; el adaptativo va de térmico severo a ahorro antes de que el móvil se limite solo | 18 |
| privilegios | instalado / corriendo / autorizado son tres escalones, no uno | 13 |
| licencia | obligaciones con evidencia; cada modelo arrastra la suya | 15 |
| descargador | cola de 100 000 con tres frenos y techo por tipo de archivo | 25 |
| transcripción | variantes con reglas reales, glosario, tiempos que no desincronizan | 32 |
| instalación | primer plano / segundo plano / silencioso, sin prometer de más | 16 |

Total del proyecto: **205 pruebas, 0 fallos**, leídas del XML de JUnit.

### Lo que este APK NO trae

- **No trae runtime de traducción.** El OCR lee el texto; traducirlo no lo hace.
  El código lo dice con `ON_DEVICE.available = false` y el motivo escrito.
- **No trae inpainting LaMa.**
- **No trae Activity de lector.** El motor está; la pantalla no.
- **No se ha probado en un dispositivo.** No hay `/dev/kvm` en el entorno de
  compilación, así que no hay emulador. Compilar y firmar no es arrancar.
- **`anime4x.onnx` no tiene licencia declarada en su repositorio de origen.**
  Está escrito en el `NOTICE` porque descubrirlo después de publicar es peor.

### Aviso sobre permisos

El manifiesto declara `INSTALL_PACKAGES`, `DELETE_PACKAGES`, `QUERY_ALL_PACKAGES`
y `FOREGROUND_SERVICE_INSTALL_PACKAGES`. Ninguno se concede solo:

- `INSTALL_PACKAGES` es de sistema. Sin root ni Shizuku **no se otorga**, y la
  instalación pasa por la pantalla de confirmación de Android. La app te dice
  cuál de los tres caminos va a usar antes de usarlo.
- `QUERY_ALL_PACKAGES` está porque sin él `getPackageInfo` devuelve "no
  instalado" para Shizuku *aunque esté instalado*. Es un falso negativo, y un
  falso negativo es peor que un fallo.

Todo lo que estos módulos tocan es **interno a cero-X**: su propio lector, sus
propias descargas, sus propios modelos. No se cambian gobernadores de CPU ni los
Hz del panel, porque eso afecta a todo el teléfono y no es de la app.
