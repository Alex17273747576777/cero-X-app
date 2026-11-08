"""Core Engine — Núcleo de cero-X.

Implementa el event bus, registro de módulos y ciclo de vida del sistema.
Arquitectura inspirada en el núcleo de Linux: modular, extensible, comunitario.
"""

from __future__ import annotations

import logging
import threading
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Optional

logger = logging.getLogger("ceroX.core.engine")


@dataclass
class ModuleMetadata:
    """Metadatos de un módulo registrado."""
    name: str
    version: str
    enabled: bool
    dependencies: List[str] = field(default_factory=list)
    status: str = "unloaded"  # unloaded | initialized | started | stopped | error


class EventBus:
    """Event bus de publicación/suscripción para comunicación entre módulos."""

    def __init__(self, max_queue_size: int = 1000):
        self._max_queue_size = max_queue_size
        self._subscribers: Dict[str, List[Callable[[Any], None]]] = defaultdict(list)
        self._lock = threading.RLock()
        self._event_history: List[Dict[str, Any]] = []
        logger.debug("EventBus initialized")

    def subscribe(self, event_type: str, callback: Callable[[Any], None]) -> None:
        """Suscribe un callback a un tipo de evento."""
        with self._lock:
            self._subscribers[event_type].append(callback)
            logger.debug("Subscriber registered for event: %s", event_type)

    def unsubscribe(self, event_type: str, callback: Callable[[Any], None]) -> None:
        """Desuscribe un callback."""
        with self._lock:
            if event_type in self._subscribers:
                self._subscribers[event_type] = [
                    cb for cb in self._subscribers[event_type] if cb != callback
                ]

    def publish(self, event_type: str, data: Any = None) -> None:
        """Publica un evento a todos los suscriptores."""
        event = {"type": event_type, "data": data}
        with self._lock:
            self._event_history.append(event)
            if len(self._event_history) > self._max_queue_size:
                self._event_history.pop(0)
            subscribers = list(self._subscribers.get(event_type, []))

        for callback in subscribers:
            try:
                callback(data)
            except Exception as exc:
                logger.error("Event callback failed for %s: %s", event_type, exc)

    def get_history(self, event_type: Optional[str] = None) -> List[Dict[str, Any]]:
        """Obtiene el historial de eventos, opcionalmente filtrado por tipo."""
        with self._lock:
            if event_type:
                return [e for e in self._event_history if e["type"] == event_type]
            return list(self._event_history)


class ModuleRegistry:
    """Registro de módulos cargados y sus metadatos."""

    def __init__(self):
        self._modules: Dict[str, ModuleMetadata] = {}
        self._instances: Dict[str, Any] = {}
        self._lock = threading.RLock()
        logger.debug("ModuleRegistry initialized")

    def register(self, name: str, metadata: ModuleMetadata) -> None:
        """Registra un módulo en el sistema."""
        with self._lock:
            self._modules[name] = metadata
            logger.info("Module registered: %s v%s", name, metadata.version)

    def get(self, name: str) -> Optional[ModuleMetadata]:
        """Obtiene metadatos de un módulo."""
        with self._lock:
            return self._modules.get(name)

    def list_modules(self) -> List[str]:
        """Lista todos los módulos registrados."""
        with self._lock:
            return list(self._modules.keys())

    def set_instance(self, name: str, instance: Any) -> None:
        """Almacena la instancia activa de un módulo."""
        with self._lock:
            self._instances[name] = instance

    def get_instance(self, name: str) -> Optional[Any]:
        """Obtiene la instancia de un módulo."""
        with self._lock:
            return self._instances.get(name)

    def update_status(self, name: str, status: str) -> None:
        """Actualiza el estado de un módulo."""
        with self._lock:
            if name in self._modules:
                self._modules[name].status = status


class ModuleInterface:
    """Interfaz base que todo módulo de cero-X debe implementar."""

    name: str = "base"
    version: str = "0.0.0"
    dependencies: List[str] = field(default_factory=list)

    def __init__(self, config: Optional[dict] = None):
        self.config = config or {}
        self._initialized = False
        self._started = False

    def initialize(self, config: dict) -> bool:
        """Inicializa el módulo con la configuración dada."""
        self.config = config
        self._initialized = True
        return True

    def start(self) -> bool:
        """Inicia la operación del módulo."""
        if not self._initialized:
            return False
        self._started = True
        return True

    def stop(self) -> bool:
        """Detiene el módulo de forma ordenada."""
        self._started = False
        return True

    def health_check(self) -> dict:
        """Devuelve el estado de salud del módulo."""
        return {
            "name": self.name,
            "version": self.version,
            "initialized": self._initialized,
            "started": self._started,
            "status": "healthy" if self._started else "stopped",
        }

    def get_metadata(self) -> dict:
        """Devuelve metadatos del módulo."""
        return {
            "name": self.name,
            "version": self.version,
            "dependencies": self.dependencies,
        }


