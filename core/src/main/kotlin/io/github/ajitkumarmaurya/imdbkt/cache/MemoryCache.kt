package io.github.ajitkumarmaurya.imdbkt.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MemoryCache<K, V>(
    private val maxSize: Int = 250,
    private val defaultTtlMs: Long = 15 * 60 * 1000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : Cache<K, V> {

    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val mutex = Mutex()
    private val map = object : LinkedHashMap<K, Entry<V>>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean = size > maxSize
    }

    override suspend fun get(key: K): V? = mutex.withLock {
        val entry = map[key] ?: return@withLock null
        if (clock() > entry.expiresAt) {
            map.remove(key)
            return@withLock null
        }
        entry.value
    }

    override suspend fun put(key: K, value: V, ttlMs: Long?) = mutex.withLock {
        map[key] = Entry(value, clock() + (ttlMs ?: defaultTtlMs))
    }

    override suspend fun remove(key: K) = mutex.withLock {
        map.remove(key)
        Unit
    }

    override suspend fun clear() = mutex.withLock {
        map.clear()
    }
}
