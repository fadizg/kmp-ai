package io.github.fadizg.kmpai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

class DefaultModelRepository(
    private val cacheDir: Path,
    private val httpClient: HttpClient = defaultClient(),
) : ModelRepository {

    init {
        Files.createDirectories(cacheDir)
    }

    override fun resolve(source: ModelSource): Flow<DownloadProgress> = flow {
        if (source is ModelSource.LocalFile) {
            emit(DownloadProgress.Done(source.path))
            return@flow
        }

        val target = targetPath(source)
        if (Files.exists(target)) {
            emit(DownloadProgress.Done(target.toString()))
            return@flow
        }

        val url = urlFor(source)
        val expectedSha = (source as? ModelSource.Url)?.sha256

        Files.createDirectories(target.parent)
        val tmp = target.resolveSibling(target.fileName.toString() + ".part")
        Files.deleteIfExists(tmp)

        try {
            val request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", "kmp-ai/0.1")
                .build()
            val response: HttpResponse<java.io.InputStream> =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in 200..299) {
                throw IOException("HTTP ${response.statusCode()} for $url")
            }
            val total = response.headers().firstValueAsLong("content-length").let {
                if (it.isPresent) it.asLong else -1L
            }.takeIf { it > 0 }

            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            response.body().use { input ->
                Files.newOutputStream(tmp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        written += n
                        emit(DownloadProgress.Running(written, total))
                    }
                }
            }

            if (expectedSha != null) {
                val actual = digest.digest().toHex()
                if (!actual.equals(expectedSha, ignoreCase = true)) {
                    Files.deleteIfExists(tmp)
                    throw ModelDownloadException("sha256 mismatch: expected $expectedSha, got $actual")
                }
            }

            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            emit(DownloadProgress.Done(target.toString()))
        } catch (t: Throwable) {
            Files.deleteIfExists(tmp)
            emit(DownloadProgress.Failed(t))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun path(source: ModelSource): String {
        if (source is ModelSource.LocalFile) return source.path
        return when (val end = resolve(source).last()) {
            is DownloadProgress.Done -> end.path
            is DownloadProgress.Failed -> throw ModelDownloadException(
                "failed to resolve ${source.id}",
                end.cause,
            )
            is DownloadProgress.Running -> error("unreachable: flow ended on Running")
        }
    }

    override suspend fun remove(source: ModelSource) {
        val target = targetPath(source)
        Files.deleteIfExists(target)
        val parent = target.parent
        if (parent != null && parent != cacheDir && Files.isDirectory(parent)) {
            Files.list(parent).use { stream ->
                if (stream.findAny().isEmpty) Files.deleteIfExists(parent)
            }
        }
    }

    override fun list(): List<CachedModel> {
        if (!Files.isDirectory(cacheDir)) return emptyList()
        val out = mutableListOf<CachedModel>()
        Files.walk(cacheDir).use { walk ->
            walk.filter { Files.isRegularFile(it) && !it.fileName.toString().endsWith(".part") }
                .forEach {
                    out += CachedModel(
                        id = cacheDir.relativize(it).toString(),
                        path = it.toString(),
                        sizeBytes = Files.size(it),
                    )
                }
        }
        return out
    }

    private fun targetPath(source: ModelSource): Path = when (source) {
        is ModelSource.HuggingFace -> cacheDir
            .resolve("hf")
            .resolve(source.repo.replace('/', '_'))
            .resolve(source.revision)
            .resolve(source.file)
        is ModelSource.Url -> cacheDir
            .resolve("url")
            .resolve(source.url.sha256Hex())
            .resolve(source.url.substringAfterLast('/').ifBlank { "model.bin" })
        is ModelSource.LocalFile -> Path.of(source.path)
    }

    private fun urlFor(source: ModelSource): String = when (source) {
        is ModelSource.HuggingFace -> source.url
        is ModelSource.Url -> source.url
        is ModelSource.LocalFile -> error("local file has no URL")
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.sha256Hex(): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build()

        fun userCacheDir(): Path {
            val home = System.getProperty("user.home")
            val xdg = System.getenv("XDG_CACHE_HOME")
            val base = when {
                !xdg.isNullOrBlank() -> Path.of(xdg)
                System.getProperty("os.name").lowercase().contains("mac") ->
                    Path.of(home, "Library", "Caches")
                System.getProperty("os.name").lowercase().contains("windows") ->
                    Path.of(System.getenv("LOCALAPPDATA") ?: "$home/AppData/Local")
                else -> Path.of(home, ".cache")
            }
            return base.resolve("kmp-ai").resolve("models")
        }
    }
}
