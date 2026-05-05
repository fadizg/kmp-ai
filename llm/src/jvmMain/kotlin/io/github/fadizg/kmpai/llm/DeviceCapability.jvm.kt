package io.github.fadizg.kmpai.llm

@ExperimentalKmpAiApi
actual fun LlmEnvironment.Companion.capability(): DeviceCapability {
    val arch = System.getProperty("os.arch")?.lowercase().orEmpty()
    val supportedArch = arch in setOf("aarch64", "arm64", "amd64", "x86_64")
    if (!supportedArch) {
        return DeviceCapability.Unsupported(DeviceCapability.Reason.ARCHITECTURE_UNSUPPORTED)
    }
    val backends = buildSet {
        add(Backend.CPU)
        // Apple Silicon hosts get Metal via de.kherud:llama bundling.
        val osName = System.getProperty("os.name")?.lowercase().orEmpty()
        if (osName.contains("mac") && arch == "aarch64") add(Backend.METAL)
    }
    return DeviceCapability.Available(backends)
}
