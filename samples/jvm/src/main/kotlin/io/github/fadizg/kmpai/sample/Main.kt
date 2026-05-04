package io.github.fadizg.kmpai.sample

import io.github.fadizg.kmpai.llm.ChatSession
import io.github.fadizg.kmpai.llm.ChatTemplate
import io.github.fadizg.kmpai.llm.DefaultModelRepository
import io.github.fadizg.kmpai.llm.DownloadProgress
import io.github.fadizg.kmpai.llm.EngineConfig
import io.github.fadizg.kmpai.llm.JvmModelCache
import io.github.fadizg.kmpai.llm.LlmEngineFactory
import io.github.fadizg.kmpai.llm.ModelSource
import io.github.fadizg.kmpai.llm.SamplingParams
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val source = parseSource(args) ?: ModelSource.HuggingFace(
        repo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
        file = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
    )

    val repository = DefaultModelRepository(JvmModelCache.userCacheDir())

    print("resolving model... ")
    val modelPath = run {
        var lastPercent = -1
        repository.resolve(source).onEach { progress ->
            when (progress) {
                is DownloadProgress.Running -> {
                    val pct = progress.fraction?.let { (it * 100).toInt() } ?: -1
                    if (pct != lastPercent) {
                        lastPercent = pct
                        print("\rdownloading: ${pct}% (${progress.bytes / 1_000_000} MB)")
                    }
                }
                is DownloadProgress.Done -> println("\nready: ${progress.path}")
                is DownloadProgress.Failed -> {
                    println("\nfailed: ${progress.cause.message}")
                    throw progress.cause
                }
            }
        }.collect()
        repository.path(source)
    }

    val engine = LlmEngineFactory().load(
        modelPath = modelPath,
        config = EngineConfig(contextSize = 2048, gpuLayers = 0),
    )

    engine.use {
        val chat = ChatSession(
            engine = it,
            template = ChatTemplate.ChatML,
            systemPrompt = "You are a concise, helpful assistant. Reply in one short paragraph.",
        )

        val userMessage = args.dropWhile { a -> a.startsWith("--") }.joinToString(" ")
            .ifBlank { "In one sentence, what is Kotlin Multiplatform?" }

        println("\n> $userMessage\n")
        chat.send(userMessage, SamplingParams(maxTokens = 200, temperature = 0.7f))
            .collect { token -> print(token.text); System.out.flush() }
        println()
    }
}

private fun parseSource(args: Array<String>): ModelSource? {
    val map = args.filter { it.startsWith("--") }.associate {
        val (k, v) = it.removePrefix("--").split("=", limit = 2).let { p ->
            if (p.size == 2) p[0] to p[1] else p[0] to ""
        }
        k to v
    }
    return when {
        map["model"] != null -> ModelSource.LocalFile(map.getValue("model"))
        map["hf"] != null && map["file"] != null -> ModelSource.HuggingFace(
            repo = map.getValue("hf"),
            file = map.getValue("file"),
            revision = map["revision"] ?: "main",
        )
        map["url"] != null -> ModelSource.Url(map.getValue("url"), map["sha256"])
        else -> null
    }
}
