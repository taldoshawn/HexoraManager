package com.hexora.manager

import android.app.Application
import android.os.StrictMode
import com.hexora.manager.core.filesystem.CapabilityManager
import com.hexora.manager.core.filesystem.FileProviderRegistry
import com.hexora.manager.core.filesystem.LocalFileProvider
import com.hexora.manager.core.filesystem.PrivilegedFileProvider
import com.hexora.manager.core.filesystem.SafFileProvider
import com.hexora.manager.core.model.FileSource
import com.hexora.manager.core.operations.OperationEngine
import com.hexora.manager.core.storage.AppSettingsRepository
import com.hexora.manager.core.storage.StorageRepository
import com.hexora.manager.privileged.PrivilegeAccessManager
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
    val privilegeAccess = PrivilegeAccessManager(application)
    val localProvider = LocalFileProvider(application)
    val safProvider = SafFileProvider(application)
    val shizukuProvider = PrivilegedFileProvider(
        key = PrivilegedFileProvider.SHIZUKU_KEY,
        source = FileSource.SHIZUKU,
        service = { privilegeAccess.shizukuService.value },
    )
    val rootProvider = PrivilegedFileProvider(
        key = PrivilegedFileProvider.ROOT_KEY,
        source = FileSource.ROOT,
        service = { privilegeAccess.rootService.value },
    )
    val providers = FileProviderRegistry(localProvider, safProvider, shizukuProvider, rootProvider)
    val capabilityManager = CapabilityManager(application)
    val storageRepository = StorageRepository(application, localProvider)
    val settingsRepository = AppSettingsRepository(application)
    val operationEngine = OperationEngine(providers, CoroutineScope(SupervisorJob() + Dispatchers.IO))
}
