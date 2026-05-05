package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Conditions under which a download is allowed to start. Pass to
 * `ModelRepository.resolve(source, constraints)` to gate large model
 * downloads on user-friendly defaults.
 *
 * If a constraint isn't met when `resolve(...)` is collected, the flow
 * emits [DownloadProgress.Failed] with [ConstraintNotMetException] and the
 * caller can retry once conditions change.
 *
 * On the JVM target the constraints are no-ops (always treated as met).
 */
@ExperimentalKmpAiApi
data class DownloadConstraints(
    /** If true, download only on a non-metered network (Wi-Fi / Ethernet). */
    val wifiOnly: Boolean = false,
    /** If true, download only while the device is charging. */
    val requiresCharging: Boolean = false,
) {
    companion object {
        /** No constraints — download whenever. Default. */
        val Unrestricted: DownloadConstraints = DownloadConstraints()

        /** Conservative: Wi-Fi + charging. Good default for multi-GB models. */
        val Recommended: DownloadConstraints = DownloadConstraints(
            wifiOnly = true,
            requiresCharging = true,
        )
    }
}

@ExperimentalKmpAiApi
class ConstraintNotMetException(
    message: String,
) : LlmException(message)

/**
 * Internal hook — returns null if [constraints] are met, or a non-null
 * exception describing what's missing. Implemented per-platform.
 */
@ExperimentalKmpAiApi
internal expect fun checkDownloadConstraints(
    constraints: DownloadConstraints,
): ConstraintNotMetException?

/**
 * Resolve a model only if [constraints] are met. If they aren't, the flow
 * emits a single [DownloadProgress.Failed] with [ConstraintNotMetException]
 * and completes — no network is touched.
 */
@ExperimentalKmpAiApi
fun ModelRepository.resolve(
    source: ModelSource,
    constraints: DownloadConstraints,
): Flow<DownloadProgress> = flow {
    val violation = checkDownloadConstraints(constraints)
    if (violation != null) {
        emit(DownloadProgress.Failed(violation))
        return@flow
    }
    emitAll(resolve(source))
}
