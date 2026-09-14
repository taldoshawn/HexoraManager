package com.hexora.manager.core.filesystem

import android.content.Context
import android.os.Environment
import com.hexora.manager.core.model.FileCapability
import com.hexora.manager.core.model.FileSource
import com.hexora.manager.core.model.HexoraFileRef
import com.hexora.manager.core.model.PrivilegeLevel

class CapabilityManager(private val context: Context) {
    data class Resolution(
        val allowed: Boolean,
        val privilege: PrivilegeLevel,
        val explanation: String,
    )

    fun resolve(ref: HexoraFileRef, capability: FileCapability): Resolution {
        if (capability !in ref.capabilities) {
            return Resolution(false, privilegeFor(ref), "O provider atual não oferece esta capacidade.")
        }
        return Resolution(true, privilegeFor(ref), "Menor nível de privilégio suficiente selecionado.")
    }

    fun privilegeFor(ref: HexoraFileRef): PrivilegeLevel = when (ref.source) {
        FileSource.SAF -> PrivilegeLevel.SAF
        FileSource.MEDIASTORE -> PrivilegeLevel.MEDIASTORE
        FileSource.LOCAL -> if (isSharedStorage(ref) && Environment.isExternalStorageManager()) {
            PrivilegeLevel.ALL_FILES
        } else {
            PrivilegeLevel.NORMAL
        }
        FileSource.SHIZUKU -> PrivilegeLevel.SHIZUKU
        FileSource.ADB -> PrivilegeLevel.ADB_SHELL
        FileSource.ROOT -> PrivilegeLevel.ROOT
        else -> PrivilegeLevel.NORMAL
    }

    fun availableLevels(): Set<PrivilegeLevel> = buildSet {
        add(PrivilegeLevel.NORMAL)
        add(PrivilegeLevel.MEDIASTORE)
        add(PrivilegeLevel.SAF)
        if (Environment.isExternalStorageManager()) add(PrivilegeLevel.ALL_FILES)
        // Shizuku/ADB/ROOT are intentionally absent until their bridges are implemented.
    }

    private fun isSharedStorage(ref: HexoraFileRef): Boolean {
        val path = ref.path ?: return false
        val root = runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrNull() ?: return false
        val canonical = runCatching { java.io.File(path).canonicalPath }.getOrNull() ?: return false
        return canonical == root || canonical.startsWith(root.trimEnd('/') + "/")
    }
}
