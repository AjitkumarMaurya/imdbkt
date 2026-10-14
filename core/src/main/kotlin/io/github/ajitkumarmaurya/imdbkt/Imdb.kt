package io.github.ajitkumarmaurya.imdbkt

import io.github.ajitkumarmaurya.imdbkt.cache.DiskCache
import io.github.ajitkumarmaurya.imdbkt.cache.MemoryCache
import io.github.ajitkumarmaurya.imdbkt.cache.TieredCache
import io.github.ajitkumarmaurya.imdbkt.network.FirecrawlClient
import io.github.ajitkumarmaurya.imdbkt.network.HttpClient
import io.github.ajitkumarmaurya.imdbkt.repository.ImdbRepository
import io.github.ajitkumarmaurya.imdbkt.repository.ImdbRepositoryImpl

class Imdb(config: ImdbConfig = ImdbConfig()) : ImdbRepository by createRepository(config) {

    class Builder {
        var config: ImdbConfig = ImdbConfig()
        fun build(): Imdb = Imdb(config)
    }

    companion object {
        operator fun invoke(block: Builder.() -> Unit): Imdb = Builder().apply(block).build()

        private fun createRepository(config: ImdbConfig): ImdbRepository {
            val memory = MemoryCache<String, String>(
                maxSize = config.memoryCacheMaxEntries,
                defaultTtlMs = config.metadataCacheTtlMs
            )
            val disk = config.diskCacheDir?.let {
                DiskCache(directory = it, defaultTtlMs = config.metadataCacheTtlMs)
            }
            val tieredCache = TieredCache(memory, disk)
            return ImdbRepositoryImpl(
                config = config,
                httpClient = HttpClient(config),
                firecrawlClient = FirecrawlClient(config),
                cache = tieredCache
            )
        }
    }
}
