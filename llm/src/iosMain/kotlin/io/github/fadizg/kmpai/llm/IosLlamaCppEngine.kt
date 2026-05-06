package io.github.fadizg.kmpai.llm

import cnames.structs.llama_context
import cnames.structs.llama_model
import io.github.fadizg.kmpai.llm.llamacpp.LLAMA_DEFAULT_SEED
import io.github.fadizg.kmpai.llm.llamacpp.llama_backend_init
import io.github.fadizg.kmpai.llm.llamacpp.llama_batch_get_one
import io.github.fadizg.kmpai.llm.llamacpp.llama_context_default_params
import io.github.fadizg.kmpai.llm.llamacpp.llama_decode
import io.github.fadizg.kmpai.llm.llamacpp.llama_free
import io.github.fadizg.kmpai.llm.llamacpp.llama_get_embeddings
import io.github.fadizg.kmpai.llm.llamacpp.llama_get_embeddings_seq
import io.github.fadizg.kmpai.llm.llamacpp.llama_get_memory
import io.github.fadizg.kmpai.llm.llamacpp.llama_init_from_model
import io.github.fadizg.kmpai.llm.llamacpp.llama_memory_clear
import io.github.fadizg.kmpai.llm.llamacpp.llama_memory_seq_rm
import io.github.fadizg.kmpai.llm.llamacpp.llama_model_default_params
import io.github.fadizg.kmpai.llm.llamacpp.llama_model_free
import io.github.fadizg.kmpai.llm.llamacpp.llama_model_get_vocab
import io.github.fadizg.kmpai.llm.llamacpp.llama_model_load_from_file
import io.github.fadizg.kmpai.llm.llamacpp.llama_model_n_embd
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_chain_add
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_chain_default_params
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_chain_init
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_free
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_init_dist
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_init_grammar
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_init_penalties
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_init_temp
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_init_top_k
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_init_top_p
import io.github.fadizg.kmpai.llm.llamacpp.llama_sampler_sample
import io.github.fadizg.kmpai.llm.llamacpp.llama_token_to_piece
import io.github.fadizg.kmpai.llm.llamacpp.llama_tokenize
import io.github.fadizg.kmpai.llm.llamacpp.llama_vocab_is_eog
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager

