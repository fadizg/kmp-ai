package io.github.fadizg.kmpai.llm

/**
 * Marker for kmp-ai APIs that may change in incompatible ways before 1.0.
 *
 * Today this is the policy for *new* APIs added in a given pre-1.0 release —
 * existing API surface that was already published unmarked is treated as
 * "best-effort stable" and we'll do our best to avoid breaking it without a
 * minor-version bump.
 *
 * Consumers acknowledge with `@OptIn(ExperimentalKmpAiApi::class)` at the
 * call site, or project-wide via `freeCompilerArgs += "-opt-in=io.github.fadizg.kmpai.llm.ExperimentalKmpAiApi"`.
 */
@RequiresOptIn(
    message = "This kmp-ai API is experimental and may change in pre-1.0 releases.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalKmpAiApi
