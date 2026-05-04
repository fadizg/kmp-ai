package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.flow.Flow

interface LlmEngine : AutoCloseable {
    val info: ModelInfo

    fun generate(prompt: String, params: SamplingParams = SamplingParams()): Flow<Token>

    suspend fun complete(prompt: String, params: SamplingParams = SamplingParams()): String

    fun tokenize(text: String): IntArray

    fun embed(text: String): FloatArray
}
