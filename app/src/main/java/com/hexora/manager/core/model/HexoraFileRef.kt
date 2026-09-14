package com.hexora.manager.core.model

import android.net.Uri

enum class FileSource {
    LOCAL,
    SAF,
    MEDIASTORE,
    ARCHIVE,
    SHIZUKU,
    ADB,
    ROOT,
    REMOTE,
    VIRTUAL,
}

enum class FileCapability {
    READ,
    WRITE,
    LIST,
    CREATE,
    DELETE,
    RENAME,
    COPY,
    MOVE,
    SHARE,
    HASH,
    CHMOD,
    CHOWN,
    EXECUTE,
    INSTALL,
    MOUNT,
    ROOT_OPERATION,
}

enum class PrivilegeLevel {
    NORMAL,
    MEDIASTORE,
    SAF,
    ALL_FILES,
    SHIZUKU,
    ADB_SHELL,
    ROOT,
}

data class PosixPermissions(
    val symbolic: String? = null,
    val octal: String? = null,
    val uid: Int? = null,
    val gid: Int? = null,
)

data class HexoraFileRef(
    val id: String,
    val name: String,
    val displayName: String = name,
    val path: String? = null,
    val uri: String? = null,
    val parentId: String? = null,
    val source: FileSource,
    val providerKey: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val size: Long? = null,
    val mime: String? = null,
    val extension: String? = null,
    val lastModified: Long? = null,
    val permissions: PosixPermissions? = null,
    val capabilities: Set<FileCapability> = emptySet(),
) {
    fun parsedUri(): Uri? = uri?.let(Uri::parse)
}
