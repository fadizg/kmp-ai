package io.github.fadizg.kmpai.llm

import android.content.Context

actual class LlmEnvironment(
    actual val repository: ModelRepository,
    actual val factory: LlmEngineFactory,
    actual val defaultSampling: SamplingParams = SamplingParams(),
) {
    constructor(context: Context) : this(
        repository = DefaultModelRepository(AndroidModelCache.forContext(context)),
        factory = LlmEngineFactory(),
    )

    actual suspend fun load(source: ModelSource, config: EngineConfig): LlmEngine =
        factory.load(repository.path(source), config)

    actual fun withDefaults(defaults: SamplingParams): LlmEnvironment =
        LlmEnvironment(repository, factory, defaults)

    actual companion object {
        actual fun default(): LlmEnvironment {
            val ctx = KmpAiInitializer.appContext ?: error(
                "kmp-ai is not initialized. AndroidX Startup may have been disabled. " +
                    "Either re-enable Startup, or call LlmEnvironment(context) explicitly " +
                    "(typically from Application.onCreate()).",
            )
            return LlmEnvironment(ctx)
        }
    }
}
