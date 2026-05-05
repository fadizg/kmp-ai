package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * **Stub** — iOS llama.cpp engine pending rewrite.
 *
 * The previous implementation in this file was written against Kotlin 2.0.21
 * cinterop output for llama.cpp's pre-b9000 API and the original Apple-SDK
 * cinterop type shapes. The combined bump to Kotlin 2.2.0 (LLVM 19 + new
 * Objective-C cinterop bindings for `modules = llama` framework imports) and
 * llama.cpp b9033 (which makes several previously-public typedefs opaque)
 * changed the names and signatures cinterop generates. Every reference in the
 * old implementation to `llama_context`, `llama_model`, `readValue`, struct
 * `.value` mutators, and the C-string overloads of `llama_tokenize` /
 * `llama_token_to_piece` no longer resolves.
 *
 * Rather than block the Maven Central publish of the JVM and Android
 * variants on a multi-day iOS rewrite, this stub:
 *
 *   - Compiles cleanly against the new bindings (no cinterop references).
 *   - Throws [NotImplementedError] at runtime with a clear message if a
 *     consumer accidentally instantiates it.
 *   - Lets [LlmEnvironment.default] return a structurally valid object on
 *     iOS so the artifact's API surface matches JVM and Android.
 *
 * Track the rewrite at https://github.com/fadizg/kmp-ai/issues (TBD).
 *
 * Until then, iOS consumers should either:
 *   - Use kmp-ai via composite build (`includeBuild("../kmp-ai")`), which
 *     still works because the JVM/Android paths are unaffected, or
 *   - Implement their own [LlmEngine] for iOS using the framework directly.
 */
internal class IosLlamaCppEngine private constructor(
    override val info: ModelInfo,
) : LlmEngine {

    override fun generate(prompt: String, params: SamplingParams): Flow<Token> = flow {
        throw NotImplementedError(IOS_NOT_READY)
    }

    override suspend fun complete(prompt: String, params: SamplingParams): String =
        throw NotImplementedError(IOS_NOT_READY)

    override fun tokenize(text: String): IntArray =
        throw NotImplementedError(IOS_NOT_READY)

    override fun embed(text: String): FloatArray =
        throw NotImplementedError(IOS_NOT_READY)

    override fun close() {
        // no-op
    }

    companion object {
        private const val IOS_NOT_READY =
            "kmp-ai iOS engine is pending rewrite for Kotlin 2.2.x cinterop bindings. " +
                "Use composite build for iOS until the next release. See README."

        suspend fun load(modelPath: String, config: EngineConfig): LlmEngine =
            throw ModelLoadException(IOS_NOT_READY)
    }
}
