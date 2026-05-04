package io.github.fadizg.kmpai.sample.android

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fadizg.kmpai.catalog.Qwen
import io.github.fadizg.kmpai.llm.AndroidModelCache
import io.github.fadizg.kmpai.llm.ChatSession
import io.github.fadizg.kmpai.llm.DefaultModelRepository
import io.github.fadizg.kmpai.llm.DownloadProgress
import io.github.fadizg.kmpai.llm.EngineConfig
import io.github.fadizg.kmpai.llm.LlmEngine
import io.github.fadizg.kmpai.llm.LlmEngineFactory
import io.github.fadizg.kmpai.llm.SamplingParams
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ChatUiState(
    val status: String = "idle",
    val downloadFraction: Float? = null,
    val messages: List<ChatLine> = emptyList(),
    val streaming: String = "",
    val isReady: Boolean = false,
    val isGenerating: Boolean = false,
)

data class ChatLine(val author: Author, val text: String) {
    enum class Author { USER, ASSISTANT }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = mutableStateOf(ChatUiState(status = "downloading model…"))
    val state: State<ChatUiState> = _state

    private val source = Qwen.Qwen2_5_0_5B_Q4
    private val template = Qwen.template

    private var engine: LlmEngine? = null
    private var session: ChatSession? = null
    private var generationJob: Job? = null

    init {
        viewModelScope.launch { prepare() }
    }

    private suspend fun prepare() {
        val ctx = getApplication<Application>().applicationContext
        val repo = DefaultModelRepository(AndroidModelCache.forContext(ctx))
        val modelPath: String = try {
            repo.resolve(source).onEach { progress ->
                when (progress) {
                    is DownloadProgress.Running -> _state.value = _state.value.copy(
                        status = "downloading… ${progress.bytes / 1_000_000} MB",
                        downloadFraction = progress.fraction,
                    )
                    is DownloadProgress.Done -> _state.value = _state.value.copy(
                        status = "ready", downloadFraction = null,
                    )
                    is DownloadProgress.Failed -> {
                        _state.value = _state.value.copy(
                            status = "download failed: ${progress.cause.message}",
                            downloadFraction = null,
                        )
                    }
                }
            }.collect()
            repo.path(source)
        } catch (t: Throwable) {
            _state.value = _state.value.copy(status = "download error: ${t.message}")
            return
        }

        _state.value = _state.value.copy(status = "loading model…")
        try {
            val loaded = LlmEngineFactory().load(
                modelPath = modelPath,
                config = EngineConfig(contextSize = 2048, gpuLayers = 0),
            )
            engine = loaded
            session = ChatSession(
                engine = loaded,
                template = template,
                systemPrompt = "You are a concise, helpful assistant. Reply in one short paragraph.",
            )
            _state.value = _state.value.copy(status = "ready", isReady = true)
        } catch (t: Throwable) {
            _state.value = _state.value.copy(status = "load error: ${t.message}")
        }
    }

    fun send(message: String) {
        val chat = session ?: return
        val trimmed = message.trim()
        if (trimmed.isEmpty() || _state.value.isGenerating) return

        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatLine(ChatLine.Author.USER, trimmed),
            streaming = "",
            isGenerating = true,
        )

        generationJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                chat.send(trimmed, SamplingParams(maxTokens = 256, temperature = 0.7f))
                    .collect { token ->
                        sb.append(token.text)
                        _state.value = _state.value.copy(streaming = sb.toString())
                    }
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatLine(ChatLine.Author.ASSISTANT, sb.toString().trim()),
                    streaming = "",
                    isGenerating = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatLine(
                        ChatLine.Author.ASSISTANT,
                        "[error] ${t.message}",
                    ),
                    streaming = "",
                    isGenerating = false,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        engine?.close()
    }
}
