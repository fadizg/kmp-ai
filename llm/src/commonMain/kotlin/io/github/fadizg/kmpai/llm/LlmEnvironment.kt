package io.github.fadizg.kmpai.llm

/**
 * One-stop entrypoint for kmp-ai. Holds a default [ModelRepository] and an
 * [LlmEngineFactory] for the current platform; [load] resolves a model and
 * loads it into a fresh engine in one call.
 *
 * Construction is the only thing that differs between platforms:
 *
 *   - JVM:     `LlmEnvironment()`
 *   - Android: `LlmEnvironment(context)`
 *   - iOS:     `LlmEnvironment()`
 *
 * Wire it once into your DI container, then everything downstream stays in
 * `commonMain`.
 *
 * ```kotlin
 * // androidMain
 * single { LlmEnvironment(androidContext()) }
 *
 * // jvmMain / iosMain
 * single { LlmEnvironment() }
 *
 * // commonMain
 * val env: LlmEnvironment = get()
 * val engine = env.load(Qwen.Qwen2_5_0_5B_Q4)
 * ```
 *
 * Pass your own [ModelRepository] / [LlmEngineFactory] to override the
 * defaults (for example if you bundle the model file with the app and don't
 * want kmp-ai's cache directory at all).
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
}
