package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class DefaultModelRepository(
    private val cacheDir: File,
    private val userAgent: String = "kmp-ai/0.1",
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000,
) : ModelRepository {

    init {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw IOException("could not create cache dir: $cacheDir")
        }
    }

    constructor(cacheDirPath: String) : this(File(cacheDirPath))

    override fun resolve(source: ModelSource): Flow<DownloadProgress> = flow {
        if (source is ModelSource.LocalFile) {
            emit(DownloadProgress.Done(source.path))
            return@flow
        }

        val target = targetFile(source)
        if (target.exists()) {
            emit(DownloadProgress.Done(target.absolutePath))
            return@flow
        }

        val expectedSha = (source as? ModelSource.Url)?.sha256

        target.parentFile?.takeIf { !it.exists() }?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        // If a partial download is on disk, try to resume from where it left
        // off. If the server doesn't honour Range we fall back to a fresh
        // start (handled below).
        val resumeFrom = if (tmp.isFile) tmp.length() else 0L

        try {
            val connection = openWithRedirects(
                urlFor(source),
                authFor(source),
                rangeStart = resumeFrom,
            )
            val resumed = resumeFrom > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (resumeFrom > 0L && !resumed) {
                // Server didn't give us a partial response — discard the
                // stale `.part` and start over.
                tmp.delete()
            }

            // For SHA verification we need a digest over the full file. If we
            // resumed, prime the digest with bytes already on disk.
            val digest = if (expectedSha != null) MessageDigest.getInstance("SHA-256") else null
            if (digest != null && resumed) {
                tmp.inputStream().use { existing ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = existing.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                    }
                }
            }

            // contentLengthLong is the size of *this* response body. For a
            // 206 Partial that's only the remaining bytes — add what's on
            // disk to get the full size for progress reporting.
            val responseLen = runCatching { connection.contentLengthLong }.getOrDefault(-1L)
            val total: Long? = when {
                responseLen <= 0L -> null
                resumed -> responseLen + resumeFrom
                else -> responseLen
            }

            var written = if (resumed) resumeFrom else 0L
            try {
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(tmp, /* append = */ resumed).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest?.update(buffer, 0, n)
                            written += n
                            emit(DownloadProgress.Running(written, total))
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (expectedSha != null && digest != null) {
                val actual = digest.digest().toHex()
                if (!actual.equals(expectedSha, ignoreCase = true)) {
                    tmp.delete()
                    throw ModelDownloadException("sha256 mismatch: expected $expectedSha, got $actual")
                }
            }

            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            emit(DownloadProgress.Done(target.absolutePath))
        } catch (t: Throwable) {
            // Keep the .part file on transient failure so the next call can
            // resume. Only nuke it on a verification failure (handled above).
            emit(DownloadProgress.Failed(t))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun path(source: ModelSource): String {
        if (source is ModelSource.LocalFile) return source.path
        return when (val end = resolve(source).last()) {
            is DownloadProgress.Done -> end.path
            is DownloadProgress.Failed -> throw ModelDownloadException(
                "failed to resolve ${source.id}",
                end.error,
            )
            is DownloadProgress.Running -> error("unreachable: flow ended on Running")
        }
    }

    override suspend fun remove(source: ModelSource) {
        val target = targetFile(source)
        target.delete()
        File(target.parentFile, target.name + ".part").delete()
        val parent = target.parentFile
        if (parent != null && parent != cacheDir && parent.isDirectory && parent.list().isNullOrEmpty()) {
            parent.delete()
        }
    }

    override suspend fun pathIfCached(source: ModelSource): String? = withContext(Dispatchers.IO) {
        if (source is ModelSource.LocalFile) {
            return@withContext if (java.io.File(source.path).exists()) source.path else null
        }
        val target = targetFile(source)
        if (target.isFile) target.absolutePath else null
    }

    override suspend fun isCached(source: ModelSource): Boolean = withContext(Dispatchers.IO) {
        if (source is ModelSource.LocalFile) java.io.File(source.path).exists()
        else targetFile(source).isFile
    }

    override suspend fun list(): List<CachedModel> = withContext(Dispatchers.IO) {
        if (!cacheDir.isDirectory) return@withContext emptyList()
        val out = mutableListOf<CachedModel>()
        cacheDir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".part") }
            .forEach {
                out += CachedModel(
                    id = it.relativeTo(cacheDir).path.replace(File.separatorChar, '/'),
                    path = it.absolutePath,
                    sizeBytes = it.length(),
                )
            }
        out
    }

    private fun targetFile(source: ModelSource): File = when (source) {
        is ModelSource.HuggingFace -> File(
            File(File(cacheDir, "hf"), source.repo.replace('/', '_')),
            source.revision,
        ).resolve(source.file)
        is ModelSource.Url -> File(File(cacheDir, "url"), source.url.sha256Hex())
            .resolve(source.url.substringAfterLast('/').ifBlank { "model.bin" })
        is ModelSource.LocalFile -> File(source.path)
    }

    private fun urlFor(source: ModelSource): String = when (source) {
        is ModelSource.HuggingFace -> source.url
        is ModelSource.Url -> source.url
        is ModelSource.LocalFile -> error("local file has no URL")
    }

    private fun authFor(source: ModelSource): HuggingFaceAuth? = when (source) {
        is ModelSource.HuggingFace -> source.auth
        else -> null
    }

    private fun openWithRedirects(
        initialUrl: String,
        auth: HuggingFaceAuth? = null,
        rangeStart: Long = 0L,
        maxHops: Int = 5,
    ): HttpURLConnection {
        val authHeader: String? = when (auth) {
            is HuggingFaceAuth.Token -> "Bearer ${auth.token}"
            null -> null
        }
        var url = initialUrl
        var hops = 0
        while (true) {
            val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "*/*")
                if (authHeader != null) setRequestProperty("Authorization", authHeader)
                if (rangeStart > 0L) setRequestProperty("Range", "bytes=$rangeStart-")
            }
            val code = conn.responseCode
            if (code in 300..399 && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("redirect $code without Location at $url")
                if (++hops > maxHops) throw IOException("too many redirects starting at $initialUrl")
                url = if (location.matches(Regex("^https?://.*"))) location else URI.create(url).resolve(location).toString()
                continue
            }
            if (code !in 200..299) {
                val msg = "HTTP $code for $url"
                conn.disconnect()
                throw IOException(msg)
            }
            return conn
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256").digest(toByteArray()).toHex()
}
