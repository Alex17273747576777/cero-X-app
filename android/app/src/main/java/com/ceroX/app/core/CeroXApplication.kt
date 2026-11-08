package com.ceroX.app.core

import com.ceroX.app.core.engine.CoreEngine
import com.ceroX.app.BuildConfig

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import android.util.Log
import com.ceroX.app.core.engine.ModuleLifecycleManager
import com.ceroX.app.core.engine.ModuleMetadata
import com.ceroX.app.core.engine.ModuleStatus
import com.ceroX.app.modules.ai.NeuralAIModule
import com.ceroX.app.modules.ai_training.AiTrainingModule
import com.ceroX.app.modules.aihub.AIHubModule
import com.ceroX.app.modules.audio.AudioModule
import com.ceroX.app.modules.backup.BackupModule
import com.ceroX.app.modules.booru.BooruModule
import com.ceroX.app.modules.catalog.CatalogModule
import com.ceroX.app.modules.crossplatform.CrossPlatformModule
import com.ceroX.app.modules.comic.ComicModule
import com.ceroX.app.modules.downloads.DownloadsModule
import com.ceroX.app.modules.environments.EnvironmentsModule
import com.ceroX.app.modules.extension_factory.ExtensionFactoryModule
import com.ceroX.app.modules.extension_sandbox.ExtensionSandboxModule
import com.ceroX.app.modules.houri.HoubiModule
import com.ceroX.app.modules.kai.KaiModule
import com.ceroX.app.modules.kisara.KisaraLibraryModule
import com.ceroX.app.modules.kisara.KisaraMigrationModule
import com.ceroX.app.modules.kisara.KisaraModule
import com.ceroX.app.modules.kisara.KisaraNeuralColorizerModule
import com.ceroX.app.modules.kisara.KisaraOcrTranslatorModule
import com.ceroX.app.modules.kisara.KisaraReaderEngineModule
import com.ceroX.app.modules.kisara.KisaraSyncModule
import com.ceroX.app.modules.kisara.KisaraThemeModule
import com.ceroX.app.modules.media.MediaModule
import com.ceroX.app.modules.mode.ModeManagerModule
import com.ceroX.app.modules.novel.NovelModule
import com.ceroX.app.modules.prisma.PrismaModule
import com.ceroX.app.modules.reader.UnifiedReaderModule
import com.ceroX.app.modules.reverse_engineering.DeepReverseEngineeringModule
import com.ceroX.app.modules.repoforge.RepoForgeSourceModule
import com.ceroX.app.modules.server.ServerModule
import com.ceroX.app.modules.sync.SyncModule
import com.ceroX.app.modules.theme.ThemeEngineModule
import com.ceroX.app.modules.tools.ToolsModule
import com.ceroX.app.modules.updates.UpdatesModule
import com.ceroX.app.modules.video.VideoModule

class CeroXApplication : Application() {

    private val TAG = "CeroXApp"

    lateinit var lifecycleManager: ModuleLifecycleManager
    lateinit var crashHandler: CrashHandler
    lateinit var memoryMonitor: MemoryMonitor

    internal var coreEngineLegacy: CoreEngine? = null

