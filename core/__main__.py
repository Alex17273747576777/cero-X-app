"""Punto de entrada de la aplicación cero-X."""

from __future__ import annotations

import argparse
import logging
import sys
from typing import List, Optional

from .config import ConfigManager
from .engine import CoreEngine, ModuleMetadata

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger("ceroX")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="ceroX",
        description="cero-X App — Ecosistema todo-en-uno de código abierto",
    )
    parser.add_argument(
        "--config", "-c", default="config/settings.yaml", help="Ruta al archivo de configuración"
    )
    parser.add_argument(
        "--list-modules", action="store_true", help="Listar módulos registrados"
    )
    parser.add_argument(
        "--health", action="store_true", help="Mostrar estado de salud de los módulos"
    )
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    # Cargar configuración
    config_mgr = ConfigManager(args.config)
    config = config_mgr.get_all()
    config_mgr.env_override()

    # Inicializar núcleo
    engine = CoreEngine(config=config)

    # Registrar módulos base (placeholder — se llenan al clonar ecosistemas)
    base_modules = [
        ("ai-training", "0.1.0", []),
        ("downloads", "0.1.0", []),
        ("reader", "0.1.0", []),
        ("audio", "0.1.0", []),
        ("video", "0.1.0", []),
        ("reverse-engineering", "0.1.0", []),
        ("environments", "0.1.0", []),
    ]

    for name, version, deps in base_modules:
        metadata = ModuleMetadata(
            name=name,
            version=version,
            enabled=config.get("modules", {}).get(name, {}).get("enabled", True),
            dependencies=deps,
        )
        # Instancia placeholder hasta que se implemente el módulo real
        from .engine import ModuleInterface

        class PlaceholderModule(ModuleInterface):
            name = name
            version = version
            dependencies = deps

        engine.register_module(name, PlaceholderModule, metadata)

    if args.list_modules:
        print("\nMódulos registrados:")
        for name in engine.registry.list_modules():
            status = engine.get_module_status(name) or "unregistered"
            print(f"  - {name}: {status}")
        print()
        return 0

    if args.health:
        print("\nEstado de salud del sistema:")
        for name, health in engine.health_check_all().items():
            print(f"  - {name}: {health['status']}")
        print()
        return 0

    # Iniciar todos los módulos
    logger.info("Iniciando cero-X App v%s", config.get("app", {}).get("version", "0.1.0"))
    results = engine.start_all()
    for name, success in results.items():
        logger.info("Módulo %s: %s", name, "OK" if success else "FALLO")

    logger.info("cero-X App en ejecución. Presione Ctrl+C para salir.")
    try:
        while engine.is_running():
            import time

            time.sleep(1)
    except KeyboardInterrupt:
        logger.info("Apagando cero-X App...")
        engine.stop_all()
        logger.info("cero-X App detenido.")

    return 0


if __name__ == "__main__":
    sys.exit(main())