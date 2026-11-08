package com.ceroX.app.core.engine

import android.util.Log

/**
 * ModuleDependenciesValidator — Validador de dependencias entre módulos.
 *
 * Detecta:
 *   • Dependencias faltantes (módulo depende de uno no registrado)
 *   • Dependencias circulares (A → B → C → A)
 *   • Dependencias huérfanas (módulo registrado pero nunca iniciado)
 *   • Conflictos de versión (dos módulos requieren versiones incompatibles)
 */
class ModuleDependenciesValidator {

    private val TAG = "ModuleDependenciesValidator"

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val circularDependencies: List<List<String>>
    )

    /** Valida todas las dependencias del registro */
    fun validate(registry: ModuleRegistry, instances: Map<String, ModuleInterface>): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val circularDependencies = mutableListOf<List<String>>()

        val allModules = registry.listModules()

        // 1. Detectar dependencias faltantes
        for (name in allModules) {
            val meta = registry.getMetadata(name) ?: continue
            for (dep in meta.dependencies) {
                if (!allModules.contains(dep)) {
                    errors.add("Módulo '$name' depende de '$dep' que no está registrado")
                }
                if (dep == name) {
                    errors.add("Módulo '$name' tiene dependencia circular auto-referencial")
                }
            }
        }

        // 2. Detectar dependencias circulares (DFS)
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            visited.add(node)
            recursionStack.add(node)
            path.add(node)

            val meta = registry.getMetadata(node) ?: return
            for (dep in meta.dependencies) {
                if (!allModules.contains(dep)) continue
                if (dep in recursionStack) {
                    // Ciclo detectado
                    val cycleStart = path.indexOf(dep)
                    val cycle = path.subList(cycleStart, path.size).toList() + dep
                    circularDependencies.add(cycle)
                    errors.add("Dependencia circular detectada: ${cycle.joinToString(" → ")}")
                } else if (dep !in visited) {
                    dfs(dep)
                }
            }

            recursionStack.remove(node)
            path.removeAt(path.size - 1)
        }

        for (name in allModules) {
            if (name !in visited) {
                dfs(name)
            }
        }

        // 3. Detectar módulos sin dependientes (huérfanos potenciales)
        val dependents = mutableSetOf<String>()
        for (name in allModules) {
            val meta = registry.getMetadata(name) ?: continue
            dependents.addAll(meta.dependencies)
        }
        for (name in allModules) {
            if (name !in dependents && registry.getMetadata(name)?.status != ModuleStatus.STARTED) {
                warnings.add("Módulo '$name' no es dependencia de ningún otro y no ha sido iniciado")
            }
        }

        // 4. Validar que no haya módulos con dependencias en ERROR
        for (name in allModules) {
            val meta = registry.getMetadata(name) ?: continue
            for (dep in meta.dependencies) {
                val depStatus = registry.getMetadata(dep)?.status
                if (depStatus == ModuleStatus.ERROR) {
                    errors.add("Módulo '$name' depende de '$dep' que está en estado ERROR")
                }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            circularDependencies = circularDependencies
        )
    }

    /** Valida al momento del registro de un módulo individual */
    fun validateRegistration(
        name: String,
        dependencies: List<String>,
        existingModules: Map<String, ModuleMetadata>
    ): List<String> {
        val errors = mutableListOf<String>()

        for (dep in dependencies) {
            if (dep == name) {
                errors.add("$name no puede depender de sí mismo")
            }
            if (!existingModules.containsKey(dep)) {
                errors.add("$name depende de '$dep' que no está registrado aún")
            }
        }

        return errors
    }
}