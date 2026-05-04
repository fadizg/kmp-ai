package io.github.fadizg.kmpai.catalog

import io.github.fadizg.kmpai.llm.ChatTemplate
import io.github.fadizg.kmpai.llm.ModelSource

/**
 * Curated [ModelSource]s for Google's Gemma family. All entries point at GGUF
 * artifacts on Hugging Face. Note that gated repos (anything under `google/`)
 * require a Hugging Face access token in your `Authorization: Bearer ...`
 * header — wire that into your own [io.github.fadizg.kmpai.llm.ModelRepository]
 * if needed.
 */
object Gemma {

    val template: ChatTemplate get() = ChatTemplate.Gemma

    /** Gemma 2 2B Instruct, Q4_K_M quant (~1.6 GB) — bartowski mirror, public. */
    val Gemma2_2B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "bartowski/gemma-2-2b-it-GGUF",
        file = "gemma-2-2b-it-Q4_K_M.gguf",
    )

    /** Gemma 2 9B Instruct, Q4_K_M quant (~5.7 GB). */
    val Gemma2_9B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "bartowski/gemma-2-9b-it-GGUF",
        file = "gemma-2-9b-it-Q4_K_M.gguf",
    )

    /** Gemma 3 1B Instruct, Q4_K_M quant (~750 MB). */
    val Gemma3_1B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "bartowski/google_gemma-3-1b-it-GGUF",
        file = "google_gemma-3-1b-it-Q4_K_M.gguf",
    )

    /** Gemma 3 4B Instruct, Q4_K_M quant (~2.5 GB). */
    val Gemma3_4B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "bartowski/google_gemma-3-4b-it-GGUF",
        file = "google_gemma-3-4b-it-Q4_K_M.gguf",
    )
}
