package io.github.fadizg.kmpai.catalog

import io.github.fadizg.kmpai.llm.ChatTemplate
import io.github.fadizg.kmpai.llm.ModelSource

/**
 * Curated [ModelSource]s for DeepSeek's R1-distilled reasoning models.
 *
 * R1-Distill takes a base model (Qwen2.5 / Llama 3) and fine-tunes it on
 * traces produced by DeepSeek-R1, the open chain-of-thought reasoning
 * model. The result is a much smaller model that emits explicit
 * `<think>…</think>` blocks before its final answer — useful for
 * step-by-step problem solving, math, and code on-device.
 *
 * Prompting is ChatML-compatible; reuse [Qwen.template]-style
 * `ChatTemplate.ChatML`. To suppress the `<think>` block in user-facing
 * output, parse it out before rendering, or add `"</think>"` to
 * [io.github.fadizg.kmpai.llm.SamplingParams.stop] (this drops the
 * reasoning trace entirely — useful for production, lossy for debugging).
 *
 * All entries point at public Q4_K_M GGUF mirrors — no Hugging Face token
 * required.
 *
 * Model card: https://huggingface.co/deepseek-ai/DeepSeek-R1
 */
object DeepSeek {

    /** Reasoning models distilled from DeepSeek-R1 use ChatML formatting. */
    val template: ChatTemplate get() = ChatTemplate.ChatML

    /**
     * R1 distilled into Qwen2.5-Math-1.5B, Q4_K_M (~1.0 GB).
     *
     * Smallest reasoning model that's realistic on phones (≥4 GB RAM).
     * Runs on Pixel 7 / iPhone 13 class hardware. Strong at math and
     * code, weaker at general chat than the non-distill 1.5B Qwen.
     */
    val R1_Distill_Qwen_1_5B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
        file = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
    )

    /**
     * R1 distilled into Qwen2.5-Math-7B, Q4_K_M (~4.7 GB).
     *
     * Desktop / tablet only. Substantially better reasoning quality than
     * the 1.5B distill. Will OOM on most Android phones.
     */
    val R1_Distill_Qwen_7B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF",
        file = "DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf",
    )

    /**
     * R1 distilled into Llama-3.1-8B, Q4_K_M (~4.9 GB).
     *
     * Desktop / tablet only. An alternative to the 7B Qwen distill —
     * worth trying if your prompts already work well with Llama 3
     * formatting (this still uses ChatML for the distill, but the
     * underlying knowledge cutoff and style differ from the Qwen base).
     */
    val R1_Distill_Llama_8B_Q4: ModelSource = ModelSource.HuggingFace(
        repo = "bartowski/DeepSeek-R1-Distill-Llama-8B-GGUF",
        file = "DeepSeek-R1-Distill-Llama-8B-Q4_K_M.gguf",
    )
}
