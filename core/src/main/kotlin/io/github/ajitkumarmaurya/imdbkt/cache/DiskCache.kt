package io.github.ajitkumarmaurya.imdbkt.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DiskCache(
    private val directory: File,
    private val defaultTtlMs: Long = 6 * 60 * 60 * 1000L,
    private val maxBytes: Long = 50L * 1024 * 1024,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : Cache<String, String> {

    private val writeMutex = Mutex()

    init {
        if (!directory.exists()) directory.mkdirs()
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null

        runCatching {
            val lines = file.readLines()
            if (lines.isEmpty()) return@withContext null
            val expiresAt = lines.first().toLongOrNull() ?: return@withContext null
            if (clock() > expiresAt) {
                file.delete()
                return@withContext null
            }
            lines.drop(1).joinToString("\n")
        }.getOrNull()
    }

    override suspend fun put(key: String, value: String, ttlMs: Long?) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            if (!directory.exists()) directory.mkdirs()
            val expiresAt = clock() + (ttlMs ?: defaultTtlMs)
            val target = fileFor(key)
            val temp = File(directory, "${target.name}.tmp")
            temp.writeText("$expiresAt\n$value")
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            pruneIfNeeded()
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            fileFor(key).delete()
            Unit
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    private fun pruneIfNeeded() {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxBytes) return

        files.sortedBy { it.lastModified() }.forEach { file ->
            if (totalSize <= maxBytes * 0.8) return
            val len = file.length()
            if (file.delete()) totalSize -= len
        }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.cache")
    }
}
