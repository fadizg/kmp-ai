package io.github.fadizg.kmpai.llm

@ExperimentalKmpAiApi
internal actual fun checkDownloadConstraints(
    constraints: DownloadConstraints,
): ConstraintNotMetException? = null  // JVM treats both checks as met (desktop always plugged in / wired).
