package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun resolve(source: ModelSource): Flow<DownloadProgress>

    suspend fun path(source: ModelSource): String

    suspend fun remove(source: ModelSource)

    fun list(): List<CachedModel>
}

data class CachedModel(
    val id: String,
    val path: String,
    val sizeBytes: Long,
)
