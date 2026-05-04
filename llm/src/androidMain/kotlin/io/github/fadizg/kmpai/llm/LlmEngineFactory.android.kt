package io.github.fadizg.kmpai.llm

actual class LlmEngineFactory actual constructor() {
    actual suspend fun load(modelPath: String, config: EngineConfig): LlmEngine =
        AndroidLlamaCppEngine.load(modelPath, config)
}