@OptIn(ExperimentalForeignApi::class)
internal class IosLlamaCppEngine private constructor(
    private val model: CPointer<llama_model>,
    private val ctx: CPointer<llama_context>,
    override val info: ModelInfo,
) : LlmEngine, KvCacheControl {

    private var closed = false

    // KV-cache reuse: tokens currently sitting in the KV cache. Next
    // generate() compares with the new prompt and only decodes the diff.
    private var cachedTokens: IntArray = IntArray(0)
    private val generateMutex = Mutex()

    override fun generate(prompt: String, params: SamplingParams): Flow<Token> = flow {
        ensureOpen()
        generateMutex.withLock {
            runGeneration(prompt, params) { piece -> emit(Token(piece)) }
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun complete(prompt: String, params: SamplingParams): String =
        withContext(Dispatchers.Default) {
            ensureOpen()
            val sb = StringBuilder()
            generateMutex.withLock {
                runGeneration(prompt, params) { sb.append(it) }
            }
            sb.toString()
        }

    override fun tokenize(text: String): IntArray = tokenize(text, addSpecial = false)

    override fun countTokens(text: String): Int {
        ensureOpen()
        return memScoped {
            val vocab = llama_model_get_vocab(model) ?: return@memScoped 0
            val byteLen = text.encodeToByteArray().size
            if (byteLen == 0) return@memScoped 0
            val nNeg = llama_tokenize(vocab, text, byteLen, null, 0, false, true)
            if (nNeg < 0) -nNeg else nNeg
        }
    }

    override fun embed(text: String): FloatArray {
        ensureOpen()
        return memScoped {
            val vocab = llama_model_get_vocab(model) ?: return@memScoped FloatArray(0)
            val byteLen = text.encodeToByteArray().size
            if (byteLen == 0) return@memScoped FloatArray(0)
            val nNeg = llama_tokenize(vocab, text, byteLen, null, 0, true, true)
            val n = if (nNeg < 0) -nNeg else nNeg
            if (n == 0) return@memScoped FloatArray(0)
            val tokens = allocArray<IntVar>(n)
            llama_tokenize(vocab, text, byteLen, tokens, n, true, true)

            // embed() invalidates the KV cache; reset our prefix tracking.
            llama_memory_clear(llama_get_memory(ctx), true)
            cachedTokens = IntArray(0)

            val batch = llama_batch_get_one(tokens, n)
            if (llama_decode(ctx, batch) != 0) {
                throw GenerationException("llama_decode failed during embed")
            }
            val nEmbd = llama_model_n_embd(model)
            val src = llama_get_embeddings(ctx) ?: llama_get_embeddings_seq(ctx, 0)
            FloatArray(nEmbd) { i -> if (src != null) src[i] else 0f }
        }
    }

    override fun resetKvCache() {
        ensureOpen()
        llama_memory_clear(llama_get_memory(ctx), true)
        cachedTokens = IntArray(0)
    }

    override fun close() {
        if (!closed) {
            closed = true
            llama_free(ctx)
            llama_model_free(model)
        }
    }

    private fun tokenize(text: String, addSpecial: Boolean): IntArray {
        ensureOpen()
        return memScoped {
            val vocab = llama_model_get_vocab(model) ?: return@memScoped IntArray(0)
            val byteLen = text.encodeToByteArray().size
            if (byteLen == 0) return@memScoped IntArray(0)
            val nNeg = llama_tokenize(vocab, text, byteLen, null, 0, addSpecial, true)
            val n = if (nNeg < 0) -nNeg else nNeg
            if (n == 0) return@memScoped IntArray(0)
            val buf = allocArray<IntVar>(n)
            val written = llama_tokenize(vocab, text, byteLen, buf, n, addSpecial, true)
            IntArray(written) { i -> buf[i] }
        }
    }

    private suspend inline fun runGeneration(
        prompt: String,
        params: SamplingParams,
        crossinline onPiece: suspend (String) -> Unit,
    ) {
        val sampler = buildSampler(params) ?: throw GenerationException("failed to init sampler chain")
        try {
            val pieceBuf = ByteArray(256)
            val accumulated = StringBuilder()

            val promptTokens = tokenize(prompt, addSpecial = true)
            if (promptTokens.isEmpty()) throw GenerationException("tokenization produced no tokens")

            // Compute KV-cache prefix reuse.
            var reuseN = commonPrefixLen(cachedTokens, promptTokens)
            if (reuseN == promptTokens.size) reuseN = promptTokens.size - 1

            if (reuseN == 0) {
                llama_memory_clear(llama_get_memory(ctx), true)
            } else if (reuseN < cachedTokens.size) {
                llama_memory_seq_rm(llama_get_memory(ctx), 0, reuseN, -1)
            }

            val prefillCount = promptTokens.size - reuseN
            val live = IntArray(promptTokens.size + params.maxTokens)
            promptTokens.copyInto(live, destinationOffset = 0)
            var liveSize = promptTokens.size

            memScoped {
                val vocab = llama_model_get_vocab(model)
                    ?: throw GenerationException("llama_model_get_vocab returned null")

                val prefillBuf = allocArray<IntVar>(prefillCount)
                for (i in 0 until prefillCount) {
                    prefillBuf[i] = promptTokens[reuseN + i]
                }
                val promptBatch = llama_batch_get_one(prefillBuf, prefillCount)
                if (llama_decode(ctx, promptBatch) != 0) {
                    llama_memory_clear(llama_get_memory(ctx), true)
                    cachedTokens = IntArray(0)
                    throw GenerationException("llama_decode failed for prompt")
                }

                val nextToken = alloc<IntVar>()
                for (i in 0 until params.maxTokens) {
                    val id = llama_sampler_sample(sampler, ctx, -1)
                    if (llama_vocab_is_eog(vocab, id)) {
                        if (liveSize < live.size) live[liveSize++] = id
                        break
                    }

                    val written = pieceBuf.usePinned { p ->
                        llama_token_to_piece(
                            vocab, id, p.addressOf(0).reinterpret(), pieceBuf.size, 0, true,
                        )
                    }
                    if (liveSize < live.size) live[liveSize++] = id
                    if (written <= 0) {
                        nextToken.value = id
                        if (llama_decode(ctx, llama_batch_get_one(nextToken.ptr, 1)) != 0) break
                        continue
                    }
                    val piece = pieceBuf.decodeToString(0, written)
                    accumulated.append(piece)
                    onPiece(piece)
                    if (params.stop.any { it.isNotEmpty() && accumulated.endsWith(it) }) break

                    nextToken.value = id
                    if (llama_decode(ctx, llama_batch_get_one(nextToken.ptr, 1)) != 0) break
                }
            }

            cachedTokens = live.copyOf(liveSize)
        } finally {
            llama_sampler_free(sampler)
        }
    }

    private fun buildSampler(params: SamplingParams): CPointer<llama_sampler>? {
        val chain = llama_sampler_chain_init(llama_sampler_chain_default_params()) ?: return null
        // Grammar mask first so it vetoes invalid tokens before any
        // probability shaping touches them.
        val grammar = params.grammar
        if (grammar != null) {
            val vocab = llama_model_get_vocab(model)
            if (vocab != null) {
                val gsmpl = llama_sampler_init_grammar(vocab, grammar.gbnf, "root")
                if (gsmpl != null) llama_sampler_chain_add(chain, gsmpl)
            }
        }
        llama_sampler_chain_add(chain, llama_sampler_init_penalties(64, params.repeatPenalty, 0f, 0f))
        if (params.topK > 0) llama_sampler_chain_add(chain, llama_sampler_init_top_k(params.topK))
        if (params.topP < 1f) llama_sampler_chain_add(chain, llama_sampler_init_top_p(params.topP, 1uL))
        llama_sampler_chain_add(chain, llama_sampler_init_temp(params.temperature))
        val seed = params.seed?.toUInt() ?: LLAMA_DEFAULT_SEED.toUInt()
        llama_sampler_chain_add(chain, llama_sampler_init_dist(seed))
        return chain
    }

    private fun ensureOpen() {
        if (closed) throw IllegalStateException("engine already closed")
    }

    private fun commonPrefixLen(a: IntArray, b: IntArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) if (a[i] != b[i]) return i
        return n
    }

    companion object {
        private var backendInitialized = false

        private fun ensureBackend() {
            if (!backendInitialized) {
                llama_backend_init()
                backendInitialized = true
            }
        }

        suspend fun load(modelPath: String, config: EngineConfig): LlmEngine =
            withContext(Dispatchers.Default) {
                if (!NSFileManager.defaultManager.fileExistsAtPath(modelPath)) {
                    throw ModelLoadException("model file not found: $modelPath")
                }
                ensureBackend()

                val modelParams = llama_model_default_params().useContents {
                    n_gpu_layers = config.gpuLayers
                    readValue()
                }
                val model = llama_model_load_from_file(modelPath, modelParams)
                    ?: throw ModelLoadException("llama_model_load_from_file returned null for $modelPath")

                val ctxParams = llama_context_default_params().useContents {
                    n_ctx = config.contextSize.toUInt()
                    n_batch = 512u
                    config.threads?.let {
                        n_threads = it
                        n_threads_batch = it
                    }
                    readValue()
                }
                val ctx = llama_init_from_model(model, ctxParams) ?: run {
                    llama_model_free(model)
                    throw ModelLoadException("llama_init_from_model returned null")
                }

                val name = modelPath.substringAfterLast('/').substringBeforeLast('.')
                IosLlamaCppEngine(
                    model = model,
                    ctx = ctx,
                    info = ModelInfo(
                        name = name,
                        contextSize = config.contextSize,
                        backend = if (config.gpuLayers != 0) Backend.METAL else Backend.CPU,
                    ),
                )
            }
    }
}
