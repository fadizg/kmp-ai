package io.github.fadizg.kmpai.llm

data class EngineConfig(
    val contextSize: Int = 2048,
    val gpuLayers: Int = 0,
    val threads: Int? = null,
    val mmap: Boolean = true,
) {
    companion object {
        /**
         * Conservative settings for low-RAM devices (≤ 2 GB free RAM, older
         * mid-range Androids, iPhone SE class). Smaller context, mmap on, no
         * GPU.
         */
        fun lowMemory(): EngineConfig = EngineConfig(
            contextSize = 512,
            gpuLayers = 0,
            mmap = true,
        )

        /** Default. Suitable for most modern phones / laptops. */
        fun balanced(): EngineConfig = EngineConfig(
            contextSize = 2048,
            gpuLayers = 0,
            mmap = true,
        )

        /**
         * Larger context + opportunistic GPU offload. Use on flagship phones,
         * tablets, and desktops with discrete GPUs / Apple Silicon.
         */
        fun highCapacity(): EngineConfig = EngineConfig(
            contextSize = 4096,
            gpuLayers = -1, // -1 = let llama.cpp pick the maximum it can fit
            mmap = true,
        )
    }
}
