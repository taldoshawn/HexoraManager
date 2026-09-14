package com.hexora.manager.core.filesystem

import com.hexora.manager.core.model.FileCapability
import com.hexora.manager.core.model.FileSource
import com.hexora.manager.core.model.HexoraFileRef
import com.hexora.manager.core.model.HexoraResult
import java.io.InputStream
import java.io.OutputStream

interface FileAccessProvider {
    val key: String
    val source: FileSource

    suspend fun list(directory: HexoraFileRef): HexoraResult<List<HexoraFileRef>>
    suspend fun stat(ref: HexoraFileRef): HexoraResult<HexoraFileRef>
    suspend fun exists(ref: HexoraFileRef): Boolean
    suspend fun openInput(ref: HexoraFileRef): HexoraResult<InputStream>
    suspend fun openOutput(ref: HexoraFileRef, truncate: Boolean = true): HexoraResult<OutputStream>
    suspend fun createFile(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef>
    suspend fun createDirectory(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef>
    suspend fun rename(ref: HexoraFileRef, newName: String): HexoraResult<HexoraFileRef>
    suspend fun delete(ref: HexoraFileRef): HexoraResult<Unit>
    suspend fun move(ref: HexoraFileRef, destinationDirectory: HexoraFileRef): HexoraResult<HexoraFileRef>
    suspend fun parentOf(ref: HexoraFileRef): HexoraResult<HexoraFileRef?>
    fun supportedCapabilities(ref: HexoraFileRef): Set<FileCapability>
}
