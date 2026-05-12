package io.github.ajitkumarmaurya.imdbkt.cache

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemoryCacheTest {

    @Test
    fun `put and get returns stored value`() {
        val cache = MemoryCache()
        cache.put("key1", "value1")
        assertThat(cache.get("key1")).isEqualTo("value1")
    }

    @Test
    fun `get returns null for missing key`() {
        val cache = MemoryCache()
        assertThat(cache.get("missing")).isNull()
    }

    @Test
    fun `get returns null for expired entry`() {
        val cache = MemoryCache(ttlMs = 1L)
        cache.put("expiring", "data")
        Thread.sleep(10)
        assertThat(cache.get("expiring")).isNull()
    }

    @Test
    fun `remove deletes entry`() {
        val cache = MemoryCache()
        cache.put("key", "value")
        cache.remove("key")
        assertThat(cache.get("key")).isNull()
    }

    @Test
    fun `clear removes all entries`() {
        val cache = MemoryCache()
        cache.put("a", "1")
        cache.put("b", "2")
        cache.clear()
        assertThat(cache.get("a")).isNull()
        assertThat(cache.get("b")).isNull()
    }

    @Test
    fun `evicts when max size is reached`() {
        val cache = MemoryCache(maxSize = 2)
        cache.put("k1", "v1")
        cache.put("k2", "v2")
        cache.put("k3", "v3") // should trigger eviction
        // At most 2 entries remain; no exception thrown
        val stored = listOf("k1", "k2", "k3").count { cache.get(it) != null }
        assertThat(stored).isAtMost(2)
    }
}
