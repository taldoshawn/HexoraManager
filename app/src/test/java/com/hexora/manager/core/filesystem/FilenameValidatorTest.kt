package com.hexora.manager.core.filesystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameValidatorTest {
    @Test
    fun acceptsOrdinaryFileNames() {
        listOf(
            "documento.txt",
            "foto 2026.webp",
            "arquivo-sem-extensao",
            ".nomedia",
            "日本語.txt",
        ).forEach { name -> assertTrue(name, FilenameValidator.validate(name)) }
    }

    @Test
    fun rejectsUnsafeOrInvalidNames() {
        listOf(
            "",
            "   ",
            ".",
            "..",
            "pasta/arquivo.txt",
            "nul\u0000byte",
            "controle\u0001",
            "delete\u007f",
            "a".repeat(256),
        ).forEach { name -> assertFalse(name, FilenameValidator.validate(name)) }
    }
}
