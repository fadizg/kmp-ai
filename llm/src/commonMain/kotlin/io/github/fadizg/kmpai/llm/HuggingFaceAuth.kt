package io.github.fadizg.kmpai.llm

/**
 * Authentication for Hugging Face requests. Required for gated repos
 * (e.g. anything under the `google` org for Gemma checkpoints) and useful
 * for private models.
 *
 * Get a token at https://huggingface.co/settings/tokens — `read` scope is
 * sufficient.
 */
sealed interface HuggingFaceAuth {
    /** A user access token. Sent as `Authorization: Bearer <token>`. */
    data class Token(val token: String) : HuggingFaceAuth
}
