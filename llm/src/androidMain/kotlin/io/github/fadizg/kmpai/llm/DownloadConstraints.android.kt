package io.github.fadizg.kmpai.llm

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

@ExperimentalKmpAiApi
internal actual fun checkDownloadConstraints(
    constraints: DownloadConstraints,
): ConstraintNotMetException? {
    if (!constraints.wifiOnly && !constraints.requiresCharging) return null
    val ctx: Context = KmpAiInitializer.appContext ?: return null  // can't check without context, allow

    if (constraints.wifiOnly && isMetered(ctx)) {
        return ConstraintNotMetException("download blocked: requires unmetered network (wifiOnly=true)")
    }
    if (constraints.requiresCharging && !isCharging(ctx)) {
        return ConstraintNotMetException("download blocked: requires charging (requiresCharging=true)")
    }
    return null
}

private fun isMetered(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val active = cm.activeNetwork ?: return true
    val caps = cm.getNetworkCapabilities(active) ?: return true
    return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

private fun isCharging(ctx: Context): Boolean {
    val intent: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
}
