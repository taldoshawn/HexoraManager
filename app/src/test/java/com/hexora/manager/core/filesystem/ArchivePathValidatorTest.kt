package com.hexora.manager.core.filesystem

import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePathValidatorTest {
    @Test
    fun acceptsRelativeEntriesInsideArchiveRoot() {
        listOf(
            "arquivo.txt",
            "pasta/arquivo.txt",
            "a/b/c/",
            "unicode/日本語.txt",
        ).forEach { entry -> assertTrue(entry, ArchivePathValidator.isSafeEntryName(entry)) }
    }

    @Test
    fun rejectsZipSlipAbsoluteAndInvalidEntries() {
        listOf(
            "",
            "../segredo.txt",
            "pasta/../../segredo.txt",
            "/etc/passwd",
            "C:/Windows/system.ini",
            "C:\\Windows\\system.ini",
            "pasta\\..\\segredo.txt",
            "nul\u0000byte",
        ).forEach { entry -> assertFalse(entry, ArchivePathValidator.isSafeEntryName(entry)) }
    }

    @Test
    fun resolvedPathMustRemainInsideDestination() {
        val root = Path.of("/tmp/hexora-extract")
        assertTrue(ArchivePathValidator.resolvesInside(root, "safe/sub/file.txt"))
        assertFalse(ArchivePathValidator.resolvesInside(root, "../escape.txt"))
        assertFalse(ArchivePathValidator.resolvesInside(root, "safe/../../escape.txt"))
    }
}
