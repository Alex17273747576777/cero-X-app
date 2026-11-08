"""Módulo de Entrenamiento de IA — cero-X.

Entrenamiento y fine-tuning de modelos de IA locales con ONNX Runtime.
Optimizado para CPU y NNAPI (sin dependencia de GPU NVIDIA).
"""

from .training_engine import TrainingEngine

__all__ = ["TrainingEngine"]