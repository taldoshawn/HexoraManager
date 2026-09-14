package com.hexora.manager.core.filesystem

import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import com.hexora.manager.core.model.FileCapability
import com.hexora.manager.core.model.FileSource
import com.hexora.manager.core.model.HexoraError
import com.hexora.manager.core.model.HexoraFileRef
import com.hexora.manager.core.model.HexoraResult
import com.hexora.manager.privileged.IPrivilegedFileService
import com.hexora.manager.privileged.PrivilegedRecord
import com.hexora.manager.privileged.PrivilegedRecordCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class PrivilegedFileProvider(
    override val key: String,
    override val source: FileSource,
    private val service: () -> IPrivilegedFileService?,
) : FileAccessProvider {

    suspend fun resolve(path: String): HexoraResult<HexoraFileRef> = io {
        val normalized = normalizeAbsolute(path)
        val remote = requireService()
        toRef(PrivilegedRecordCodec.decode(remote.stat(normalized)))
    }

    override suspend fun list(directory: HexoraFileRef): HexoraResult<List<HexoraFileRef>> = io {
        val remote = requireService()
        remote.list(requirePath(directory)).map { toRef(PrivilegedRecordCodec.decode(it)) }
    }

    override suspend fun stat(ref: HexoraFileRef): HexoraResult<HexoraFileRef> = io {
        val remote = requireService()
        toRef(PrivilegedRecordCodec.decode(remote.stat(requirePath(ref))))
    }

    override suspend fun exists(ref: HexoraFileRef): Boolean = withContext(Dispatchers.IO) {
        val remote = service() ?: return@withContext false
        runCatching { remote.stat(requirePath(ref)); true }.getOrDefault(false)
    }

    override suspend fun openInput(ref: HexoraFileRef): HexoraResult<InputStream> = io {
        val pfd = requireService().openRead(requirePath(ref))
        ParcelFileDescriptor.AutoCloseInputStream(pfd)
    }

    override suspend fun openOutput(ref: HexoraFileRef, truncate: Boolean): HexoraResult<OutputStream> = io {
        val pfd = requireService().openWrite(requirePath(ref), truncate)
        ParcelFileDescriptor.AutoCloseOutputStream(pfd)
    }

    override suspend fun createFile(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef> = io {
        require(FilenameValidator.validate(name)) { "Invalid file name" }
        val path = childPath(parent, name)
        val remote = requireService()
        if (!remote.createFile(path)) error("Could not create file")
        toRef(PrivilegedRecordCodec.decode(remote.stat(path)))
    }

    override suspend fun createDirectory(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef> = io {
        require(FilenameValidator.validate(name)) { "Invalid directory name" }
        val path = childPath(parent, name)
        val remote = requireService()
        if (!remote.createDirectory(path)) error("Could not create directory")
        toRef(PrivilegedRecordCodec.decode(remote.stat(path)))
    }

    override suspend fun rename(ref: HexoraFileRef, newName: String): HexoraResult<HexoraFileRef> = io {
        require(FilenameValidator.validate(newName)) { "Invalid file name" }
        val current = File(requirePath(ref)).toPath().normalize()
        val parent = current.parent ?: error("Cannot rename filesystem root")
        val target = parent.resolve(newName).normalize().toString()
        val remote = requireService()
        if (!remote.rename(current.toString(), target)) error("Could not rename file")
        toRef(PrivilegedRecordCodec.decode(remote.stat(target)))
    }

    override suspend fun delete(ref: HexoraFileRef): HexoraResult<Unit> = io {
        if (!requireService().delete(requirePath(ref), ref.isDirectory)) error("Could not delete file")
        Unit
    }

    override suspend fun move(ref: HexoraFileRef, destinationDirectory: HexoraFileRef): HexoraResult<HexoraFileRef> = io {
        val sourcePath = File(requirePath(ref)).toPath().normalize()
        val target = File(requirePath(destinationDirectory)).toPath().resolve(ref.name).normalize()
        val remote = requireService()
        if (!remote.rename(sourcePath.toString(), target.toString())) {
            return@io throw UnsupportedOperationException("Direct privileged move is not available for this target")
        }
        toRef(PrivilegedRecordCodec.decode(remote.stat(target.toString())))
    }

    override suspend fun parentOf(ref: HexoraFileRef): HexoraResult<HexoraFileRef?> = io {
        val parent = File(requirePath(ref)).toPath().normalize().parent ?: return@io null
        val remote = requireService()
        toRef(PrivilegedRecordCodec.decode(remote.stat(parent.toString())))
    }

    override fun supportedCapabilities(ref: HexoraFileRef): Set<FileCapability> = buildSet {
        add(FileCapability.READ)
        add(FileCapability.HASH)
        if (ref.isDirectory) add(FileCapability.LIST) else add(FileCapability.SHARE)
        add(FileCapability.WRITE)
        add(FileCapability.CREATE)
        add(FileCapability.DELETE)
        add(FileCapability.RENAME)
        add(FileCapability.COPY)
        add(FileCapability.MOVE)
        if (source == FileSource.ROOT) add(FileCapability.ROOT_OPERATION)
    }

    private fun toRef(record: PrivilegedRecord): HexoraFileRef {
        val extension = record.name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }
        val mime = extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        val ref = HexoraFileRef(
            id = "$key:${record.path}",
            name = record.name,
            displayName = record.name,
            path = record.path,
            parentId = File(record.path).toPath().parent?.let { "$key:$it" },
            source = source,
            providerKey = key,
            isDirectory = record.directory,
            isSymlink = record.symlink,
            size = if (record.directory) null else record.size,
            mime = mime,
            extension = extension,
            lastModified = record.modified.takeIf { it > 0 },
        )
        return ref.copy(capabilities = supportedCapabilities(ref))
    }

    private fun childPath(parent: HexoraFileRef, name: String): String =
        File(requirePath(parent)).toPath().resolve(name).normalize().toString()

    private fun requirePath(ref: HexoraFileRef): String = ref.path ?: error("Missing privileged path")

    private fun normalizeAbsolute(raw: String): String {
        val path = File(raw).toPath()
        require(path.isAbsolute) { "Path must be absolute" }
        return path.normalize().toString()
    }

    private fun requireService(): IPrivilegedFileService = service() ?: error("Privileged service is not connected")

    private suspend fun <T> io(block: suspend () -> T): HexoraResult<T> = withContext(Dispatchers.IO) {
        try {
            HexoraResult.Success(block())
        } catch (t: Throwable) {
            HexoraResult.Failure(
                when (t) {
                    is SecurityException -> HexoraError.PermissionDenied(t.message ?: "Privileged access denied")
                    is IllegalArgumentException -> HexoraError.InvalidPath(t.message ?: "Invalid path")
                    is UnsupportedOperationException -> HexoraError.UnsupportedFormat(t.message ?: "Unsupported operation")
                    else -> HexoraError.IoFailure(t.message ?: t.javaClass.simpleName)
                },
            )
        }
    }

    companion object {
        const val SHIZUKU_KEY = "shizuku"
        const val ROOT_KEY = "root"
    }
}