class CoreEngine:
    """Núcleo principal de cero-X.

    Gestiona el ciclo de vida de los módulos, el event bus y la configuración.
    """

    def __init__(self, config: Optional[dict] = None):
        self.config = config or {}
        self.event_bus = EventBus(
            max_queue_size=self.config.get("core", {}).get(
                "event_bus", {}
            ).get("max_queue_size", 1000)
        )
        self.registry = ModuleRegistry()
        self._running = False
        self._lock = threading.RLock()
        logger.info("CoreEngine initialized")

    def register_module(
        self, name: str, module_class: type, metadata: ModuleMetadata
    ) -> None:
        """Registra una clase de módulo en el sistema."""
        self.registry.register(name, metadata)
        # Instanciar el módulo
        instance = module_class(config=self.config.get("modules", {}).get(name, {}))
        self.registry.set_instance(name, instance)
        logger.info("Module instance created: %s", name)

    def initialize_module(self, name: str) -> bool:
        """Inicializa un módulo registrado."""
        instance = self.registry.get_instance(name)
        if instance is None:
            logger.error("Module not registered: %s", name)
            return False

        try:
            meta = self.registry.get(name)
            if meta and meta.dependencies:
                for dep in meta.dependencies:
                    dep_meta = self.registry.get(dep)
                    if dep_meta and dep_meta.status != "initialized":
                        logger.warning(
                            "Dependency %s not initialized for module %s", dep, name
                        )

            config = self.config.get("modules", {}).get(name, {})
            success = instance.initialize(config)
            if success:
                self.registry.update_status(name, "initialized")
                self.event_bus.publish("module.initialized", {"name": name})
                logger.info("Module initialized: %s", name)
            return success
        except Exception as exc:
            logger.exception("Failed to initialize module %s: %s", name, exc)
            self.registry.update_status(name, "error")
            return False

    def start_module(self, name: str) -> bool:
        """Inicia un módulo inicializado."""
        instance = self.registry.get_instance(name)
        if instance is None:
            return False

        meta = self.registry.get(name)
        if meta is None or meta.status != "initialized":
            logger.error("Module not initialized: %s", name)
            return False

        try:
            success = instance.start()
            if success:
                self.registry.update_status(name, "started")
                self.event_bus.publish("module.started", {"name": name})
                logger.info("Module started: %s", name)
            return success
        except Exception as exc:
            logger.exception("Failed to start module %s: %s", name, exc)
            self.registry.update_status(name, "error")
            return False

    def stop_module(self, name: str) -> bool:
        """Detiene un módulo en ejecución."""
        instance = self.registry.get_instance(name)
        if instance is None:
            return False

        try:
            success = instance.stop()
            if success:
                self.registry.update_status(name, "stopped")
                self.event_bus.publish("module.stopped", {"name": name})
                logger.info("Module stopped: %s", name)
            return success
        except Exception as exc:
            logger.exception("Failed to stop module %s: %s", name, exc)
            return False

    def start_all(self) -> Dict[str, bool]:
        """Inicia todos los módulos registrados en orden de dependencias."""
        results: Dict[str, bool] = {}
        for name in self.registry.list_modules():
            results[name] = self.start_module(name)
        self._running = True
        self.event_bus.publish("system.started", results)
        return results

    def stop_all(self) -> Dict[str, bool]:
        """Detiene todos los módulos en ejecución."""
        results: Dict[str, bool] = {}
        for name in reversed(self.registry.list_modules()):
            results[name] = self.stop_module(name)
        self._running = False
        self.event_bus.publish("system.stopped", results)
        return results

    def health_check_all(self) -> Dict[str, dict]:
        """Devuelve el estado de salud de todos los módulos."""
        return {
            name: self.registry.get_instance(name).health_check()
            for name in self.registry.list_modules()
            if self.registry.get_instance(name) is not None
        }

    def is_running(self) -> bool:
        """Indica si el sistema está en ejecución."""
        return self._running

    def get_module_status(self, name: str) -> Optional[str]:
        """Obtiene el estado actual de un módulo."""
        meta = self.registry.get(name)
        return meta.status if meta else None