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
 *
 * Set [defaultSampling] (via [withDefaults]) to apply a baseline
 * [SamplingParams] — most usefully a default [Grammar] — to every chat
 * session created via [chat]. Per-call params still win.
 */
expect class LlmEnvironment {
    val repository: ModelRepository
    val factory: LlmEngineFactory

    /** Baseline sampling applied to every [chat] session. Per-call params override. */
    val defaultSampling: SamplingParams

    /**
     * Resolve [source] (downloading + caching if needed) and load the
     * resulting GGUF into a fresh [LlmEngine]. The caller owns the engine
     * and is responsible for `close()`-ing it.
     */
    suspend fun load(
        source: ModelSource,
        config: EngineConfig = EngineConfig(),
    ): LlmEngine

    /** Returns a copy of this environment with the supplied [SamplingParams] as the new baseline. */
    fun withDefaults(defaults: SamplingParams): LlmEnvironment

    companion object {
        /** Platform-default environment. Zero-arg on every platform. */
        fun default(): LlmEnvironment
    }
}

/**
 * Resolve [source], load it, and wrap the engine in a [ChatSession] that
 * inherits this environment's [LlmEnvironment.defaultSampling]. The
 * [systemPrompt] (if any) is added to the session.
 */
suspend fun LlmEnvironment.chat(
    source: ModelSource,
    template: ChatTemplate = ChatTemplate.ChatML,
    systemPrompt: String? = null,
    config: EngineConfig = EngineConfig(),
): ChatSession = ChatSession(
    engine = load(source, config),
    template = template,
    systemPrompt = systemPrompt,
    defaults = defaultSampling,
)
