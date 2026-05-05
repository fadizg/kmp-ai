package io.github.fadizg.kmpai.llm

data class SamplingParams(
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val seed: Int? = null,
    val stop: List<String> = emptyList(),
) {
    companion object {
        /** Low-temperature, focused output. Good for factual Q&A, summarisation, code. */
        val Conservative: SamplingParams = SamplingParams(
            temperature = 0.2f,
            topP = 0.9f,
            topK = 20,
        )

        /** Default. Balanced for chat. */
        val Balanced: SamplingParams = SamplingParams()

        /** Higher temperature, more variety. Good for creative writing, brainstorming. */
        val Creative: SamplingParams = SamplingParams(
            temperature = 0.9f,
            topP = 0.95f,
            topK = 80,
        )
    }
}
