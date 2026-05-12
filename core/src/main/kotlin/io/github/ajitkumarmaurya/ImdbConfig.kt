package io.github.ajitkumarmaurya.imdbkt

import io.github.ajitkumarmaurya.imdbkt.cache.Cache
import io.github.ajitkumarmaurya.imdbkt.cache.DiskCache
import io.github.ajitkumarmaurya.imdbkt.cache.MemoryCache
import io.github.ajitkumarmaurya.imdbkt.cache.TieredCache
import java.io.File

/**
 * Configuration for the [Imdb] client.
 *
 * All parameters have sensible defaults; override only what you need.
 *
 * ```kotlin
 * val config = ImdbConfig(
 *     enableLogging = BuildConfig.DEBUG,
 *     cacheDir = context.cacheDir,
 *     maxRequestsPerSecond = 1,
 * )
 * val imdb = Imdb(config)
 * ```
 */
data class ImdbConfig(
    /** OkHttp connect timeout in seconds. */
    val connectTimeoutSeconds: Long = 15,
    /** OkHttp read timeout in seconds. */
    val readTimeoutSeconds: Long = 30,
    /** OkHttp write timeout in seconds. */
    val writeTimeoutSeconds: Long = 15,
    /** Max concurrent requests before rate-limiter blocks. */
    val maxRequestsPerSecond: Int = 2,
    /** Number of retry attempts on transient failures. */
    val maxRetries: Int = 3,
    /** Enable OkHttp request/response logging (disable in production). */
    val enableLogging: Boolean = false,
    /** Directory for disk cache.  Pass `null` to use memory-only caching. */
    val cacheDir: File? = null,
    /** Memory cache TTL in milliseconds. */
    val memoryCacheTtlMs: Long = MemoryCache.DEFAULT_TTL_MS,
    /** Disk cache TTL in milliseconds. */
    val diskCacheTtlMs: Long = DiskCache.DEFAULT_TTL_MS,
    /** Max entries to hold in the in-memory LRU. */
    val memoryCacheMaxSize: Int = MemoryCache.DEFAULT_MAX_SIZE,
) {
    internal fun buildCache(): Cache {
        val memory = MemoryCache(memoryCacheMaxSize, memoryCacheTtlMs)
        val dir = cacheDir
        return if (dir != null) {
            TieredCache(memory, DiskCache(File(dir, "imdb-kt"), diskCacheTtlMs))
        } else {
            memory
        }
    }
}
