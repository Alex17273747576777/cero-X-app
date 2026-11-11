package com.ceroX.app.modules.houri

import java.util.concurrent.ConcurrentHashMap

/**
 * AchievementsFramework — Logros de lectura.
 * Basado en PineappleTwilight/houri achievements framework.
 * Logros: negatives, backlog, LTR, reading-time, rotating pool, ultimate tier.
 */
class AchievementsFramework {

    private val unlockedAchievements = ConcurrentHashMap<String, AchievementRecord>()
    private val achievements = mutableListOf<AchievementDefinition>()

    init {
        registerDefaultAchievements()
    }

    private fun registerDefaultAchievements() {
        achievements.add(AchievementDefinition("first_chapter", "Primer Capítulo", "Lee tu primer capítulo", "reading-time"))
        achievements.add(AchievementDefinition("backlog_10", "Backlog 10", "Añade 10 series a tu backlog", "backlog"))
        achievements.add(AchievementDefinition("ltr_reader", "Lector LTR", "Lee de izquierda a derecha", "ltr"))
        achievements.add(AchievementDefinition("reading_hour", "Hora de Lectura", "Lee durante 1 hora acumulada", "reading-time"))
        achievements.add(AchievementDefinition("rotating_pool", "Rotating Pool", "Completa el pool rotativo semanal", "rotating-pool"))
        achievements.add(AchievementDefinition("ultimate_tier", "Tier Supremo", "Desbloquea todos los logros", "ultimate"))
    }

    fun unlock(achievementId: String): AchievementResult {
        if (unlockedAchievements.containsKey(achievementId)) {
            return AchievementResult(achievementId, false, "Ya desbloqueado")
        }

        val achievement = achievements.firstOrNull { it.id == achievementId }
        if (achievement == null) {
            return AchievementResult(achievementId, false, "Logro no encontrado")
        }

        val record = AchievementRecord(
            id = achievementId,
            name = achievement.name,
            description = achievement.description,
            category = achievement.category,
            unlockedAt = System.currentTimeMillis()
        )
        unlockedAchievements[achievementId] = record

        return AchievementResult(achievementId, true, "Logro desbloqueado: ${achievement.name}")
    }

    fun isUnlocked(achievementId: String): Boolean = unlockedAchievements.containsKey(achievementId)

    fun listUnlocked(): List<AchievementRecord> = unlockedAchievements.values.toList()

    fun listAll(): List<AchievementDefinition> = achievements.toList()

    /** Progreso registrado de verdad, de 0f a 1f. Solo existe lo que se anoto. */
    private val progreso = mutableMapOf<String, Float>()

    /**
     * Anota el progreso real de un logro.
     *
     * Quien mida la actividad del usuario (capitulos leidos, episodios vistos)
     * llama aqui con lo que conto. Este metodo no deduce nada por su cuenta.
     */
    fun recordProgress(achievementId: String, valor: Float) {
        progreso[achievementId] = valor.coerceIn(0f, 1f)
    }

    /**
     * Devuelve el progreso registrado, o 1f si el logro ya esta desbloqueado.
     *
     * El comentario decía que faltaba calcularlo a partir de la actividad del
     * usuario y mientras tanto devolvía 0f o 1f segun el desbloqueo, que no es
     * progreso: es un booleano con otra forma. Ahora devuelve lo que se anoto con
     * [recordProgress]; si nadie anoto nada, devuelve 0f, que es lo que se sabe.
     */
    fun getProgress(achievementId: String): Float {
        if (isUnlocked(achievementId)) return 1.0f
        return progreso[achievementId] ?: 0.0f
    }
}

data class AchievementDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: String
)

data class AchievementRecord(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val unlockedAt: Long
)

data class AchievementResult(
    val achievementId: String,
    val unlocked: Boolean,
    val message: String
)