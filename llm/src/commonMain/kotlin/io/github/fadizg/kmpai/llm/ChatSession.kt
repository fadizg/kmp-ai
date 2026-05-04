package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

class ChatSession(
    private val engine: LlmEngine,
    private val template: ChatTemplate = ChatTemplate.ChatML,
    systemPrompt: String? = null,
) {
    private val messages: MutableList<ChatMessage> = mutableListOf()

    init {
        if (systemPrompt != null) {
            messages += ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt)
        }
    }

    val history: List<ChatMessage> get() = messages.toList()

    fun send(userMessage: String, params: SamplingParams = SamplingParams()): Flow<Token> {
        messages += ChatMessage(ChatMessage.Role.USER, userMessage)
        val prompt = template.format(messages)
        val effective = params.copy(stop = (params.stop + template.stopSequences()).distinct())
        val buffer = StringBuilder()
        return engine.generate(prompt, effective)
            .onEach { buffer.append(it.text) }
            .onCompletion { cause ->
                if (cause == null) {
                    messages += ChatMessage(ChatMessage.Role.ASSISTANT, buffer.toString().trim())
                }
            }
    }

    fun reset() {
        val system = messages.firstOrNull { it.role == ChatMessage.Role.SYSTEM }
        messages.clear()
        if (system != null) messages += system
    }
}
