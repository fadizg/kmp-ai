package io.github.fadizg.kmpai.llm

data class SamplingParams(
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val seed: Int? = null,
    val stop: List<String> = emptyList(),
)
