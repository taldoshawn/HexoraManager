package com.hexora.manager.core.filesystem

import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import com.hexora.manager.core.model.FileCapability
import com.hexora.manager.core.model.FileSource
import com.hexora.manager.core.model.HexoraError
import com.hexora.manager.core.model.HexoraFileRef
import com.hexora.manager.core.model.HexoraResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class LocalFileProvider(private val context: Context) : FileAccessProvider {
    override val key: String = KEY
    override val source: FileSource = FileSource.LOCAL

    override suspend fun list(directory: HexoraFileRef): HexoraResult<List<HexoraFileRef>> = withContext(Dispatchers.IO) {
        val file = resolve(directory) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!file.isDirectory) return@withContext HexoraResult.Failure(HexoraError.NotFound("Not a directory"))
        if (!isAllowed(file, write = false)) return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        runCatching {
            file.listFiles()?.asSequence()?.map(::toRef)?.sortedWith(
                compareByDescending<HexoraFileRef> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName },
            )?.toList() ?: throw SecurityException("Directory listing unavailable")
        }.fold(
            onSuccess = { HexoraResult.Success(it) },
            onFailure = { HexoraResult.Failure(mapError(it)) },
        )
    }

    override suspend fun stat(ref: HexoraFileRef): HexoraResult<HexoraFileRef> = withContext(Dispatchers.IO) {
        val file = resolve(ref) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!file.exists()) return@withContext HexoraResult.Failure(HexoraError.NotFound())
        if (!isAllowed(file, write = false)) return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        HexoraResult.Success(toRef(file))
    }

    override suspend fun exists(ref: HexoraFileRef): Boolean = withContext(Dispatchers.IO) {
        val file = resolve(ref) ?: return@withContext false
        isAllowed(file, write = false) && file.exists()
    }

    override suspend fun openInput(ref: HexoraFileRef): HexoraResult<InputStream> = withContext(Dispatchers.IO) {
        val file = resolve(ref) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!isAllowed(file, write = false)) return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        runCatching<InputStream> { FileInputStream(file) }.fold(
            { HexoraResult.Success(it) },
            { HexoraResult.Failure(mapError(it)) },
        )
    }

    override suspend fun openOutput(ref: HexoraFileRef, truncate: Boolean): HexoraResult<OutputStream> = withContext(Dispatchers.IO) {
        val file = resolve(ref) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!isAllowed(file, write = true)) return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        runCatching<OutputStream> { FileOutputStream(file, !truncate) }.fold(
            { HexoraResult.Success(it) },
            { HexoraResult.Failure(mapError(it)) },
        )
    }

    override suspend fun createFile(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef> = withContext(Dispatchers.IO) {
        if (!FilenameValidator.validate(name)) return@withContext HexoraResult.Failure(HexoraError.InvalidName())
        val parentFile = resolve(parent) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!isAllowed(parentFile, write = true)) return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        val target = File(parentFile, name)
        if (!isAllowed(target, write = true)) return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (target.exists()) return@withContext HexoraResult.Failure(HexoraError.AlreadyExists())
        runCatching {
            if (!target.createNewFile()) throw java.io.IOException("createNewFile returned false")
            toRef(target)
        }.fold({ HexoraResult.Success(it) }, { HexoraResult.Failure(mapError(it)) })
    }

    override suspend fun createDirectory(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef> = withContext(Dispatchers.IO) {
        if (!FilenameValidator.validate(name)) return@withContext HexoraResult.Failure(HexoraError.InvalidName())
        val parentFile = resolve(parent) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!isAllowed(parentFile, write = true)) return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        val target = File(parentFile, name)
        if (!isAllowed(target, write = true)) return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (target.exists()) return@withContext HexoraResult.Failure(HexoraError.AlreadyExists())
        if (target.mkdir()) HexoraResult.Success(toRef(target)) else HexoraResult.Failure(HexoraError.IoFailure("mkdir failed"))
    }

    override suspend fun rename(ref: HexoraFileRef, newName: String): HexoraResult<HexoraFileRef> = withContext(Dispatchers.IO) {
        if (!FilenameValidator.validate(newName)) return@withContext HexoraResult.Failure(HexoraError.InvalidName())
        val sourceFile = resolve(ref) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!isAllowed(sourceFile, write = true)) return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        val parent = sourceFile.parentFile ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        val target = File(parent, newName)
        if (!isAllowed(target, write = true)) return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (target.exists()) return@withContext HexoraResult.Failure(HexoraError.AlreadyExists())
        if (sourceFile.renameTo(target)) HexoraResult.Success(toRef(target)) else HexoraResult.Failure(HexoraError.IoFailure("rename failed"))
    }

    override suspend fun delete(ref: HexoraFileRef): HexoraResult<Unit> = withContext(Dispatchers.IO) {
        val file = resolve(ref) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!isAllowed(file, write = true)) return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        runCatching {
            if (file.isDirectory && file.listFiles()?.isNotEmpty() == true) throw java.io.IOException("Directory not empty")
            if (!file.delete()) throw java.io.IOException("delete failed")
        }.fold({ HexoraResult.Success(Unit) }, { HexoraResult.Failure(mapError(it)) })
    }

    override suspend fun move(ref: HexoraFileRef, destinationDirectory: HexoraFileRef): HexoraResult<HexoraFileRef> = withContext(Dispatchers.IO) {
        val sourceFile = resolve(ref) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        val destination = resolve(destinationDirectory) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!isAllowed(sourceFile, write = true) || !isAllowed(destination, write = true)) {
            return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        }
        val target = File(destination, sourceFile.name)
        if (target.exists()) return@withContext HexoraResult.Failure(HexoraError.AlreadyExists())
        runCatching {
            Files.move(sourceFile.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            toRef(target)
        }.recoverCatching {
            Files.move(sourceFile.toPath(), target.toPath())
            toRef(target)
        }.fold({ HexoraResult.Success(it) }, { HexoraResult.Failure(mapError(it)) })
    }

    override suspend fun parentOf(ref: HexoraFileRef): HexoraResult<HexoraFileRef?> = withContext(Dispatchers.IO) {
        val file = resolve(ref) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        val parent = file.parentFile ?: return@withContext HexoraResult.Success(null)
        if (!isAllowed(parent, write = false)) HexoraResult.Success(null) else HexoraResult.Success(toRef(parent))
    }

    override fun supportedCapabilities(ref: HexoraFileRef): Set<FileCapability> {
        val file = resolve(ref) ?: return emptySet()
        if (!isAllowed(file, write = false)) return emptySet()
        val base = mutableSetOf(FileCapability.READ, FileCapability.SHARE, FileCapability.HASH)
        if (file.isDirectory) base += FileCapability.LIST
        if (isAllowed(file, write = true)) {
            base += setOf(FileCapability.WRITE, FileCapability.RENAME, FileCapability.DELETE, FileCapability.MOVE, FileCapability.COPY)
            if (file.isDirectory) base += FileCapability.CREATE
        }
        return base
    }

    suspend fun safeReplaceText(ref: HexoraFileRef, text: String): HexoraResult<Unit> = withContext(Dispatchers.IO) {
        val target = resolve(ref) ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        if (!isAllowed(target, write = true)) return@withContext HexoraResult.Failure(HexoraError.PermissionDenied())
        val parent = target.parentFile ?: return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        val temp = File(parent, ".${target.name}.hexora-${UUID.randomUUID()}.tmp")
        if (!isAllowed(temp, write = true)) return@withContext HexoraResult.Failure(HexoraError.InvalidPath())
        runCatching {
            FileOutputStream(temp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            runCatching {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.fold(
            { HexoraResult.Success(Unit) },
            {
                temp.delete()
                HexoraResult.Failure(mapError(it))
            },
        )
    }

    fun fromPath(path: String): HexoraResult<HexoraFileRef> {
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return HexoraResult.Failure(HexoraError.InvalidPath())
        if (!isAllowed(file, write = false)) return HexoraResult.Failure(HexoraError.PermissionDenied())
        return HexoraResult.Success(toRef(file))
    }

    private fun toRef(file: File): HexoraFileRef {
        val canonical = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
        val ext = canonical.extension.takeIf { it.isNotBlank() }?.lowercase()
        val mime = ext?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        return HexoraFileRef(
            id = "local:${canonical.path}",
            name = canonical.name.ifBlank { canonical.path },
            displayName = canonical.name.ifBlank { canonical.path },
            path = canonical.path,
            parentId = canonical.parentFile?.let { "local:${it.path}" },
            source = FileSource.LOCAL,
            providerKey = KEY,
            isDirectory = canonical.isDirectory,
            isSymlink = runCatching { Files.isSymbolicLink(canonical.toPath()) }.getOrDefault(false),
            size = if (canonical.isFile) canonical.length() else null,
            mime = mime,
            extension = ext,
            lastModified = canonical.lastModified().takeIf { it > 0 },
        ).copy(capabilities = supportedCapabilitiesInternal(canonical))
    }

    private fun supportedCapabilitiesInternal(file: File): Set<FileCapability> {
        if (!isAllowed(file, write = false)) return emptySet()
        val result = mutableSetOf(FileCapability.READ, FileCapability.SHARE, FileCapability.HASH, FileCapability.COPY)
        if (file.isDirectory) result += FileCapability.LIST
        if (isAllowed(file, write = true)) {
            result += setOf(FileCapability.WRITE, FileCapability.RENAME, FileCapability.DELETE, FileCapability.MOVE)
            if (file.isDirectory) result += FileCapability.CREATE
        }
        return result
    }

    private fun resolve(ref: HexoraFileRef): File? {
        if (ref.source != FileSource.LOCAL || ref.providerKey != KEY) return null
        return ref.path?.let { runCatching { File(it).canonicalFile }.getOrNull() }
    }

    private fun isAllowed(file: File, write: Boolean): Boolean {
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return false
        val appRoots = buildList {
            add(context.filesDir)
            add(context.cacheDir)
            context.getExternalFilesDirs(null).filterNotNull().forEach(::add)
            context.externalCacheDirs.filterNotNull().forEach(::add)
        }.mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        if (appRoots.any { canonical.isWithin(it) }) return if (write) canonical.canWrite() || canonical.parentFile?.canWrite() == true else true

        val shared = runCatching { Environment.getExternalStorageDirectory().canonicalFile }.getOrNull()
        if (shared != null && canonical.isWithin(shared)) {
            if (!Environment.isExternalStorageManager()) return false
            return if (write) canonical.canWrite() || canonical.parentFile?.canWrite() == true else canonical.canRead() || canonical.isDirectory
        }
        return false
    }

    private fun File.isWithin(root: File): Boolean {
        val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
        return path == root.path || path.startsWith(rootPath)
    }

    private fun mapError(throwable: Throwable): HexoraError = when (throwable) {
        is SecurityException -> HexoraError.PermissionDenied(throwable.message)
        is java.io.FileNotFoundException -> HexoraError.NotFound(throwable.message)
        else -> HexoraError.IoFailure(throwable.message)
    }

    companion object {
        const val KEY = "local"
    }
}
