package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    /**
     * Resolve [source] into the local cache. The flow emits
     * [DownloadProgress.Running] updates while data is being downloaded,
     * then [DownloadProgress.Done] with the local path on success or
     * [DownloadProgress.Failed] on error.
     */
    fun resolve(source: ModelSource): Flow<DownloadProgress>

    /**
     * Resolve [source] and return its local path. Suspends until the
     * download completes.
     */
    suspend fun path(source: ModelSource): String

    /**
     * Delete [source] from the cache. No-op if it isn't cached.
     */
    suspend fun remove(source: ModelSource)

    /**
     * Enumerate cached models.
     *
     * Note: [CachedModel.id] is derived from the on-disk path layout and
     * is **not** guaranteed to equal [ModelSource.id] for the same model.
     * In particular, HuggingFace cache ids replace `/` with `_` in the repo
     * and use `/` (a directory level) instead of `@` between repo and
     * revision, so naive `list().any { it.id == source.id }` checks always
     * return false. Use [isCached] / [pathIfCached] instead — those compare
     * via the on-disk target path that [resolve] actually writes to.
     */
    suspend fun list(): List<CachedModel>

    /**
     * Local path of [source] if it has already been resolved into the
     * cache, `null` otherwise. Does not trigger a download.
     *
     * Default implementation returns `null`. Real implementations should
     * override to check the on-disk path that [resolve] would write to.
     */
    suspend fun pathIfCached(source: ModelSource): String? = null

    /**
     * Whether [source] is already cached locally. Default delegates to
     * [pathIfCached]; override for cheaper checks if needed.
     */
    suspend fun isCached(source: ModelSource): Boolean =
        pathIfCached(source) != null
}

data class CachedModel(
    /**
     * Stable identifier for the on-disk cache entry. **Not** guaranteed to
     * match [ModelSource.id] — see [ModelRepository.list] for details.
     * Treat this as opaque; for "is this source cached?" use
     * [ModelRepository.isCached].
     */
    val id: String,
    val path: String,
    val sizeBytes: Long,
)
