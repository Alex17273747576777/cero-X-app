#!/usr/bin/env python3
"""clone_repos.py — Clona ecosistemas de referencia para cero-X.

Clona los repositorios de referencia mencionados en el proyecto:
- Miyomi / Mihon (lector de manga)
- Wotaku (índice otaku)
- ifritraen/kisara (fork con características avanzadas)
"""

from __future__ import annotations

import logging
import subprocess
from pathlib import Path

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger("ceroX.clone_repos")

REPOS = [
    {
        "name": "mihon",
        "url": "https://github.com/mihonapp/mihon.git",
        "target": "assets/ecosystems/mihon",
    },
    {
        "name": "komikku",
        "url": "https://github.com/komikku-app/komikku.git",
        "target": "assets/ecosystems/komikku",
    },
    {
        "name": "kisara",
        "url": "https://github.com/ifritraen/kisara.git",
        "target": "assets/ecosystems/kisara",
    },
    {
        "name": "wotaku",
        "url": "https://github.com/wotakumoe/wotaku.git",
        "target": "assets/ecosystems/wotaku",
    },
]


def clone_repo(repo: dict, base_dir: Path) -> None:
    target = base_dir / repo["target"]
    if target.exists():
        logger.info("Ya existe: %s (omitiendo)", target)
        return

    target.parent.mkdir(parents=True, exist_ok=True)
    logger.info("Clonando %s desde %s", repo["name"], repo["url"])
    try:
        subprocess.run(
            ["git", "clone", "--depth", "1", repo["url"], str(target)],
            check=True,
            capture_output=True,
            text=True,
        )
        logger.info("Clonado exitosamente: %s", target)
    except subprocess.CalledProcessError as exc:
        logger.error("Error clonando %s: %s", repo["name"], exc.stderr)


def main() -> None:
    base_dir = Path(__file__).resolve().parents[1]
    logger.info("Directorio base: %s", base_dir)

    for repo in REPOS:
        clone_repo(repo, base_dir)

    logger.info("Clonación de ecosistemas completada.")


if __name__ == "__main__":
    main()