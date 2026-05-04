package io.github.fadizg.kmpai.llm

import platform.Foundation.NSURL

actual class LlmEnvironment(
    actual val repository: ModelRepository,
    actual val factory: LlmEngineFactory,
) {
    constructor(cacheDirUrl: NSURL = IosModelCache.userCacheDirUrl()) : this(
        repository = IosModelRepository(cacheDirUrl),
        factory = LlmEngineFactory(),
    )

    actual suspend fun load(source: ModelSource, config: EngineConfig): LlmEngine =
        factory.load(repository.path(source), config)
}
