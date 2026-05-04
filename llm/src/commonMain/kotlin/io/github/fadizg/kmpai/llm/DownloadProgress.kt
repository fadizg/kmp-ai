package io.github.fadizg.kmpai.llm

sealed interface DownloadProgress {
    data class Running(val bytes: Long, val total: Long?) : DownloadProgress {
        val fraction: Float? get() = total?.takeIf { it > 0 }?.let { bytes.toFloat() / it.toFloat() }
    }

    data class Done(val path: String) : DownloadProgress

    data class Failed(val cause: Throwable) : DownloadProgress
}
