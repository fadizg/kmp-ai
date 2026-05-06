package io.github.fadizg.kmpai.llm

import platform.Foundation.NSURL

actual class LlmEnvironment(
    actual val repository: ModelRepository,
    actual val factory: LlmEngineFactory,
    actual val defaultSampling: SamplingParams = SamplingParams(),
) {
    constructor(cacheDirUrl: NSURL = IosModelCache.userCacheDirUrl()) : this(
        repository = IosModelRepository(cacheDirUrl),
        factory = LlmEngineFactory(),
    )

    actual suspend fun load(source: ModelSource, config: EngineConfig): LlmEngine =
        factory.load(repository.path(source), config)

    actual fun withDefaults(defaults: SamplingParams): LlmEnvironment =
        LlmEnvironment(repository, factory, defaults)

    actual companion object {
        actual fun default(): LlmEnvironment = LlmEnvironment()
    }
}
