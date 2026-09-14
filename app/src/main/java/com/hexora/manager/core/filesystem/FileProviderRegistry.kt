package com.hexora.manager.core.filesystem

import com.hexora.manager.core.model.HexoraFileRef

class FileProviderRegistry(
    val local: LocalFileProvider,
    val saf: SafFileProvider,
) {
    fun forRef(ref: HexoraFileRef): FileAccessProvider? = when (ref.providerKey) {
        LocalFileProvider.KEY -> local
        SafFileProvider.KEY, SafFileProvider.TREE_KEY -> saf
        else -> null
    }
}
