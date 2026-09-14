package com.hexora.manager.core.operations

import com.hexora.manager.core.filesystem.FileAccessProvider
import com.hexora.manager.core.filesystem.FileProviderRegistry
import com.hexora.manager.core.filesystem.FilenameValidator
import com.hexora.manager.core.model.HexoraError
import com.hexora.manager.core.model.HexoraFileRef
import com.hexora.manager.core.model.HexoraResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class OperationEngine(
    private val registry: FileProviderRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _operations = MutableStateFlow<List<FileOperation>>(emptyList())
    val operations: StateFlow<List<FileOperation>> = _operations.asStateFlow()
    private val jobs = ConcurrentHashMap<String, Job>()

    fun copy(
        sources: List<HexoraFileRef>,
        destinationDirectory: HexoraFileRef,
        conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
        deleteSourceAfterCopy: Boolean = false,
    ): String {
        val op = FileOperation(
            type = if (deleteSourceAfterCopy) FileOperationType.MOVE else FileOperationType.COPY,
            sources = sources,
            destination = destinationDirectory,
            filesTotal = sources.size,
            bytesTotal = sources.sumOf { it.size ?: 0L }.takeIf { it > 0 },
        )
        add(op)
        jobs[op.operationId] = scope.launch {
            try {
                executeCopy(op, conflictPolicy, deleteSourceAfterCopy)
            } finally {
                jobs.remove(op.operationId)
            }
        }
        return op.operationId
    }

    fun delete(sources: List<HexoraFileRef>): String {
        val op = FileOperation(type = FileOperationType.DELETE, sources = sources, filesTotal = sources.size)
        add(op)
        jobs[op.operationId] = scope.launch {
            try {
                update(op.operationId) { it.copy(state = FileOperationState.RUNNING) }
                sources.forEachIndexed { index, ref ->
                    val provider = registry.forRef(ref) ?: fail(HexoraError.UnsupportedFormat("Unknown provider"))
                    deleteRecursive(provider, ref)
                    update(op.operationId) {
                        it.copy(
                            filesProcessed = index + 1,
                            progress = (index + 1f) / max(1, sources.size),
                        )
                    }
                }
                update(op.operationId) { it.copy(state = FileOperationState.COMPLETED, progress = 1f) }
            } catch (_: CancellationException) {
                update(op.operationId) {
                    it.copy(state = FileOperationState.CANCELLED, error = HexoraError.OperationCancelled())
                }
            } catch (e: OperationFailure) {
                update(op.operationId) { it.copy(state = FileOperationState.FAILED, error = e.error) }
            } finally {
                jobs.remove(op.operationId)
            }
        }
        return op.operationId
    }

    fun cancel(operationId: String) {
        jobs[operationId]?.cancel()
    }

    suspend fun rename(ref: HexoraFileRef, newName: String): HexoraResult<HexoraFileRef> {
        if (!FilenameValidator.validate(newName)) return HexoraResult.Failure(HexoraError.InvalidName())
        val provider = registry.forRef(ref)
            ?: return HexoraResult.Failure(HexoraError.UnsupportedFormat("Unknown provider"))
        return provider.rename(ref, newName)
    }

    suspend fun createFile(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef> {
        if (!FilenameValidator.validate(name)) return HexoraResult.Failure(HexoraError.InvalidName())
        val provider = registry.forRef(parent)
            ?: return HexoraResult.Failure(HexoraError.UnsupportedFormat("Unknown provider"))
        return provider.createFile(parent, name)
    }

    suspend fun createDirectory(parent: HexoraFileRef, name: String): HexoraResult<HexoraFileRef> {
        if (!FilenameValidator.validate(name)) return HexoraResult.Failure(HexoraError.InvalidName())
        val provider = registry.forRef(parent)
            ?: return HexoraResult.Failure(HexoraError.UnsupportedFormat("Unknown provider"))
        return provider.createDirectory(parent, name)
    }

    suspend fun sha256(ref: HexoraFileRef): HexoraResult<String> = withContext(Dispatchers.IO) {
        if (ref.isDirectory) {
            return@withContext HexoraResult.Failure(HexoraError.UnsupportedFormat("Hash de diretórios não é suportado."))
        }
        val provider = registry.forRef(ref)
            ?: return@withContext HexoraResult.Failure(HexoraError.UnsupportedFormat("Unknown provider"))
        val input = when (val result = provider.openInput(ref)) {
            is HexoraResult.Success -> result.value
            is HexoraResult.Failure -> return@withContext result
        }
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { stream ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.fold(
            { HexoraResult.Success(it) },
            { HexoraResult.Failure(HexoraError.IoFailure(it.message)) },
        )
    }

    private suspend fun executeCopy(
        op: FileOperation,
        policy: ConflictPolicy,
        deleteAfter: Boolean,
    ) {
        update(op.operationId) { it.copy(state = FileOperationState.RUNNING) }
        var bytesProcessed = 0L
        val started = System.nanoTime()
        try {
            val destination = op.destination ?: fail(HexoraError.InvalidPath("Destination missing"))
            val destProvider = registry.forRef(destination)
                ?: fail(HexoraError.UnsupportedFormat("Unknown destination provider"))

            op.sources.forEachIndexed { index, source ->
                val sourceProvider = registry.forRef(source)
                    ?: fail(HexoraError.UnsupportedFormat("Unknown source provider"))
                val canTryProviderMove = deleteAfter &&
                    sourceProvider.key == destProvider.key &&
                    !destinationContainsName(destProvider, destination, source.name)

                var movedDirectly = false
                if (canTryProviderMove) {
                    when (val moveResult = sourceProvider.move(source, destination)) {
                        is HexoraResult.Success -> movedDirectly = true
                        is HexoraResult.Failure -> {
                            if (moveResult.error !is HexoraError.UnsupportedFormat) fail(moveResult.error)
                        }
                    }
                }

                if (!movedDirectly) {
                    val outcome = copyRecursive(source, destination, policy) { delta ->
                        bytesProcessed += delta
                        val elapsedSeconds = ((System.nanoTime() - started) / 1_000_000_000.0).coerceAtLeast(0.001)
                        val speed = (bytesProcessed / elapsedSeconds).toLong()
                        val total = op.bytesTotal
                        val eta = if (total != null && speed > 0) {
                            ((total - bytesProcessed).coerceAtLeast(0) * 1000 / speed)
                        } else {
                            null
                        }
                        update(op.operationId) {
                            it.copy(
                                bytesProcessed = bytesProcessed,
                                speedBytesPerSecond = speed,
                                etaMillis = eta,
                                progress = total?.let { totalBytes ->
                                    (bytesProcessed.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
                                } ?: (index.toFloat() / max(1, op.sources.size)),
                            )
                        }
                    }

                    if (deleteAfter && outcome.complete) {
                        deleteRecursive(sourceProvider, source)
                    }
                }

                update(op.operationId) {
                    it.copy(
                        filesProcessed = index + 1,
                        progress = if (movedDirectly) {
                            (index + 1f) / max(1, op.sources.size)
                        } else {
                            it.progress
                        },
                    )
                }
            }
            update(op.operationId) { it.copy(state = FileOperationState.COMPLETED, progress = 1f) }
        } catch (_: CancellationException) {
            update(op.operationId) {
                it.copy(state = FileOperationState.CANCELLED, error = HexoraError.OperationCancelled())
            }
        } catch (e: OperationFailure) {
            update(op.operationId) { it.copy(state = FileOperationState.FAILED, error = e.error) }
        }
    }

    private suspend fun copyRecursive(
        source: HexoraFileRef,
        destinationDirectory: HexoraFileRef,
        policy: ConflictPolicy,
        onBytes: (Long) -> Unit,
    ): CopyOutcome {
        val sourceProvider = registry.forRef(source)
            ?: fail(HexoraError.UnsupportedFormat("Unknown source provider"))
        val destinationProvider = registry.forRef(destinationDirectory)
            ?: fail(HexoraError.UnsupportedFormat("Unknown destination provider"))

        val finalName = chooseTargetName(destinationProvider, destinationDirectory, source.name, policy)
            ?: return CopyOutcome(target = null, complete = false)

        if (source.isDirectory) {
            val created = destinationProvider.createDirectory(destinationDirectory, finalName).unwrap()
            return try {
                var complete = true
                val children = sourceProvider.list(source).unwrap()
                children.forEach { child ->
                    val childResult = copyRecursive(child, created, policy, onBytes)
                    if (!childResult.complete) complete = false
                }
                CopyOutcome(target = created, complete = complete)
            } catch (t: Throwable) {
                cleanupBestEffort(destinationProvider, created)
                throw t
            }
        }

        val target = destinationProvider.createFile(destinationDirectory, finalName).unwrap()
        var bytesWritten = 0L
        try {
            val input = sourceProvider.openInput(source).unwrap()
            val output = destinationProvider.openOutput(target, truncate = true).unwrap()
            input.use { src ->
                output.use { dst ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        val read = src.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        dst.write(buffer, 0, read)
                        bytesWritten += read
                        onBytes(read.toLong())
                    }
                    dst.flush()
                }
            }
            verifyCopiedFile(source, target, destinationProvider, bytesWritten)
            return CopyOutcome(target = target, complete = true)
        } catch (t: Throwable) {
            cleanupBestEffort(destinationProvider, target)
            throw t
        }
    }

    private suspend fun verifyCopiedFile(
        source: HexoraFileRef,
        target: HexoraFileRef,
        destinationProvider: FileAccessProvider,
        bytesWritten: Long,
    ) {
        val expected = source.size
        if (expected != null && expected >= 0 && expected != bytesWritten) {
            fail(HexoraError.IoFailure("A cópia foi interrompida antes de todos os bytes serem gravados."))
        }

        val targetStat = destinationProvider.stat(target).unwrap()
        val actual = targetStat.size
        if (actual != null && actual >= 0 && actual != bytesWritten) {
            fail(HexoraError.IoFailure("O tamanho do arquivo copiado não confere com os bytes gravados."))
        }
    }

    private suspend fun destinationContainsName(
        provider: FileAccessProvider,
        destination: HexoraFileRef,
        name: String,
    ): Boolean = provider.list(destination).unwrap().any { it.name == name }

    private suspend fun chooseTargetName(
        provider: FileAccessProvider,
        destination: HexoraFileRef,
        originalName: String,
        policy: ConflictPolicy,
    ): String? {
        val existingNames = provider.list(destination).unwrap().asSequence().map { it.name }.toHashSet()
        if (originalName !in existingNames) return originalName

        return when (policy) {
            ConflictPolicy.SKIP -> null
            ConflictPolicy.REPLACE -> fail(
                HexoraError.UnsupportedFormat(
                    "Substituição destrutiva foi bloqueada até existir troca transacional segura para este provider.",
                ),
            )
            ConflictPolicy.RENAME_NEW,
            ConflictPolicy.KEEP_BOTH,
            -> {
                val dot = originalName.lastIndexOf('.')
                val base = if (dot > 0) originalName.substring(0, dot) else originalName
                val ext = if (dot > 0) originalName.substring(dot) else ""
                var index = 1
                var candidate: String
                do {
                    candidate = "$base ($index)$ext"
                    index++
                } while (candidate in existingNames)
                candidate
            }
        }
    }

    private suspend fun cleanupBestEffort(provider: FileAccessProvider, ref: HexoraFileRef) {
        try {
            deleteRecursive(provider, ref)
        } catch (_: Throwable) {
            // Cleanup failure must never hide the original operation failure.
        }
    }

    private suspend fun deleteRecursive(provider: FileAccessProvider, ref: HexoraFileRef) {
        if (ref.isDirectory) {
            provider.list(ref).unwrap().forEach { child -> deleteRecursive(provider, child) }
        }
        provider.delete(ref).unwrap()
    }

    private fun add(operation: FileOperation) {
        _operations.value = (listOf(operation) + _operations.value).take(MAX_HISTORY)
    }

    private fun update(operationId: String, transform: (FileOperation) -> FileOperation) {
        _operations.value = _operations.value.map { operation ->
            if (operation.operationId == operationId) transform(operation) else operation
        }
    }

    private fun fail(error: HexoraError): Nothing = throw OperationFailure(error)

    private fun <T> HexoraResult<T>.unwrap(): T = when (this) {
        is HexoraResult.Success -> value
        is HexoraResult.Failure -> fail(error)
    }

    private data class CopyOutcome(
        val target: HexoraFileRef?,
        val complete: Boolean,
    )

    private class OperationFailure(val error: HexoraError) : RuntimeException()

    companion object {
        const val COPY_BUFFER_SIZE = 256 * 1024
        private const val MAX_HISTORY = 50
    }
}
