"""Config Manager — Gestión de configuración jerárquica.

Soporte para archivos YAML, variables de entorno y valores por defecto.
"""

from __future__ import annotations

import logging
import os
from typing import Any, Dict, Optional

try:
    import yaml

    HAS_YAML = True
except ImportError:
    HAS_YAML = False

logger = logging.getLogger("ceroX.core.config")


class ConfigManager:
    """Gestor de configuración con soporte para YAML y variables de entorno."""

    def __init__(self, config_path: Optional[str] = None):
        self._config: Dict[str, Any] = {}
        self._config_path = config_path
        if config_path:
            self.load(config_path)

    def load(self, path: str) -> None:
        """Carga configuración desde un archivo YAML."""
        if not HAS_YAML:
            logger.warning("PyYAML not installed. Cannot load YAML config.")
            return

        try:
            with open(path, "r", encoding="utf-8") as fh:
                data = yaml.safe_load(fh) or {}
            self._config = self._deep_merge(self._config, data)
            logger.info("Configuration loaded from %s", path)
        except FileNotFoundError:
            logger.warning("Config file not found: %s", path)
        except Exception as exc:
            logger.exception("Failed to load config from %s: %s", path, exc)

    def get(self, key: str, default: Any = None) -> Any:
        """Obtiene un valor de configuración usando notación con puntos (ej. 'core.event_bus.enabled')."""
        parts = key.split(".")
        current: Any = self._config
        for part in parts:
            if isinstance(current, dict) and part in current:
                current = current[part]
            else:
                return default
        return current

    def set(self, key: str, value: Any) -> None:
        """Establece un valor de configuración."""
        parts = key.split(".")
        current = self._config
        for part in parts[:-1]:
            current = current.setdefault(part, {})
        current[parts[-1]] = value

    def get_all(self) -> Dict[str, Any]:
        """Devuelve una copia de toda la configuración."""
        import copy

        return copy.deepcopy(self._config)

    @staticmethod
    def _deep_merge(base: Dict[str, Any], override: Dict[str, Any]) -> Dict[str, Any]:
        """Combina dos diccionarios de forma recursiva."""
        result = dict(base)
        for key, value in override.items():
            if (
                key in result
                and isinstance(result[key], dict)
                and isinstance(value, dict)
            ):
                result[key] = ConfigManager._deep_merge(result[key], value)
            else:
                result[key] = value
        return result

    def env_override(self, prefix: str = "CEROX_") -> None:
        """Sobrescribe valores de configuración con variables de entorno."""
        for env_key, env_value in os.environ.items():
            if env_key.startswith(prefix):
                config_key = env_key[len(prefix):].lower().replace("__", ".")
                self.set(config_key, env_value)
                logger.debug("Env override: %s = %s", config_key, env_value)