    override fun onCreate() {
        super.onCreate()

        // StrictMode solo en debug
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        crashHandler = CrashHandler(applicationContext)
        crashHandler.install()

        lifecycleManager = ModuleLifecycleManager(applicationContext)

        registerModules()
        validateDependencies()

        val initResults = lifecycleManager.initializeAll()
        logInitResults(initResults)

        val startResults = lifecycleManager.startAll()
        logStartResults(startResults)

        memoryMonitor = MemoryMonitor(applicationContext, lifecycleManager)
        memoryMonitor.start()

        registerActivityLifecycleCallbacks(lifecycleCallbacks)

        Log.i(TAG, "cero-X App v1.0.0-beta-kisara-universal initialized — 30 modules fused")
        Log.i(TAG, "Houbi (PineappleTwilight/houri) = Lector Base Principal")
        Log.i(TAG, "Kisara (ifritraen/kisara) = Lector Manga con ONNX OCR + Neural Colorizer")
        Log.i(TAG, "APK Universal: armeabi-v7a, arm64-v8a, x86, x86_64")
        Log.i(TAG, "CrashHandler + MemoryMonitor + ModuleLifecycleManager activos")
        Log.i(TAG, "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    }

    override fun onTerminate() {
        super.onTerminate()
        memoryMonitor.stop()
        lifecycleManager.onDestroy()
        crashHandler.uninstall()
        Log.i(TAG, "cero-X App terminated")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        lifecycleManager.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        lifecycleManager.onTrimMemory(level)
    }

    private val lifecycleCallbacks = object : android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
        override fun onActivityStarted(activity: android.app.Activity) {}
        override fun onActivityResumed(activity: android.app.Activity) {}
        override fun onActivityPaused(activity: android.app.Activity) {}
        override fun onActivityStopped(activity: android.app.Activity) {}
        // En API 35+ el parámetro outState no es nulo: la firma cambió.
        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
        override fun onActivityDestroyed(activity: android.app.Activity) {}
    }

    private fun registerModules() {
        val modules = listOf(
            ModuleMetadata("houbi-reader", "1.22.17-fusion", emptyList()),
            ModuleMetadata("extension-sandbox", "1.0.0-beta", emptyList()),
            ModuleMetadata("extension-factory", "1.0.0-beta", emptyList()),
            ModuleMetadata("app-catalog", "1.0.0-beta", emptyList()),
            ModuleMetadata("novel-reader", "1.0.0-beta", emptyList()),
            ModuleMetadata("comic-reader", "1.0.0-beta", emptyList()),
            ModuleMetadata("media-server", "1.0.0-beta", emptyList()),
            ModuleMetadata("download-tools", "1.0.0-beta", emptyList()),
            ModuleMetadata("booru-viewer", "1.0.0-beta", emptyList()),
            ModuleMetadata("prisma-client", "4.2.1-fusion", emptyList()),
            ModuleMetadata("ai-hub", "3.0.1-fusion", emptyList()),
            ModuleMetadata("kai-assistant", "1.0.0-fusion", emptyList()),
            ModuleMetadata("kisara-reader", "2.3.9-kisara-stable", emptyList()),
            ModuleMetadata("kisara-reader-engine", "2.3.9-kisara-stable", listOf("kisara-reader")),
            ModuleMetadata("kisara-library", "2.3.9-kisara-stable", listOf("kisara-reader")),
            ModuleMetadata("kisara-sync", "2.3.9-kisara-stable", listOf("kisara-library")),
            ModuleMetadata("kisara-theme", "2.3.9-kisara-stable", emptyList()),
            ModuleMetadata("kisara-migration", "2.3.9-kisara-stable", listOf("kisara-library", "kisara-sync")),
            ModuleMetadata("kisara-ocr-translator", "1.0.0-kisara", listOf("kisara-reader")),
            ModuleMetadata("kisara-neural-colorizer", "1.0.0-kisara", listOf("kisara-reader")),
            ModuleMetadata("theme-engine", "1.0.0-beta", emptyList()),
            ModuleMetadata("mode-manager", "1.0.0-beta", emptyList()),
            ModuleMetadata("cross-platform", "1.0.0-beta", emptyList()),
            ModuleMetadata("unified-reader", "0.1.0-fusion", emptyList()),
            ModuleMetadata("repoforge-sources", "0.1.0-fusion", emptyList()),
            ModuleMetadata("sync-trackers", "0.1.0-fusion", emptyList()),
            ModuleMetadata("backup-restore", "0.1.0-fusion", emptyList()),
            ModuleMetadata("neural-ai", "0.1.0-fusion", emptyList()),
            ModuleMetadata("media-player", "0.1.0-fusion", emptyList()),
            ModuleMetadata("updates-channel", "0.1.0-fusion", emptyList()),
            ModuleMetadata("deep-reverse-engineering", "0.1.0-fusion", emptyList()),
            ModuleMetadata("downloads", "0.1.0-fusion", emptyList()),
            ModuleMetadata("environments", "0.1.0-fusion", emptyList()),
            ModuleMetadata("ai-training", "0.1.0", emptyList()),
            ModuleMetadata("video", "0.1.0", emptyList()),
            ModuleMetadata("audio", "0.1.0", emptyList()),
        )

        val moduleClasses: Map<String, Class<out com.ceroX.app.core.engine.ModuleInterface>> = mapOf(
            "houbi-reader" to HoubiModule::class.java,
            "extension-sandbox" to ExtensionSandboxModule::class.java,
            "extension-factory" to ExtensionFactoryModule::class.java,
            "app-catalog" to CatalogModule::class.java,
            "novel-reader" to NovelModule::class.java,
            "comic-reader" to ComicModule::class.java,
            "media-server" to ServerModule::class.java,
            "download-tools" to ToolsModule::class.java,
            "booru-viewer" to BooruModule::class.java,
            "prisma-client" to PrismaModule::class.java,
            "ai-hub" to AIHubModule::class.java,
            "kai-assistant" to KaiModule::class.java,
            "kisara-reader" to KisaraModule::class.java,
            "kisara-reader-engine" to KisaraReaderEngineModule::class.java,
            "kisara-library" to KisaraLibraryModule::class.java,
            "kisara-sync" to KisaraSyncModule::class.java,
            "kisara-theme" to KisaraThemeModule::class.java,
            "kisara-migration" to KisaraMigrationModule::class.java,
            "kisara-ocr-translator" to KisaraOcrTranslatorModule::class.java,
            "kisara-neural-colorizer" to KisaraNeuralColorizerModule::class.java,
            "theme-engine" to ThemeEngineModule::class.java,
            "mode-manager" to ModeManagerModule::class.java,
            "cross-platform" to CrossPlatformModule::class.java,
            "unified-reader" to UnifiedReaderModule::class.java,
            "repoforge-sources" to RepoForgeSourceModule::class.java,
            "sync-trackers" to SyncModule::class.java,
            "backup-restore" to BackupModule::class.java,
            "neural-ai" to NeuralAIModule::class.java,
            "media-player" to MediaModule::class.java,
            "updates-channel" to UpdatesModule::class.java,
            "deep-reverse-engineering" to DeepReverseEngineeringModule::class.java,
            "downloads" to com.ceroX.app.modules.downloads.DownloadsModule::class.java,
            "environments" to com.ceroX.app.modules.environments.EnvironmentsModule::class.java,
            "ai-training" to com.ceroX.app.modules.ai_training.AiTrainingModule::class.java,
            "video" to com.ceroX.app.modules.video.VideoModule::class.java,
            "audio" to com.ceroX.app.modules.audio.AudioModule::class.java,
        )

        for (meta in modules) {
            val clazz = moduleClasses[meta.name] ?: continue
            lifecycleManager.registerModule(meta.name, clazz, meta)
        }
    }

    private fun validateDependencies() {
        val validator = com.ceroX.app.core.engine.ModuleDependenciesValidator()
        val metas = lifecycleManager.getRegistry().listModules().associateWith { name ->
            lifecycleManager.getRegistry().getMetadata(name)
        }.filterValues { it != null } as Map<String, com.ceroX.app.core.engine.ModuleMetadata>
        val instances = metas.keys.associateWith { name ->
            lifecycleManager.getModuleInstance(name)
        }.filterValues { it != null } as Map<String, com.ceroX.app.core.engine.ModuleInterface>

        val result = validator.validate(lifecycleManager.getRegistry(), instances)

        if (result.isValid) {
            Log.i(TAG, "Validación de dependencias: OK (${result.warnings.size} advertencias)")
        } else {
            Log.e(TAG, "VALIDACIÓN DE DEPENDENCIAS FALLÓ:")
            for (error in result.errors) {
                Log.e(TAG, "  ERROR: $error")
            }
        }
    }

    private fun logInitResults(results: Map<String, Boolean>) {
        val successCount = results.count { it.value }
        val failCount = results.count { !it.value }
        Log.i(TAG, "Inicialización: $successCount exitosos, $failCount fallidos")
    }

    private fun logStartResults(results: Map<String, Boolean>) {
        val successCount = results.count { it.value }
        val failCount = results.count { !it.value }
        Log.i(TAG, "Inicio: $successCount exitosos, $failCount fallidos")
    }

    fun getModuleStatus(name: String): ModuleStatus? = lifecycleManager.getStatus(name)
    fun isModuleStarted(name: String): Boolean = lifecycleManager.isModuleStarted(name)
    fun getModuleInstance(name: String): com.ceroX.app.core.engine.ModuleInterface? =
        lifecycleManager.getModuleInstance(name)
    fun getAllHealth(): Map<String, com.ceroX.app.core.engine.ModuleHealth> =
        lifecycleManager.getAllHealth()

    val coreEngine: CoreEngine
        get() {
            if (coreEngineLegacy == null) {
                coreEngineLegacy = CoreEngine(applicationContext)
            }
            return coreEngineLegacy!!
        }
}