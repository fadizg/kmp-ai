package io.github.fadizg.kmpai.llm

data class ModelInfo(
    val name: String,
    val contextSize: Int,
    val backend: Backend,
)

enum class Backend { CPU, METAL, VULKAN, CUDA }
