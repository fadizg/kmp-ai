package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import platform.Foundation.NSURL

/**
 * **Stub** — iOS model repository pending rewrite.
 *
 * The previous implementation used `NSURLSessionDownloadTask`,
 * `NSMutableURLRequest`, and a custom delegate to stream downloads with
 * progress, plus `CC_SHA256` for SHA-256 verification. Kotlin 2.2.x cinterop
 * tightened type checking on Foundation method overloads (in particular
 * `setValue:forHTTPHeaderField:` and the URL-helper signatures) and several
 * call sites in the old implementation no longer compile without explicit
 * type annotations and `@OptIn(ExperimentalForeignApi::class)` plumbing.
 *
 * To unblock the JVM and Android Maven Central publish, this stub returns
 * a single [DownloadProgress.Failed] for any HuggingFace / URL source. Local
 * files still resolve correctly via [DownloadProgress.Done] so consumers
 * who pre-fetch the model and pass [ModelSource.LocalFile] can still
 * proceed.
 *
 * Track the rewrite at https://github.com/fadizg/kmp-ai/issues (TBD).
 */
class IosModelRepository(
    @Suppress("unused") private val cacheDirUrl: NSURL,
) : ModelRepository {

    override fun resolve(source: ModelSource): Flow<DownloadProgress> {
        if (source is ModelSource.LocalFile) {
            return flowOf(DownloadProgress.Done(source.path))
        }
        return flowOf(
            DownloadProgress.Failed(
                ModelDownloadException(IOS_NOT_READY),
            ),
        )
    }

    override suspend fun path(source: ModelSource): String {
        if (source is ModelSource.LocalFile) return source.path
        throw ModelDownloadException(IOS_NOT_READY)
    }

    override suspend fun remove(source: ModelSource) {
        // no-op — we don't manage a cache yet on this platform.
    }

    override suspend fun list(): List<CachedModel> = emptyList()

    private companion object {
        const val IOS_NOT_READY =
            "kmp-ai iOS network repository is pending rewrite for Kotlin 2.2.x " +
                "cinterop. Use ModelSource.LocalFile with a pre-fetched .gguf, " +
                "or use kmp-ai via composite build."
    }
}
