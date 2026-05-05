package io.github.fadizg.kmpai.llm

/**
 * One-stop entrypoint for kmp-ai. Holds a default [ModelRepository] and an
 * [LlmEngineFactory] for the current platform; [load] resolves a model and
 * loads it into a fresh engine in one call.
 *
 * The recommended way to obtain an instance is [Companion.default]:
 *
 * ```kotlin
 * val env = LlmEnvironment.default()        // works on JVM, Android, iOS
 * val engine = env.load(Qwen.Qwen2_5_0_5B_Q4)
 * ```
 *
 * On Android this works without passing a `Context` because the library
 * registers an AndroidX Startup `Initializer` via its manifest, which captures
 * the Application context at process start. If your consumer app has disabled
 * AndroidX Startup, fall back to `LlmEnvironment(context)`.
 *
 * Pass your own [ModelRepository] / [LlmEngineFactory] to override the defaults.
 */
expect class LlmEnvironment {
    val repository: ModelRepository
    val factory: LlmEngineFactory

    /**
     * Resolve [source] (downloading + caching if needed) and load the
     * resulting GGUF into a fresh [LlmEngine]. The caller owns the engine
     * and is responsible for `close()`-ing it.
     */
    suspend fun load(
        source: ModelSource,
        config: EngineConfig = EngineConfig(),
    ): LlmEngine

    companion object {
        /** Platform-default environment. Zero-arg on every platform. */
        fun default(): LlmEnvironment
    }
}
