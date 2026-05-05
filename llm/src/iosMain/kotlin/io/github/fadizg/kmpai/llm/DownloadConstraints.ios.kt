package io.github.fadizg.kmpai.llm

import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryStateCharging
import platform.UIKit.UIDeviceBatteryStateFull

@ExperimentalKmpAiApi
internal actual fun checkDownloadConstraints(
    constraints: DownloadConstraints,
): ConstraintNotMetException? {
    if (constraints.requiresCharging) {
        UIDevice.currentDevice.batteryMonitoringEnabled = true
        val state = UIDevice.currentDevice.batteryState
        if (state != UIDeviceBatteryStateCharging && state != UIDeviceBatteryStateFull) {
            return ConstraintNotMetException("download blocked: requires charging (requiresCharging=true)")
        }
    }
    // wifiOnly on iOS isn't enforced here — set
    // NSURLSessionConfiguration.allowsCellularAccess = false on the session
    // you pass to IosModelRepository if you need a hard guarantee.
    return null
}
