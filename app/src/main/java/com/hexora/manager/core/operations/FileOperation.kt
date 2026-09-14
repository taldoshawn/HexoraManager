package com.hexora.manager.core.operations

import com.hexora.manager.core.model.HexoraError
import com.hexora.manager.core.model.HexoraFileRef
import java.util.UUID

enum class FileOperationType { COPY, MOVE, DELETE, RENAME, CREATE_FILE, CREATE_DIRECTORY, HASH }
enum class FileOperationState { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
enum class ConflictPolicy { REPLACE, SKIP, RENAME_NEW, KEEP_BOTH }

data class FileOperation(
    val operationId: String = UUID.randomUUID().toString(),
    val type: FileOperationType,
    val sources: List<HexoraFileRef>,
    val destination: HexoraFileRef? = null,
    val state: FileOperationState = FileOperationState.QUEUED,
    val progress: Float = 0f,
    val bytesProcessed: Long = 0,
    val bytesTotal: Long? = null,
    val filesProcessed: Int = 0,
    val filesTotal: Int? = null,
    val speedBytesPerSecond: Long? = null,
    val etaMillis: Long? = null,
    val error: HexoraError? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
