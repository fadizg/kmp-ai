package io.github.fadizg.kmpai.llm

/**
 * Optional capability for engines that maintain a llama.cpp KV cache across
 * calls. Implementations that don't reuse cache (e.g. JVM, where caching is
 * handled by the underlying server) won't implement this — `ChatSession`
 * cooperates with whichever it gets.
 */
interface KvCacheControl {
    /** Drop everything in the KV cache. Next [LlmEngine.generate] re-decodes the full prompt. */
    fun resetKvCache()
}
