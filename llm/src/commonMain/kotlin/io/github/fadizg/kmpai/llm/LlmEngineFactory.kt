package io.github.fadizg.kmpai.llm

expect class LlmEngineFactory() {
    suspend fun load(modelPath: String, config: EngineConfig = EngineConfig()): LlmEngine
}
