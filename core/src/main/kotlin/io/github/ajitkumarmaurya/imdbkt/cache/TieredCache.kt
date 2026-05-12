package io.github.ajitkumarmaurya.imdbkt.cache

/**
 * Wraps a fast [MemoryCache] in front of a persistent [DiskCache].
 * Reads hit memory first; on a miss it tries disk and back-fills memory.
 */
internal class TieredCache(
    private val memory: Cache,
    private val disk: Cache,
) : Cache {

    override fun get(key: String): String? {
        val fromMemory = memory.get(key)
        if (fromMemory != null) return fromMemory
        return disk.get(key)?.also { memory.put(key, it) }
    }

    override fun put(key: String, value: String) {
        memory.put(key, value)
        disk.put(key, value)
    }

    override fun remove(key: String) {
        memory.remove(key)
        disk.remove(key)
    }

    override fun clear() {
        memory.clear()
        disk.clear()
    }
}
