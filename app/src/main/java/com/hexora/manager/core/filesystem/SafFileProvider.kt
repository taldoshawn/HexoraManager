package com.hexora.manager.core.filesystem

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.hexora.manager.core.model.FileCapability
import com.hexora.manager.core.model.FileSource
import com.hexora.manager.core.model.HexoraError
import com.hexora.manager.core.model.HexoraFileRef
import com.hexora.manager.core.model.HexoraResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class SafFileProvider(private val context: Context) : FileAccessProvider {
    override val key: String = KEY
    override val source: FileSource = FileSource.SAF

    override suspend fun list(directory: HexoraFileRef): HexoraResult<List<HexoraFileRef>> = withContext(Dispatchers.IO) {
        val doc = document(directory) ?: return@withContext HexoraResult.Failure(HexoraError.NotFound())
        if (!doc.isDirectory) return@withContext HexoraResult.Failure(HexoraError.NotFound("Not a directory"))
        runCatching {
            doc.listFiles().map { child -> toRef(child, directory.id, isTreeRoot = false) }
                .sortedWith(compareByDescending<HexoraFileRef> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        }.fold({ HexoraResult.Success(it) }, { HexoraResult.Failure(mapError(it)) })
    }

    override suspend fun stat(ref: HexoraFileRef): HexoraResult<HexoraFileRef> = withContext(Dispatchers.IO) {
        val doc = document(ref) ?: return@withContext HexoraResult.Failure(HexoraError.NotFound())
        HexoraResult.Success(toRef(doc, ref.parentId, ref.providerKey == TREE_KEY))
    }

    override suspend fun exists(ref: HexoraFileRef): Boolean = withContext(Dispatchers.IO) { document(ref)?.exists() == true }

    override suspend fun openInput(ref: HexoraFileRef): HexoraResult<InputStream> = withContext(Dispatchers.IO) {
        val uri = ref.parsedUri() ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        runCatching { context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString()) }
            .fold({ HexoraResult.Success(it) }, { HexoraResult.Failure(mapError(it)) })
    }

    override suspend fun openOutput(ref: HexoraFileRef, truncate: Boolean): HexoraResult<OutputStream> = withContext(Dispatchers.IO) {
        val uri = ref.parsedUri() ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        val mode = if (truncate) "wt" else "wa"
        runCatching { context.contentResolver.openOutputStream(uri, mode) ?: throw java.io.FileNotFoundException(uri.toString()) }
            .fold({ HexoraResult.Success(it) }, { HexoraResult.Failure(mapError(it)) })
    }

    override suspend fun createFile(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef> = withContext(Dispatchers.IO) {
        if (!FilenameValidator.validate(name)) return@withContext HexoraResult.Failure(HexoraError.InvalidName())
        val doc = document(parent) ?: return@withContext HexoraResult.Failure(HexoraError.NotFound())
        runCatching {
            doc.createFile("application/octet-stream", name) ?: throw java.io.IOException("createFile failed")
        }.fold(
            { HexoraResult.Success(toRef(it, parent.id, false)) },
            { HexoraResult.Failure(mapError(it)) },
        )
    }

    override suspend fun createDirectory(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef> = withContext(Dispatchers.IO) {
        if (!FilenameValidator.validate(name)) return@withContext HexoraResult.Failure(HexoraError.InvalidName())
        val doc = document(parent) ?: return@withContext HexoraResult.Failure(HexoraError.NotFound())
        runCatching { doc.createDirectory(name) ?: throw java.io.IOException("createDirectory failed") }
            .fold({ HexoraResult.Success(toRef(it, parent.id, false)) }, { HexoraResult.Failure(mapError(it)) })
    }

    override suspend fun rename(ref: HexoraFileRef, newName: String): HexoraResult<HexoraFileRef> = withContext(Dispatchers.IO) {
        if (!FilenameValidator.validate(newName)) return@withContext HexoraResult.Failure(HexoraError.InvalidName())
        val doc = document(ref) ?: return@withContext HexoraResult.Failure(HexoraError.NotFound())
        if (!doc.renameTo(newName)) return@withContext HexoraResult.Failure(HexoraError.IoFailure("renameTo failed"))
        HexoraResult.Success(toRef(doc, ref.parentId, false))
    }

    override suspend fun delete(ref: HexoraFileRef): HexoraResult<Unit> = withContext(Dispatchers.IO) {
        val doc = document(ref) ?: return@withContext HexoraResult.Failure(HexoraError.NotFound())
        if (doc.delete()) HexoraResult.Success(Unit) else HexoraResult.Failure(HexoraError.IoFailure("delete failed"))
    }

    override suspend fun move(ref: HexoraFileRef, destinationDirectory: HexoraFileRef): HexoraResult<HexoraFileRef> =
        HexoraResult.Failure(HexoraError.UnsupportedFormat("SAF atomic move is provider-specific; OperationEngine will copy then delete."))

    override suspend fun parentOf(ref: HexoraFileRef): HexoraResult<HexoraFileRef?> = withContext(Dispatchers.IO) {
        ref.parentId ?: return@withContext HexoraResult.Success(null)
        HexoraResult.Success(null)
    }

    override fun supportedCapabilities(ref: HexoraFileRef): Set<FileCapability> = buildSet {
        add(FileCapability.READ)
        add(FileCapability.SHARE)
        add(FileCapability.HASH)
        add(FileCapability.COPY)
        if (ref.isDirectory) add(FileCapability.LIST)
        val doc = document(ref)
        if (doc?.canWrite() == true) {
            add(FileCapability.WRITE)
            add(FileCapability.RENAME)
            add(FileCapability.DELETE)
            add(FileCapability.MOVE)
            if (ref.isDirectory) add(FileCapability.CREATE)
        }
    }

    fun treeRoot(uri: Uri): HexoraResult<HexoraFileRef> {
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return HexoraResult.Failure(HexoraError.InvalidPath())
        return HexoraResult.Success(toRef(doc, parentId = null, isTreeRoot = true))
    }

    private fun document(ref: HexoraFileRef): DocumentFile? {
        if (ref.source != FileSource.SAF) return null
        val uri = ref.parsedUri() ?: return null
        return if (ref.providerKey == TREE_KEY) DocumentFile.fromTreeUri(context, uri) else DocumentFile.fromSingleUri(context, uri)
    }

    private fun toRef(doc: DocumentFile, parentId: String?, isTreeRoot: Boolean): HexoraFileRef {
        val name = doc.name ?: "Documento"
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.lowercase()
        val base = HexoraFileRef(
            id = "saf:${doc.uri}",
            name = name,
            displayName = name,
            uri = doc.uri.toString(),
            parentId = parentId,
            source = FileSource.SAF,
            providerKey = if (isTreeRoot) TREE_KEY else KEY,
            isDirectory = doc.isDirectory,
            size = if (doc.isFile) doc.length().takeIf { it >= 0 } else null,
            mime = doc.type,
            extension = extension,
            lastModified = doc.lastModified().takeIf { it > 0 },
        )
        return base.copy(capabilities = supportedCapabilities(base))
    }

    private fun mapError(error: Throwable): HexoraError = when (error) {
        is SecurityException -> HexoraError.PermissionDenied(error.message)
        is java.io.FileNotFoundException -> HexoraError.NotFound(error.message)
        else -> HexoraError.IoFailure(error.message)
    }

    companion object {
        const val KEY = "saf"
        const val TREE_KEY = "saf-tree"
    }
}
