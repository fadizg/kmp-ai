package io.github.fadizg.kmpai.llm

import platform.UIKit.UIDevice

@ExperimentalKmpAiApi
actual fun LlmEnvironment.Companion.capability(): DeviceCapability {
    // iOS minimum is 14.0; if a build lands on an older OS that's a packaging
    // bug, but check anyway to avoid surprising crashes.
    val systemVersion = UIDevice.currentDevice.systemVersion
    val major = systemVersion.substringBefore('.').toIntOrNull() ?: 0
    if (major < 14) {
        return DeviceCapability.Unsupported(DeviceCapability.Reason.OS_VERSION_TOO_OLD)
    }
    // The framework is statically linked, so if we got this far the native
    // entry points are present. Metal is the standard backend.
    return DeviceCapability.Available(setOf(Backend.METAL))
}
