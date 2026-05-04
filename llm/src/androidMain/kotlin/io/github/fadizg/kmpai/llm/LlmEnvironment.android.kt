package io.github.fadizg.kmpai.llm

import android.content.Context

actual class LlmEnvironment(
    actual val repository: ModelRepository,
    actual val factory: LlmEngineFactory,
) {
    constructor(context: Context) : this(
        repository = DefaultModelRepository(AndroidModelCache.forContext(context)),
        factory = LlmEngineFactory(),
    )

    actual suspend fun load(source: ModelSource, config: EngineConfig): LlmEngine =
        factory.load(repository.path(source), config)
}
