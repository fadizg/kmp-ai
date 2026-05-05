package io.github.fadizg.kmpai.llm

import android.os.Build

@ExperimentalKmpAiApi
actual fun LlmEnvironment.Companion.capability(): DeviceCapability {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return DeviceCapability.Unsupported(DeviceCapability.Reason.OS_VERSION_TOO_OLD)
    }
    val abis = Build.SUPPORTED_ABIS.orEmpty().toSet()
    val supportedAbi = abis.any { it == "arm64-v8a" || it == "x86_64" }
    if (!supportedAbi) {
        return DeviceCapability.Unsupported(DeviceCapability.Reason.ARCHITECTURE_UNSUPPORTED)
    }
    val nativeAvailable = runCatching { System.loadLibrary("kmpai_llama") }.isSuccess
    if (!nativeAvailable) {
        return DeviceCapability.Unsupported(DeviceCapability.Reason.NATIVE_LIB_UNAVAILABLE)
    }
    return DeviceCapability.Available(setOf(Backend.CPU))
}
