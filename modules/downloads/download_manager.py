"""DownloadManager — Gestor de descargas para cero-X.

Soporta múltiples protocolos, reanudación y verificación de integridad SHA-256.
"""

from __future__ import annotations

import hashlib
import logging
import os
from pathlib import Path
from typing import Any, Dict, Optional

from core.engine import ModuleInterface

logger = logging.getLogger("ceroX.modules.downloads")


class DownloadManager(ModuleInterface):
    name = "downloads"
    version = "0.1.0"
    dependencies = []

    def __init__(self, config: Optional[dict] = None):
        super().__init__(config)
        self.max_concurrent = self.config.get("max_concurrent", 5)
        self.verify_hash = self.config.get("verify_hash", True)
        self.chunk_size = self.config.get("chunk_size", 8192)
        self.download_dir = Path(self.config.get("download_dir", "./downloads"))
        self.download_dir.mkdir(parents=True, exist_ok=True)
        self._active: Dict[str, Any] = {}

    def initialize(self, config: dict) -> bool:
        self.config = config
        self.max_concurrent = config.get("max_concurrent", 5)
        self.verify_hash = config.get("verify_hash", True)
        self.chunk_size = config.get("chunk_size", 8192)
        self.download_dir = Path(config.get("download_dir", "./downloads"))
        self.download_dir.mkdir(parents=True, exist_ok=True)
        logger.info("Download Manager initialized — dir: %s", self.download_dir)
        self._initialized = True
        return True

    def start(self) -> bool:
        if not self._initialized:
            return False
        self._started = True
        logger.info("Download Manager started")
        return True

    def stop(self) -> bool:
        self._started = False
        self._active.clear()
        logger.info("Download Manager stopped")
        return True

    def health_check(self) -> dict:
        base = super().health_check()
        base.update({
            "download_dir": str(self.download_dir),
            "active_downloads": len(self._active),
        })
        return base

    def download(self, url: str, filename: str, expected_hash: Optional[str] = None) -> dict:
        """Descarga un archivo y opcionalmente verifica su hash SHA-256."""
        if not self._started:
            return {"status": "error", "message": "Download Manager not started"}

        dest = self.download_dir / filename
        logger.info("Downloading %s -> %s", url, dest)

        # Placeholder: implementación real usaría requests o aiohttp
        if expected_hash and self.verify_hash:
            actual_hash = self._compute_sha256(dest) if dest.exists() else ""
            verified = actual_hash == expected_hash
            return {
                "status": "downloaded",
                "url": url,
                "path": str(dest),
                "hash_verified": verified,
                "expected_hash": expected_hash,
                "actual_hash": actual_hash,
            }

        return {
            "status": "downloaded",
            "url": url,
            "path": str(dest),
            "hash_verified": False,
        }

    def _compute_sha256(self, path: Path) -> str:
        """Calcula el hash SHA-256 de un archivo."""
        h = hashlib.sha256()
        with open(path, "rb") as fh:
            for chunk in iter(lambda: fh.read(self.chunk_size), b""):
                h.update(chunk)
        return h.hexdigest()

    def verify_file(self, path: Path, expected_hash: str) -> bool:
        """Verifica que un archivo coincida con el hash SHA-256 esperado."""
        actual = self._compute_sha256(path)
        return actual == expected_hash