package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * Stateful chat over an [LlmEngine]. Holds the conversation history,
 * formats prompts via [template], and reuses the engine's KV cache across
 * turns (when supported) so each follow-up only decodes the new user
 * message + reply rather than the whole history.
 *
 * Sampling layering (most-specific wins):
 *
 * 1. per-call [send] / [complete] / [choose] / [askJson] params,
 * 2. session [defaults] passed to the constructor,
 * 3. [LlmEnvironment.defaultSampling] (when the session was created via
 *    [LlmEnvironment.chat]).
 *
 * Only the [SamplingParams.grammar] field is layered between (1) and (2);
 * other fields use the most-specific [SamplingParams] verbatim.
 */
class ChatSession(
    private val engine: LlmEngine,
    private val template: ChatTemplate = ChatTemplate.ChatML,
    systemPrompt: String? = null,
    /** Default sampling for [send] / [complete] when the caller doesn't override. */
    val defaults: SamplingParams = SamplingParams(),
) {
    private val messages: MutableList<ChatMessage> = mutableListOf()

    init {
        if (systemPrompt != null) {
            messages += ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt)
        }
    }

    val history: List<ChatMessage> get() = messages.toList()

    fun send(userMessage: String, params: SamplingParams = defaults): Flow<Token> {
        messages += ChatMessage(ChatMessage.Role.USER, userMessage)
        val effective = params
            .withGrammarFallback(defaults)
            .copy(stop = (params.stop + template.stopSequences()).distinct())
        val prompt = template.format(messages)
        val buffer = StringBuilder()
        return engine.generate(prompt, effective)
            .onEach { buffer.append(it.text) }
            .onCompletion { cause ->
                if (cause == null) {
                    messages += ChatMessage(ChatMessage.Role.ASSISTANT, buffer.toString().trim())
                }
            }
    }

    /**
     * Like [send] but blocks until generation finishes and returns the full
     * assistant reply. The reply is also appended to [history].
     */
    suspend fun complete(userMessage: String, params: SamplingParams = defaults): String {
        val sb = StringBuilder()
        send(userMessage, params).onEach { sb.append(it.text) }.last()
        return sb.toString().trim()
    }

    /**
     * Constrain the next reply to one of [options]. Returns the option the
     * model picked. Useful for yes/no, routing, multiple-choice. Token
     * budget is small by default since the output is bounded.
     */
    suspend fun choose(
        userMessage: String,
        vararg options: String,
        maxTokens: Int = 32,
    ): String {
        require(options.isNotEmpty()) { "choose() needs at least one option" }
        return complete(
            userMessage,
            defaults.copy(grammar = Grammar.choice(*options), maxTokens = maxTokens),
        )
    }

    /**
     * Constrain the next reply to be valid JSON (any shape). Returns the
     * raw JSON string — parsing is left to the caller so they can choose
     * their preferred deserializer (kotlinx.serialization, Moshi, etc.).
     */
    suspend fun askJson(
        userMessage: String,
        params: SamplingParams = defaults,
    ): String = complete(userMessage, params.copy(grammar = Grammar.json()))

    /** Drop the conversation history (system prompt is preserved) and clear the engine's KV cache. */
    fun reset() {
        val system = messages.firstOrNull { it.role == ChatMessage.Role.SYSTEM }
        messages.clear()
        if (system != null) messages += system
        (engine as? KvCacheControl)?.resetKvCache()
    }
}
