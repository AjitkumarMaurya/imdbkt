package io.github.ajitkumarmaurya.imdbkt.cache

interface Cache<K, V> {
    suspend fun get(key: K): V?
    suspend fun put(key: K, value: V, ttlMs: Long? = null)
    suspend fun remove(key: K)
    suspend fun clear()
}
