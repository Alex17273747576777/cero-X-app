"""cero-X Core — Núcleo del sistema modular.

Inspirado en la filosofía del núcleo de Linux: abierto, extensible y comunitario.
"""

__version__ = "0.1.0"
__author__ = "cero-X Development Team"
__license__ = "Apache-2.0"

from .engine import CoreEngine, ModuleInterface, EventBus, ModuleRegistry
from .config import ConfigManager

__all__ = [
    "CoreEngine",
    "ModuleInterface",
    "EventBus",
    "ModuleRegistry",
    "ConfigManager",
]