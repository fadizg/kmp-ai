package io.github.fadizg.kmpai.llm

import java.io.File

actual class LlmEnvironment(
    actual val repository: ModelRepository,
    actual val factory: LlmEngineFactory,
) {
    constructor(cacheDir: File = JvmModelCache.userCacheDir()) : this(
        repository = DefaultModelRepository(cacheDir),
        factory = LlmEngineFactory(),
    )

    actual suspend fun load(source: ModelSource, config: EngineConfig): LlmEngine =
        factory.load(repository.path(source), config)
}
