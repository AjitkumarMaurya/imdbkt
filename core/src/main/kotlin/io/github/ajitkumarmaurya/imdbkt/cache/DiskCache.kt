package io.github.ajitkumarmaurya.imdbkt.cache

import java.io.File
import java.security.MessageDigest

/**
 * Simple file-based cache.  Each entry is a plain text file whose name is
 * the SHA-256 hash of the cache key.  The first line is the expiry timestamp;
 * remaining lines are the payload.
 */
class DiskCache(
    private val cacheDir: File,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) : Cache {

    init {
        cacheDir.mkdirs()
    }

    override fun get(key: String): String? {
        val file = fileForKey(key)
        if (!file.exists()) return null
        return runCatching {
            val lines = file.readLines()
            val expiresAt = lines.firstOrNull()?.toLongOrNull()
            when {
                expiresAt == null -> null
                System.currentTimeMillis() > expiresAt -> { file.delete(); null }
                else -> lines.drop(1).joinToString("\n")
            }
        }.getOrNull()
    }

    override fun put(key: String, value: String) {
        runCatching {
            val expiresAt = System.currentTimeMillis() + ttlMs
            fileForKey(key).writeText("$expiresAt\n$value")
        }
    }

    override fun remove(key: String) {
        fileForKey(key).delete()
    }

    override fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun fileForKey(key: String): File {
        val hash = sha256(key)
        return File(cacheDir, hash)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_TTL_MS = 60 * 60 * 1_000L // 1 hour
    }
}
