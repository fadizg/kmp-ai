package io.github.fadizg.kmpai.llm

internal fun interface TokenCallback {
    fun onToken(text: String): Boolean
}

internal object LlamaNative {

    init {
        System.loadLibrary("kmpai_llama")
    }

    @JvmStatic
    external fun nativeLoadModel(
        modelPath: String,
        ctxSize: Int,
        gpuLayers: Int,
        threads: Int,
    ): Long

    @JvmStatic
    external fun nativeFreeModel(handle: Long)

    @JvmStatic
    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float,
        seed: Int,
        stops: Array<String>,
        grammar: String?,
        reuseCache: Boolean,
        callback: TokenCallback,
    )

    @JvmStatic
    external fun nativeTokenize(handle: Long, text: String): IntArray

    @JvmStatic
    external fun nativeCountTokens(handle: Long, text: String): Int

    @JvmStatic
    external fun nativeEmbed(handle: Long, text: String): FloatArray

    @JvmStatic
    external fun nativeContextSize(handle: Long): Int

    @JvmStatic
    external fun nativeResetCache(handle: Long)
}
