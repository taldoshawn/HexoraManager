package com.hexora.manager.core.filesystem

import java.nio.file.Path

/** Pure path checks used by archive extraction before touching the filesystem. */
object ArchivePathValidator {
    fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank() || name.indexOf('\u0000') >= 0) return false
        val normalizedSlashes = name.replace('\\', '/')
        if (normalizedSlashes.startsWith('/')) return false
        if (WINDOWS_DRIVE.matches(normalizedSlashes)) return false
        val segments = normalizedSlashes.split('/')
        if (segments.any { it == ".." }) return false
        return true
    }

    fun resolvesInside(root: Path, entryName: String): Boolean {
        if (!isSafeEntryName(entryName)) return false
        val normalizedRoot = root.toAbsolutePath().normalize()
        val target = normalizedRoot.resolve(entryName.replace('\\', '/')).normalize()
        return target.startsWith(normalizedRoot)
    }

    private val WINDOWS_DRIVE = Regex("^[A-Za-z]:/.*")
}
