package io.github.ajitkumarmaurya.imdbkt.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class TieredCache(
    private val memoryCache: Cache<String, String>,
    private val diskCache: Cache<String, String>? = null
) : Cache<String, String> {

    private val keyLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun get(key: String): String? {
        memoryCache.get(key)?.let { return it }
        val fromDisk = diskCache?.get(key) ?: return null
        memoryCache.put(key, fromDisk)
        return fromDisk
    }

    override suspend fun put(key: String, value: String, ttlMs: Long?) {
        memoryCache.put(key, value, ttlMs)
        diskCache?.put(key, value, ttlMs)
    }

    override suspend fun remove(key: String) {
        memoryCache.remove(key)
        diskCache?.remove(key)
    }

    override suspend fun clear() {
        memoryCache.clear()
        diskCache?.clear()
    }

    suspend fun getOrPut(key: String, ttlMs: Long? = null, loader: suspend () -> String?): String? {
        get(key)?.let { return it }
        val mutex = keyLocks.computeIfAbsent(key) { Mutex() }
        return try {
            mutex.withLock {
                get(key)?.let { return@withLock it }
                val loaded = loader()
                if (loaded != null) put(key, loaded, ttlMs)
                loaded
            }
        } finally {
            keyLocks.remove(key, mutex)
        }
    }
}
