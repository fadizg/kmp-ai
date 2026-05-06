package io.github.fadizg.kmpai.llm

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.addValue
import platform.Foundation.dataWithContentsOfFile
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class IosModelRepository(
    private val cacheDirUrl: NSURL,
) : ModelRepository {

    init {
        ensureDir(cacheDirUrl)
    }

    override fun resolve(source: ModelSource): Flow<DownloadProgress> {
        if (source is ModelSource.LocalFile) {
            return flow { emit(DownloadProgress.Done(source.path)) }
        }
        val targetUrl = targetUrl(source)
        val targetPath = targetUrl.path ?: error("target has no path")
        if (NSFileManager.defaultManager.fileExistsAtPath(targetPath)) {
            return flow { emit(DownloadProgress.Done(targetPath)) }
        }
        val expectedSha = (source as? ModelSource.Url)?.sha256
        val sourceUrlString = urlFor(source)

        return callbackFlow {
            val parentUrl = targetUrl.URLByDeletingLastPathComponent
            if (parentUrl != null) ensureDir(parentUrl)

            val delegate = DownloadDelegate(
                onProgress = { written, total ->
                    trySendBlocking(DownloadProgress.Running(written, total))
                },
                onComplete = { tempPath, error ->
                    when {
                        error != null -> {
                            trySendBlocking(DownloadProgress.Failed(
                                ModelDownloadException("network error: ${error.localizedDescription}"),
                            ))
                        }
                        tempPath == null -> {
                            trySendBlocking(DownloadProgress.Failed(
                                ModelDownloadException("download produced no file"),
                            ))
                        }
                        else -> {
                            if (expectedSha != null) {
                                val actual = sha256OfFile(tempPath)
                                if (!actual.equals(expectedSha, ignoreCase = true)) {
                                    NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
                                    trySendBlocking(DownloadProgress.Failed(
                                        ModelDownloadException("sha256 mismatch: expected $expectedSha, got $actual"),
                                    ))
                                    close()
                                    return@DownloadDelegate
                                }
                            }
                            NSFileManager.defaultManager.removeItemAtPath(targetPath, null)
                            val moved = NSFileManager.defaultManager.moveItemAtPath(
                                srcPath = tempPath, toPath = targetPath, error = null,
                            )
                            if (moved) trySendBlocking(DownloadProgress.Done(targetPath))
                            else trySendBlocking(DownloadProgress.Failed(
                                ModelDownloadException("failed to move $tempPath -> $targetPath"),
                            ))
                        }
                    }
                    close()
                },
            )

            val nsUrl = NSURL.URLWithString(sourceUrlString)
                ?: throw ModelDownloadException("invalid url: $sourceUrlString")
            val request = NSMutableURLRequest.requestWithURL(nsUrl)
            (source as? ModelSource.HuggingFace)?.auth?.let { auth ->
                when (auth) {
                    is HuggingFaceAuth.Token ->
                        request.addValue("Bearer ${auth.token}", forHTTPHeaderField = "Authorization")
                }
            }
            val session = NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.ephemeralSessionConfiguration,
                delegate,
                delegateQueue = null,
            )
            val task = session.downloadTaskWithRequest(request)
            task.resume()

            awaitClose {
                task.cancel()
                session.invalidateAndCancel()
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun path(source: ModelSource): String {
        if (source is ModelSource.LocalFile) return source.path
        return when (val end = resolve(source).last()) {
            is DownloadProgress.Done -> end.path
            is DownloadProgress.Failed -> throw ModelDownloadException(
                "failed to resolve ${source.id}", end.error,
            )
            is DownloadProgress.Running -> error("unreachable: flow ended on Running")
        }
    }

    override suspend fun remove(source: ModelSource) {
        val target = targetUrl(source).path ?: return
        NSFileManager.defaultManager.removeItemAtPath(target, null)
    }

    override suspend fun list(): List<CachedModel> = withContext(Dispatchers.Default) {
        val basePath = cacheDirUrl.path ?: return@withContext emptyList()
        val mgr = NSFileManager.defaultManager
        val out = mutableListOf<CachedModel>()
        val enumerator = mgr.enumeratorAtPath(basePath) ?: return@withContext emptyList()
        while (true) {
            val rel = enumerator.nextObject() as? String ?: break
            if (rel.endsWith(".part")) continue
            val full = "$basePath/$rel"
            val attrs = mgr.attributesOfItemAtPath(full, null) ?: continue
            val isDir = (attrs["NSFileType"] as? String) == "NSFileTypeDirectory"
            if (isDir) continue
            val size = (attrs["NSFileSize"] as? Number)?.toLong() ?: 0L
            out += CachedModel(id = rel, path = full, sizeBytes = size)
        }
        out
    }

    private fun targetUrl(source: ModelSource): NSURL = when (source) {
        is ModelSource.HuggingFace -> cacheDirUrl
            .URLByAppendingPathComponent("hf")!!
            .URLByAppendingPathComponent(source.repo.replace('/', '_'))!!
            .URLByAppendingPathComponent(source.revision)!!
            .URLByAppendingPathComponent(source.file)!!
        is ModelSource.Url -> cacheDirUrl
            .URLByAppendingPathComponent("url")!!
            .URLByAppendingPathComponent(sha256Hex(source.url))!!
            .URLByAppendingPathComponent(
                source.url.substringAfterLast('/').ifBlank { "model.bin" },
            )!!
        is ModelSource.LocalFile -> NSURL.fileURLWithPath(source.path)
    }

    private fun urlFor(source: ModelSource): String = when (source) {
        is ModelSource.HuggingFace -> source.url
        is ModelSource.Url -> source.url
        is ModelSource.LocalFile -> error("local file has no URL")
    }

    private fun ensureDir(url: NSURL) {
        val path = url.path ?: return
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
            NSFileManager.defaultManager.createDirectoryAtPath(
                path = path, withIntermediateDirectories = true, attributes = null, error = null,
            )
        }
    }

    @OptIn(BetaInteropApi::class)
    private fun sha256Hex(input: String): String {
        val bytes = input.encodeToByteArray()
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        bytes.usePinned { src ->
            digest.usePinned { dst ->
                CC_SHA256(src.addressOf(0), bytes.size.toUInt(), dst.addressOf(0).reinterpret())
            }
        }
        return digest.toHex()
    }

    @OptIn(BetaInteropApi::class)
    private fun sha256OfFile(path: String): String {
        val data = NSData.dataWithContentsOfFile(path) ?: return ""
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        digest.usePinned { dst ->
            CC_SHA256(data.bytes, data.length.toUInt(), dst.addressOf(0).reinterpret())
        }
        return digest.toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> ((byte.toInt() and 0xff)).toString(16).padStart(2, '0') }
}

@OptIn(ExperimentalForeignApi::class)
private class DownloadDelegate(
    private val onProgress: (Long, Long?) -> Unit,
    private val onComplete: (tempPath: String?, error: NSError?) -> Unit,
) : NSObject(), NSURLSessionDownloadDelegateProtocol {

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        onComplete(didFinishDownloadingToURL.path, null)
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        val total = if (totalBytesExpectedToWrite > 0) totalBytesExpectedToWrite else null
        onProgress(totalBytesWritten, total)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        if (didCompleteWithError != null) {
            onComplete(null, didCompleteWithError)
        }
    }
}
