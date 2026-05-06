package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.flow.Flow

interface LlmEngine : AutoCloseable {
    val info: ModelInfo

    fun generate(prompt: String, params: SamplingParams = SamplingParams()): Flow<Token>

    suspend fun complete(prompt: String, params: SamplingParams = SamplingParams()): String

    fun tokenize(text: String): IntArray

    /**
     * Number of tokens [text] would consume — useful for staying under
     * [ModelInfo.contextSize] before sending a prompt. Default delegates
     * to [tokenize]; engines may override with a cheaper path.
     */
    fun countTokens(text: String): Int = tokenize(text).size

    fun embed(text: String): FloatArray
}
