package io.github.fadizg.kmpai.catalog

import io.github.fadizg.kmpai.llm.ChatTemplate
import io.github.fadizg.kmpai.llm.ModelSource

/**
 * Curated [ModelSource]s for Alibaba's Qwen models, paired with a recommended
 * [ChatTemplate]. All entries point at GGUF artifacts on Hugging Face.
 */
object Qwen {

    val template: ChatTemplate get() = ChatTemplate.ChatML

    /** Qwen2.5 0.5B Instruct, Q4_K_M quant (~350 MB). Smallest practical Qwen. */
    val Qwen2_5_0_5B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
        file = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
    )

    /** Qwen2.5 1.5B Instruct, Q4_K_M quant (~1.0 GB). */
    val Qwen2_5_1_5B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
        file = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
    )

    /** Qwen2.5 3B Instruct, Q4_K_M quant (~1.9 GB). */
    val Qwen2_5_3B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "Qwen/Qwen2.5-3B-Instruct-GGUF",
        file = "qwen2.5-3b-instruct-q4_k_m.gguf",
    )

    /** Qwen2.5 7B Instruct, Q4_K_M quant (~4.7 GB). */
    val Qwen2_5_7B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "Qwen/Qwen2.5-7B-Instruct-GGUF",
        file = "qwen2.5-7b-instruct-q4_k_m.gguf",
    )

    /** Qwen2.5 Coder 1.5B Instruct, Q4_K_M quant — for code completion / chat. */
    val Qwen2_5_Coder_1_5B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF",
        file = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
    )
}
