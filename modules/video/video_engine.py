"""VideoEngine — Motor de video para cero-X.

Reproductor y gestor de video con soporte para codecs abiertos y FFmpeg.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, List, Optional

from core.engine import ModuleInterface

logger = logging.getLogger("ceroX.modules.video")


class VideoEngine(ModuleInterface):
    name = "video"
    version = "0.1.0"
    dependencies = []

    def __init__(self, config: Optional[dict] = None):
        super().__init__(config)
        self.backend = self.config.get("backend", "ffmpeg")
        self.hw_accel = self.config.get("hw_accel", False)
        self._library: List[Dict[str, Any]] = []

    def initialize(self, config: dict) -> bool:
        self.config = config
        self.backend = config.get("backend", "ffmpeg")
        self.hw_accel = config.get("hw_accel", False)
        logger.info(
            "Video Engine initialized — backend: %s, hw_accel: %s",
            self.backend, self.hw_accel,
        )
        self._initialized = True
        return True

    def start(self) -> bool:
        if not self._initialized:
            return False
        self._started = True
        logger.info("Video Engine started")
        return True

    def stop(self) -> bool:
        self._started = False
        logger.info("Video Engine stopped")
        return True

    def health_check(self) -> dict:
        base = super().health_check()
        base.update({
            "backend": self.backend,
            "hw_accel": self.hw_accel,
            "library_items": len(self._library),
        })
        return base

    def add_to_library(self, item: Dict[str, Any]) -> bool:
        """Añade un video a la biblioteca local."""
        self._library.append(item)
        logger.info("Added to video library: %s", item.get("title", "unknown"))
        return True

    def list_library(self) -> list[Dict[str, Any]]:
        """Lista todos los videos de la biblioteca."""
        return list(self._library)

    def play(self, video_id: str) -> dict:
        """Reproduce un video por su ID."""
        for item in self._library:
            if item.get("id") == video_id:
                logger.info("Playing video: %s", item.get("title", "unknown"))
                return {"status": "playing", "video": item}
        return {"status": "error", "message": "Video not found"}