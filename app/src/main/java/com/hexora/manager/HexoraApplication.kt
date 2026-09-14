package com.hexora.manager

import android.app.Application
import android.os.StrictMode
import com.hexora.manager.core.filesystem.CapabilityManager
import com.hexora.manager.core.filesystem.FileProviderRegistry
import com.hexora.manager.core.filesystem.LocalFileProvider
import com.hexora.manager.core.filesystem.SafFileProvider
import com.hexora.manager.core.operations.OperationEngine
import com.hexora.manager.core.storage.StorageRepository
import com.hexora.manager.core.storage.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HexoraApplication : Application() {
    lateinit var services: HexoraServices
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build(),
            )
        }
        services = HexoraServices(this)
    }
}

class HexoraServices(application: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val localProvider = LocalFileProvider(application)
    val safProvider = SafFileProvider(application)
    val providers = FileProviderRegistry(localProvider, safProvider)
    val capabilityManager = CapabilityManager(application)
    val storageRepository = StorageRepository(application, localProvider)
    val settingsRepository = AppSettingsRepository(application)
    val operationEngine = OperationEngine(providers, CoroutineScope(SupervisorJob() + Dispatchers.IO))
}
