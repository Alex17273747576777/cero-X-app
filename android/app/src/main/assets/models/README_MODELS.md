# Modelos ONNX de cero-X

Este directorio contiene los modelos de IA utilizados por cero-X.

## Modelos incluidos (descarga bajo demanda)

| Modelo | Tamaño aprox. | Uso | Módulo |
|--------|--------------|-----|--------|
| manga_colorization_v2_int8.onnx | ~61.6 MB | Colorización neural de manga | KisaraNeuralColorizer |
| deoldify_artistic_quant.onnx | ~170 MB | Colorización artística | KisaraNeuralColorizer |
| PP-OCRv6_manga | ~45 MB | OCR de páginas de manga | KisaraOcrTranslator |
| llama.cpp (Index-Translate 2B) | ~1.3 GB | Traducción de manga (opcional, descarga externa) | KisaraOcrTranslator |
| LaMa inpainting | ~150 MB | Eliminación de texto en balones | KisaraOcrTranslator |

## Configuración

Los modelos se descargan bajo demanda desde:
- https://github.com/ifritraen/KisaraTranslator/releases
- https://github.com/ifritraen/KisaraColorizer/releases

## Tamaño del APK universal

El APK universal incluye bibliotecas nativas para:
- armeabi-v7a (ARM 32-bit)
- arm64-v8a (ARM 64-bit)
- x86 (Intel 32-bit)
- x86_64 (Intel 64-bit)

**Tamaño objetivo: 200-750 MB** (incluye ONNX Runtime para 4 arquitecturas + modelos base)

## Notas de eficiencia

- ONNX Runtime ejecuta en CPU (NEON) o NNAPI (NPU/GPU) - no requiere GPU NVIDIA propietaria
- R8/ProGuard elimina todo código muerto
- shrinkResources elimina recursos no utilizados
- MultiDex habilitado para apps grandes
- Core library desugaring para compatibilidad con Android 8+