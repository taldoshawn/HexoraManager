package com.hexora.manager.core.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.hexora.manager.core.filesystem.LocalFileProvider
import com.hexora.manager.core.model.HexoraFileRef
import com.hexora.manager.core.model.HexoraResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StorageRepository(
    private val context: Context,
    private val localProvider: LocalFileProvider,
) {
    data class StorageVolumeInfo(
        val id: String,
        val name: String,
        val path: String?,
        val isPrimary: Boolean,
        val isRemovable: Boolean,
        val state: String,
        val totalBytes: Long?,
        val freeBytes: Long?,
    ) {
        val usedBytes: Long? = if (totalBytes != null && freeBytes != null) (totalBytes - freeBytes).coerceAtLeast(0) else null
    }

    data class QuickLocation(
        val label: String,
        val path: String,
    )

    suspend fun volumes(): List<StorageVolumeInfo> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(StorageManager::class.java)
        manager.storageVolumes.mapIndexed { index, volume ->
            val dir = volume.directory
            val stats = dir?.takeIf { it.exists() }?.let(::safeStats)
            StorageVolumeInfo(
                id = volume.uuid ?: if (volume.isPrimary) "primary" else "volume-$index",
                name = volume.getDescription(context),
                path = dir?.absolutePath,
                isPrimary = volume.isPrimary,
                isRemovable = volume.isRemovable,
                state = volume.state,
                totalBytes = stats?.first,
                freeBytes = stats?.second,
            )
        }
    }

    fun quickLocations(): List<QuickLocation> {
        val root = Environment.getExternalStorageDirectory()
        fun at(name: String) = File(root, name).absolutePath
        return listOf(
            QuickLocation("Armazenamento interno", root.absolutePath),
            QuickLocation("Downloads", at(Environment.DIRECTORY_DOWNLOADS)),
            QuickLocation("DCIM", at(Environment.DIRECTORY_DCIM)),
            QuickLocation("Camera", at("${Environment.DIRECTORY_DCIM}/Camera")),
            QuickLocation("Pictures", at(Environment.DIRECTORY_PICTURES)),
            QuickLocation("Screenshots", at("${Environment.DIRECTORY_PICTURES}/Screenshots")),
            QuickLocation("Documents", at(Environment.DIRECTORY_DOCUMENTS)),
            QuickLocation("Movies", at(Environment.DIRECTORY_MOVIES)),
            QuickLocation("Music", at(Environment.DIRECTORY_MUSIC)),
            QuickLocation("Podcasts", at(Environment.DIRECTORY_PODCASTS)),
            QuickLocation("Ringtones", at(Environment.DIRECTORY_RINGTONES)),
            QuickLocation("Notifications", at(Environment.DIRECTORY_NOTIFICATIONS)),
        )
    }

    fun resolveLocal(path: String): HexoraResult<HexoraFileRef> = localProvider.fromPath(path)

    private fun safeStats(directory: File): Pair<Long, Long>? = runCatching {
        val stat = StatFs(directory.absolutePath)
        stat.totalBytes to stat.availableBytes
    }.getOrNull()
}
