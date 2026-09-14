package com.hexora.manager.core.filesystem

import com.hexora.manager.core.model.HexoraFileRef

class FileProviderRegistry(
    val local: LocalFileProvider,
    val saf: SafFileProvider,
    val shizuku: PrivilegedFileProvider,
    val root: PrivilegedFileProvider,
) {
    fun forRef(ref: HexoraFileRef): FileAccessProvider? = when (ref.providerKey) {
        LocalFileProvider.KEY -> local
        SafFileProvider.KEY, SafFileProvider.TREE_KEY -> saf
        PrivilegedFileProvider.SHIZUKU_KEY -> shizuku
        PrivilegedFileProvider.ROOT_KEY -> root
        else -> null
    }
}
