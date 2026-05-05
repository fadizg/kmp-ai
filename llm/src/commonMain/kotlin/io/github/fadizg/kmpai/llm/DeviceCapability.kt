package io.github.fadizg.kmpai.llm

/**
 * Whether the current device can run kmp-ai.
 *
 * Use it before `LlmEnvironment.default()` to decide whether to surface the
 * LLM feature in your UI at all:
 *
 * ```kotlin
 * when (val cap = LlmEnvironment.capability()) {
 *     is DeviceCapability.Available    -> showAiFeature(cap.backends)
 *     is DeviceCapability.Unsupported  -> hideAiFeature(cap.reason)
 * }
 * ```
 *
 * The check is cheap — it inspects OS version, ABI, and (best-effort)
 * native-library presence. It does not load the model or initialise the
 * inference engine.
 */
@ExperimentalKmpAiApi
sealed interface DeviceCapability {
    data class Available(val backends: Set<Backend>) : DeviceCapability

    data class Unsupported(val reason: Reason) : DeviceCapability

    enum class Reason {
        /** Android API level / iOS version below the minimum supported. */
        OS_VERSION_TOO_OLD,

        /** CPU architecture isn't one of the ABIs we ship natives for. */
        ARCHITECTURE_UNSUPPORTED,

        /** Native library couldn't be loaded (likely a packaging issue). */
        NATIVE_LIB_UNAVAILABLE,

        /** Catch-all for platform-specific causes. */
        UNKNOWN,
    }
}

/**
 * Inspect the current device's ability to run kmp-ai. See [DeviceCapability]
 * for usage.
 */
@ExperimentalKmpAiApi
expect fun LlmEnvironment.Companion.capability(): DeviceCapability
