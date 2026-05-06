package io.github.fadizg.kmpai.llm

data class SamplingParams(
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val seed: Int? = null,
    val stop: List<String> = emptyList(),
    /**
     * Constrains output to text matching a GBNF grammar. `null` means free
     * generation. See [Grammar] for helpers (choice, regex, json, raw).
     */
    val grammar: Grammar? = null,
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

/**
 * Layer [override] on top of [base]: any non-default field in [override]
 * wins, otherwise [base] is kept. Used to thread environment-level and
 * session-level defaults through to per-call params.
 *
 * Implementation: only `grammar` is currently overlaid (it's the only
 * field with a clear "unset" sentinel, `null`). Other fields use
 * [override]'s value verbatim — call sites that want to inherit from
 * [base] should pass [base]'s field explicitly.
 */
internal fun SamplingParams.withGrammarFallback(base: SamplingParams?): SamplingParams =
    if (grammar != null || base?.grammar == null) this else copy(grammar = base.grammar)
