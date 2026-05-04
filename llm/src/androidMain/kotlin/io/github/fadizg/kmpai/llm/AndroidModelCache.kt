package io.github.fadizg.kmpai.llm

import android.content.Context
import java.io.File

object AndroidModelCache {
    /**
     * Returns the per-app model cache directory, preferring external app-private storage
     * (which has more headroom for multi-GB GGUF files) and falling back to the internal
     * files dir if external is unavailable.
     */
    fun forContext(context: Context): File {
        val external = context.getExternalFilesDir(null)
        val base = external ?: context.filesDir
        return File(File(base, "kmp-ai"), "models").apply { mkdirs() }
    }
}
