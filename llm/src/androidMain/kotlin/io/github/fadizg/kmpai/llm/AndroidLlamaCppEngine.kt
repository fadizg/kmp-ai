package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidLlamaCppEngine private constructor(
    private val handle: Long,
    override val info: ModelInfo,
) : LlmEngine {

    private val closed = AtomicBoolean(false)

    override fun generate(prompt: String, params: SamplingParams): Flow<Token> = callbackFlow {
        ensureOpen()
        val cancelled = AtomicBoolean(false)
        val callback = TokenCallback { text ->
            if (cancelled.get()) return@TokenCallback false
            val sent = trySendBlocking(Token(text)).isSuccess
            sent && !cancelled.get()
        }
        val thread = Thread({
            try {
                LlamaNative.nativeGenerate(
                    handle = handle,
                    prompt = prompt,
                    maxTokens = params.maxTokens,
                    temperature = params.temperature,
                    topK = params.topK,
                    topP = params.topP,
                    repeatPenalty = params.repeatPenalty,
                    seed = params.seed ?: -1,
                    stops = params.stop.toTypedArray(),
                    callback = callback,
                )
                close()
            } catch (t: Throwable) {
                close(GenerationException("native generate failed: ${t.message}", t))
            }
        }, "kmpai-llama-gen")
        thread.start()
        awaitClose {
            cancelled.set(true)
            thread.join(2_000)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(prompt: String, params: SamplingParams): String =
        withContext(Dispatchers.IO) {
            ensureOpen()
            val sb = StringBuilder()
            val callback = TokenCallback { text -> sb.append(text); true }
            try {
                LlamaNative.nativeGenerate(
                    handle = handle,
                    prompt = prompt,
                    maxTokens = params.maxTokens,
                    temperature = params.temperature,
                    topK = params.topK,
                    topP = params.topP,
                    repeatPenalty = params.repeatPenalty,
                    seed = params.seed ?: -1,
                    stops = params.stop.toTypedArray(),
                    callback = callback,
                )
            } catch (t: Throwable) {
                throw GenerationException("native complete failed: ${t.message}", t)
            }
            sb.toString()
        }

    override fun tokenize(text: String): IntArray {
        ensureOpen()
        return LlamaNative.nativeTokenize(handle, text)
    }

    override fun embed(text: String): FloatArray {
        ensureOpen()
        return LlamaNative.nativeEmbed(handle, text)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            LlamaNative.nativeFreeModel(handle)
        }
    }

    private fun ensureOpen() {
        if (closed.get()) throw IllegalStateException("engine already closed")
    }

    companion object {
        suspend fun load(modelPath: String, config: EngineConfig): LlmEngine =
            withContext(Dispatchers.IO) {
                val file = File(modelPath)
                if (!file.exists()) {
                    throw ModelLoadException("model file not found: $modelPath")
                }
                val handle = try {
                    LlamaNative.nativeLoadModel(
                        modelPath = modelPath,
                        ctxSize = config.contextSize,
                        gpuLayers = config.gpuLayers,
                        threads = config.threads ?: 0,
                    )
                } catch (t: Throwable) {
                    throw ModelLoadException("failed to load model: ${t.message}", t)
                }
                if (handle == 0L) {
                    throw ModelLoadException("nativeLoadModel returned null handle")
                }
                AndroidLlamaCppEngine(
                    handle = handle,
                    info = ModelInfo(
                        name = file.nameWithoutExtension,
                        contextSize = LlamaNative.nativeContextSize(handle),
                        backend = if (config.gpuLayers != 0) Backend.VULKAN else Backend.CPU,
                    ),
                )
            }
    }
}
