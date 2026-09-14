package com.hexora.manager.core.filesystem

object FilenameValidator {
    fun validate(name: String): Boolean {
        if (name.isBlank() || name == "." || name == "..") return false
        if (name.length > 255) return false
        return name.none { ch ->
            ch == '/' || ch == '\u0000' || ch.code in 1..31 || ch.code == 127
        }
    }
}
