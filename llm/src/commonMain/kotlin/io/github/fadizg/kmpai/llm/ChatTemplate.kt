package io.github.fadizg.kmpai.llm

sealed interface ChatTemplate {
    fun format(messages: List<ChatMessage>): String
    fun stopSequences(): List<String>

    data object ChatML : ChatTemplate {
        override fun format(messages: List<ChatMessage>): String = buildString {
            for (m in messages) {
                append("<|im_start|>").append(m.role.tag()).append('\n')
                append(m.content).append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }

        override fun stopSequences(): List<String> = listOf("<|im_end|>")

        private fun ChatMessage.Role.tag(): String = when (this) {
            ChatMessage.Role.SYSTEM -> "system"
            ChatMessage.Role.USER -> "user"
            ChatMessage.Role.ASSISTANT -> "assistant"
        }
    }

    data object Llama3 : ChatTemplate {
        override fun format(messages: List<ChatMessage>): String = buildString {
            append("<|begin_of_text|>")
            for (m in messages) {
                append("<|start_header_id|>").append(m.role.tag()).append("<|end_header_id|>\n\n")
                append(m.content).append("<|eot_id|>")
            }
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }

        override fun stopSequences(): List<String> = listOf("<|eot_id|>")

        private fun ChatMessage.Role.tag(): String = when (this) {
            ChatMessage.Role.SYSTEM -> "system"
            ChatMessage.Role.USER -> "user"
            ChatMessage.Role.ASSISTANT -> "assistant"
        }
    }

    data object Gemma : ChatTemplate {
        override fun format(messages: List<ChatMessage>): String = buildString {
            for (m in messages) {
                val role = when (m.role) {
                    ChatMessage.Role.ASSISTANT -> "model"
                    else -> "user"
                }
                append("<start_of_turn>").append(role).append('\n')
                append(m.content).append("<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }

        override fun stopSequences(): List<String> = listOf("<end_of_turn>")
    }

    data class Custom(
        val formatter: (List<ChatMessage>) -> String,
        val stops: List<String> = emptyList(),
    ) : ChatTemplate {
        override fun format(messages: List<ChatMessage>): String = formatter(messages)
        override fun stopSequences(): List<String> = stops
    }
}
