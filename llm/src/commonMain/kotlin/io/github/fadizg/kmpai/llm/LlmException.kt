package io.github.fadizg.kmpai.llm

open class LlmException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ModelLoadException(message: String, cause: Throwable? = null) : LlmException(message, cause)

class GenerationException(message: String, cause: Throwable? = null) : LlmException(message, cause)

class ModelDownloadException(message: String, cause: Throwable? = null) : LlmException(message, cause)
