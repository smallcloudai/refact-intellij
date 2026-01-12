package com.smallcloud.refactai.utils

import com.intellij.openapi.diagnostic.Logger
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object ResourceCache {
    private val logger = Logger.getInstance(ResourceCache::class.java)
    private const val MAX_SIZE = 10 * 1024 * 1024L
    private const val MAX_SINGLE_ENTRY = 2 * 1024 * 1024L

    private val cache = ConcurrentHashMap<String, CachedResource>()
    private val currentSize = AtomicLong(0)

    data class CachedResource(
        val data: ByteArray,
        val mimeType: String,
        val lastAccess: AtomicLong = AtomicLong(System.currentTimeMillis())
    ) {
        fun touch() = lastAccess.set(System.currentTimeMillis())
        fun createInputStream(): InputStream = ByteArrayInputStream(data)
    }

    fun get(path: String): CachedResource? {
        return cache[path]?.also { it.touch() }
    }

    fun getOrLoad(path: String, loader: () -> InputStream?): CachedResource? {
        cache[path]?.let {
            it.touch()
            return it
        }

        val stream = loader() ?: return null
        return try {
            val data = stream.use { it.readBytes() }
            if (data.size > MAX_SINGLE_ENTRY) {
                logger.debug("Resource too large to cache: $path (${data.size} bytes)")
                return CachedResource(data, guessMimeType(path))
            }
            put(path, data, guessMimeType(path))
        } catch (e: Exception) {
            logger.warn("Failed to cache resource: $path", e)
            null
        }
    }

    fun put(path: String, data: ByteArray, mimeType: String): CachedResource {
        evictIfNeeded(data.size.toLong())
        val resource = CachedResource(data, mimeType)
        val existing = cache.put(path, resource)
        if (existing != null) {
            currentSize.addAndGet(-existing.data.size.toLong())
        }
        currentSize.addAndGet(data.size.toLong())
        logger.debug("Cached resource: $path (${data.size} bytes, total: ${currentSize.get()} bytes)")
        return resource
    }

    private fun evictIfNeeded(newSize: Long) {
        while (currentSize.get() + newSize > MAX_SIZE && cache.isNotEmpty()) {
            val oldest = cache.entries.minByOrNull { it.value.lastAccess.get() } ?: break
            cache.remove(oldest.key)
            currentSize.addAndGet(-oldest.value.data.size.toLong())
            logger.debug("Evicted cached resource: ${oldest.key}")
        }
    }

    private fun guessMimeType(path: String): String = when {
        path.endsWith(".css") -> "text/css"
        path.endsWith(".js") || path.endsWith(".cjs") -> "text/javascript"
        path.endsWith(".html") -> "text/html"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".woff") -> "font/woff"
        path.endsWith(".woff2") -> "font/woff2"
        else -> URLConnection.guessContentTypeFromName(path) ?: "application/octet-stream"
    }

    fun clear() {
        cache.clear()
        currentSize.set(0)
    }

    fun stats(): String = "ResourceCache: ${cache.size} entries, ${currentSize.get()} bytes"
}
