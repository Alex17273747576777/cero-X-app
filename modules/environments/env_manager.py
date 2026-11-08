"""EnvironmentManager — Gestor de entornos de trabajo para cero-X.

Soporta Debian (estable/testing/sid), contenedores y máquinas virtuales ligeras.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict, List, Optional

from core.engine import ModuleInterface

logger = logging.getLogger("ceroX.modules.environments")


class EnvironmentManager(ModuleInterface):
    name = "environments"
    version = "0.1.0"
    dependencies = []

    def __init__(self, config: Optional[dict] = None):
        super().__init__(config)
        self.preferred_distro = self.config.get("preferred_distro", "debian")
        self.debian_version = self.config.get("debian_version", "testing")
        self.container_runtime = self.config.get("container_runtime", "podman")
        self._environments: List[Dict[str, Any]] = []

    def initialize(self, config: dict) -> bool:
        self.config = config
        self.preferred_distro = config.get("preferred_distro", "debian")
        self.debian_version = config.get("debian_version", "testing")
        self.container_runtime = config.get("container_runtime", "podman")
        logger.info(
            "Environment Manager initialized — distro: %s, version: %s, runtime: %s",
            self.preferred_distro, self.debian_version, self.container_runtime,
        )
        self._initialized = True
        return True

    def start(self) -> bool:
        if not self._initialized:
            return False
        self._started = True
        logger.info("Environment Manager started")
        return True

    def stop(self) -> bool:
        self._started = False
        logger.info("Environment Manager stopped")
        return True

    def health_check(self) -> dict:
        base = super().health_check()
        base.update({
            "distro": self.preferred_distro,
            "version": self.debian_version,
            "runtime": self.container_runtime,
            "environments": len(self._environments),
        })
        return base

    def list_available_debian_versions(self) -> list[str]:
        """Lista versiones disponibles de Debian."""
        return ["stable", "testing", "sid"]

    def create_environment(self, name: str, distro: str, version: str) -> dict:
        """Crea un nuevo entorno de desarrollo."""
        logger.info("Creating environment: %s (%s %s)", name, distro, version)
        env = {
            "name": name,
            "distro": distro,
            "version": version,
            "status": "created",
        }
        self._environments.append(env)
        return env

    def list_environments(self) -> list[Dict[str, Any]]:
        """Lista todos los entornos creados."""
        return list(self._environments)

    def download_debian_iso(self, version: str, output_dir: Path) -> dict:
        """Descarga una imagen ISO de Debian."""
        logger.info("Downloading Debian %s ISO to %s", version, output_dir)
        return {
            "version": version,
            "output": str(output_dir),
            "status": "scheduled",
            "note": "Verificar https://debian.org para ISOs oficiales",
        }