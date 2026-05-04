package io.github.fadizg.kmpai.llm

data class EngineConfig(
    val contextSize: Int = 2048,
    val gpuLayers: Int = 0,
    val threads: Int? = null,
    val mmap: Boolean = true,
)
