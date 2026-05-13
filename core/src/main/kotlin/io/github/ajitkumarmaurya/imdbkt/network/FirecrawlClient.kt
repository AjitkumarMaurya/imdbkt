package io.github.ajitkumarmaurya.imdbkt.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin client for the Firecrawl REST API.
 *
 * Used as a fallback when the normal IMDb crawler is blocked or returns
 * an empty bot-challenge page. Firecrawl returns content as markdown,
 * which is then parsed by [io.github.ajitkumarmaurya.imdbkt.parser.FirecrawlMarkdownParser].
 */
internal class FirecrawlClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Scrape [url] and return a [ScrapeResult] with markdown content and poster URL,
     * or null on failure.
     */
    fun scrape(url: String): ScrapeResult? = runCatching {
        val body = json.encodeToString(ScrapeRequest(url = url))
        val request = Request.Builder()
            .url("$BASE_URL/scrape")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON_TYPE))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val raw = response.body?.string() ?: return null
            val data = json.decodeFromString<ScrapeResponse>(raw).data ?: return null
            val markdown = data.markdown ?: return null
            val meta = data.metadata
            val posterUrl = meta?.ogImage
                ?: meta?.image
                ?: extractImageFromMarkdown(markdown)
            ScrapeResult(markdown = markdown, posterUrl = posterUrl)
        }
    }.getOrNull()

    /** Pull the first image URL out of a markdown string — `![alt](url)`. */
    private fun extractImageFromMarkdown(markdown: String): String? =
        Regex("""!\[[^\]]*]\((https://[^)]+)\)""").find(markdown)?.groupValues?.get(1)

    /**
     * Search Firecrawl for IMDb title pages matching [query].
     * Returns a list of raw search hits that the caller converts to [ImdbSearchItem].
     */
    fun search(query: String): List<FirecrawlSearchHit> = runCatching {
        val body = json.encodeToString(SearchRequest(query = "$query site:imdb.com/title", limit = 8))
        val request = Request.Builder()
            .url("$BASE_URL/search")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON_TYPE))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val raw = response.body?.string() ?: return emptyList()
            json.decodeFromString<SearchResponse>(raw).data ?: emptyList()
        }
    }.getOrElse { emptyList() }

    // ── Request / response models ─────────────────────────────────────────────

    @Serializable
    private data class ScrapeRequest(
        val url: String,
        val formats: List<String> = listOf("markdown"),
    )

    @Serializable
    private data class SearchRequest(
        val query: String,
        val limit: Int = 8,
    )

    @Serializable
    private data class ScrapeResponse(
        val success: Boolean = false,
        val data: ScrapeData? = null,
    )

    @Serializable
    private data class ScrapeData(
        val markdown: String? = null,
        val metadata: ScrapeMetadata? = null,
    )

    @Serializable
    private data class ScrapeMetadata(
        val ogImage: String? = null,
        val image: String? = null,
    )

    @Serializable
    private data class SearchResponse(
        val success: Boolean = false,
        val data: List<FirecrawlSearchHit>? = null,
    )

    companion object {
        private const val BASE_URL = "https://api.firecrawl.dev/v1"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Structured result returned by [FirecrawlClient.scrape]. */
internal data class ScrapeResult(
    val markdown: String,
    val posterUrl: String? = null,
)

/** One result from a Firecrawl `/search` call. */
@Serializable
internal data class FirecrawlSearchHit(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val markdown: String? = null,
)
