package io.github.ajitkumarmaurya.imdbkt.cache

import java.util.concurrent.ConcurrentHashMap

/**
 * LRU-like in-memory cache with a configurable TTL per entry and max size.
 */
class MemoryCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) : Cache {

    private data class Entry(val value: String, val expiresAt: Long)

    private val store = ConcurrentHashMap<String, Entry>(maxSize)

    override fun get(key: String): String? {
        val entry = store[key] ?: return null
        val expired = System.currentTimeMillis() > entry.expiresAt
        if (expired) store.remove(key)
        return entry.value.takeUnless { expired }
    }

    override fun put(key: String, value: String) {
        if (store.size >= maxSize) evictOldest()
        store[key] = Entry(value, System.currentTimeMillis() + ttlMs)
    }

    override fun remove(key: String) {
        store.remove(key)
    }

    override fun clear() = store.clear()

    private fun evictOldest() {
        val now = System.currentTimeMillis()
        // Remove all expired first
        store.entries.removeIf { it.value.expiresAt <= now }
        // If still over capacity, remove the earliest-expiring entry
        if (store.size >= maxSize) {
            store.entries.minByOrNull { it.value.expiresAt }?.key?.let { store.remove(it) }
        }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 100
        const val DEFAULT_TTL_MS = 10 * 60 * 1_000L // 10 minutes
    }
}
