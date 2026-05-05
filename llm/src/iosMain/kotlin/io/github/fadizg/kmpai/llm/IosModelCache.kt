package io.github.fadizg.kmpai.llm

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
object IosModelCache {
    /**
     * Returns `<NSCachesDirectory>/kmp-ai/models`, creating it if needed.
     * On iOS the system may purge `Caches/` under storage pressure; if you
     * need persistence across reboots, point `IosModelRepository` at
     * `NSDocumentDirectory` instead.
     */
    fun userCacheDirUrl(): NSURL {
        val mgr = NSFileManager.defaultManager
        val caches = mgr.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first() as NSURL
        val target = caches.URLByAppendingPathComponent("kmp-ai", isDirectory = true)!!
            .URLByAppendingPathComponent("models", isDirectory = true)!!
        target.path?.let { path ->
            if (!mgr.fileExistsAtPath(path)) {
                mgr.createDirectoryAtPath(
                    path = path, withIntermediateDirectories = true, attributes = null, error = null,
                )
            }
        }
        return target
    }
}
