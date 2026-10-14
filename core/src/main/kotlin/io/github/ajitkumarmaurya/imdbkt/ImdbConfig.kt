package io.github.ajitkumarmaurya.imdbkt

import java.io.File

data class ImdbConfig(
    val connectTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 15_000L,
    val maxRetries: Int = 3,
    val baseRetryDelayMs: Long = 400L,
    val memoryCacheMaxEntries: Int = 250,
    val searchCacheTtlMs: Long = 10 * 60 * 1000L,
    val metadataCacheTtlMs: Long = 60 * 60 * 1000L,
    val diskCacheDir: File? = null,
    val acceptLanguage: String = "en-US,en;q=0.9",
    val customUserAgent: String? = null,
    val firecrawlApiKey: String? = null,
    val firecrawlBaseUrl: String = "https://api.firecrawl.dev"
)
