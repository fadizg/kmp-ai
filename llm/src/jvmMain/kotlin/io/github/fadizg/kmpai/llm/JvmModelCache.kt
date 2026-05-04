package io.github.fadizg.kmpai.llm

import java.io.File

object JvmModelCache {
    fun userCacheDir(): File {
        val home = System.getProperty("user.home") ?: "."
        val xdg = System.getenv("XDG_CACHE_HOME")
        val os = System.getProperty("os.name").lowercase()
        val base = when {
            !xdg.isNullOrBlank() -> File(xdg)
            os.contains("mac") -> File(home, "Library/Caches")
            os.contains("windows") -> File(System.getenv("LOCALAPPDATA") ?: "$home/AppData/Local")
            else -> File(home, ".cache")
        }
        return File(File(base, "kmp-ai"), "models")
    }
}
