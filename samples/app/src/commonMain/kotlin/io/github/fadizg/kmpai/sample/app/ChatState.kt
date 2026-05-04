package io.github.fadizg.kmpai.sample.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fadizg.kmpai.llm.ChatSession
import io.github.fadizg.kmpai.llm.ChatTemplate
import io.github.fadizg.kmpai.llm.DownloadProgress
import io.github.fadizg.kmpai.llm.EngineConfig
import io.github.fadizg.kmpai.llm.LlmEngine
import io.github.fadizg.kmpai.llm.LlmEnvironment
import io.github.fadizg.kmpai.llm.ModelSource
import io.github.fadizg.kmpai.llm.SamplingParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ChatLine(val author: Author, val text: String) {
    enum class Author { USER, ASSISTANT }
}

class ChatState(
    private val env: LlmEnvironment,
    private val source: ModelSource,
    private val template: ChatTemplate,
    private val systemPrompt: String?,
) {
    var status: String by mutableStateOf("preparing…")
        private set
    var downloadFraction: Float? by mutableStateOf(null)
        private set
    var messages: List<ChatLine> by mutableStateOf(emptyList())
        private set
    var streaming: String by mutableStateOf("")
        private set
    var isReady: Boolean by mutableStateOf(false)
        private set
    var isGenerating: Boolean by mutableStateOf(false)
        private set

    private var session: ChatSession? = null
    private var engine: LlmEngine? = null
    private var generationJob: Job? = null

    suspend fun prepare() {
        status = "downloading model…"
        try {
            env.repository.resolve(source).onEach { progress ->
                when (progress) {
                    is DownloadProgress.Running -> {
                        status = "downloading… ${progress.bytes / 1_000_000} MB"
                        downloadFraction = progress.fraction
                    }
                    is DownloadProgress.Done -> {
                        status = "loading model…"
                        downloadFraction = null
                    }
                    is DownloadProgress.Failed -> {
                        status = "download failed: ${progress.cause.message}"
                    }
                }
            }.collect()
            val loaded = env.load(source, EngineConfig(contextSize = 2048))
            engine = loaded
            session = ChatSession(loaded, template, systemPrompt)
            status = "ready"
            isReady = true
        } catch (t: Throwable) {
            status = "error: ${t.message}"
        }
    }

    fun send(message: String, scope: CoroutineScope) {
        val chat = session ?: return
        val text = message.trim()
        if (text.isEmpty() || isGenerating) return
        messages = messages + ChatLine(ChatLine.Author.USER, text)
        streaming = ""
        isGenerating = true
        generationJob = scope.launch {
            val sb = StringBuilder()
            try {
                chat.send(text, SamplingParams(maxTokens = 256, temperature = 0.7f))
                    .collect { token ->
                        sb.append(token.text)
                        streaming = sb.toString()
                    }
                messages = messages + ChatLine(ChatLine.Author.ASSISTANT, sb.toString().trim())
            } catch (t: Throwable) {
                messages = messages + ChatLine(
                    ChatLine.Author.ASSISTANT, "[error] ${t.message}",
                )
            } finally {
                streaming = ""
                isGenerating = false
            }
        }
    }

    fun close() {
        generationJob?.cancel()
        engine?.close()
    }
}
