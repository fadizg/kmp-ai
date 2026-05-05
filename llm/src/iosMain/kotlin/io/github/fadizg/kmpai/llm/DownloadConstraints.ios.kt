package io.github.fadizg.kmpai.llm

import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState

@ExperimentalKmpAiApi
internal actual fun checkDownloadConstraints(
    constraints: DownloadConstraints,
): ConstraintNotMetException? {
    if (constraints.requiresCharging) {
        UIDevice.currentDevice.batteryMonitoringEnabled = true
        val state = UIDevice.currentDevice.batteryState
        // Kotlin 2.2.x exposes the constants as UIDeviceBatteryState enum values
        // rather than top-level Ints.
        if (state != UIDeviceBatteryState.UIDeviceBatteryStateCharging &&
            state != UIDeviceBatteryState.UIDeviceBatteryStateFull
        ) {
            return ConstraintNotMetException("download blocked: requires charging (requiresCharging=true)")
        }
    }
    // wifiOnly on iOS isn't enforced here — set
    // NSURLSessionConfiguration.allowsCellularAccess = false on the session
    // you pass to IosModelRepository if you need a hard guarantee.
    return null
}
