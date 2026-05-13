package io.github.ajitkumarmaurya.imdbkt.repository

import io.github.ajitkumarmaurya.imdbkt.cache.Cache
import io.github.ajitkumarmaurya.imdbkt.model.ErrorType
import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.Season
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType
import io.github.ajitkumarmaurya.imdbkt.network.FirecrawlClient
import io.github.ajitkumarmaurya.imdbkt.network.HttpClient
import io.github.ajitkumarmaurya.imdbkt.parser.ActorParser
import io.github.ajitkumarmaurya.imdbkt.parser.EpisodeParser
import io.github.ajitkumarmaurya.imdbkt.parser.FirecrawlMarkdownParser
import io.github.ajitkumarmaurya.imdbkt.parser.SearchParser
import io.github.ajitkumarmaurya.imdbkt.parser.TitleParser
import io.github.ajitkumarmaurya.imdbkt.parser.TrendingParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

internal class ImdbRepositoryImpl(
    private val httpClient: HttpClient,
    private val cache: Cache,
    private val firecrawlClient: FirecrawlClient? = null,
) : ImdbRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val searchParser = SearchParser(json)
    private val titleParser = TitleParser(json)
    private val actorParser = ActorParser(json)
    private val trendingParser = TrendingParser(json)
    private val episodeParser = EpisodeParser(json)
    private val markdownParser = FirecrawlMarkdownParser()

    override suspend fun search(query: String): ImdbResult<List<ImdbSearchItem>> =
        withContext(Dispatchers.IO) {
            safeCall {
                val cacheKey = "search:$query"
                cache.get(cacheKey)?.let {
                    return@safeCall json.decodeFromString<List<ImdbSearchItem>>(it)
                }

                // Primary path — IMDb suggestion API
                val results = runCatching {
                    searchParser.parse(fetch(searchParser.buildUrl(query)))
                }.getOrNull()

                if (!results.isNullOrEmpty()) {
                    cache.put(cacheKey, json.encodeToString(results))
                    return@safeCall results
                }

                // Fallback — Firecrawl search
                val fallback = firecrawlClient?.let { fc ->
                    markdownParser.parseSearchHits(fc.search(query))
                }
                if (!fallback.isNullOrEmpty()) {
                    cache.put(cacheKey, json.encodeToString(fallback))
                }
                fallback ?: results ?: emptyList()
            }
        }

    override suspend fun getTitle(imdbId: String): ImdbResult<ImdbTitle> =
        withContext(Dispatchers.IO) {
            safeCall {
                val cacheKey = "title:$imdbId"
                cache.get(cacheKey)?.let {
                    return@safeCall json.decodeFromString<ImdbTitle>(it)
                }

                val url = titleParser.buildUrl(imdbId)

                // Primary path — direct HTML fetch + __NEXT_DATA__ parser
                val title = runCatching {
                    titleParser.parse(imdbId, fetch(url))
                }.getOrNull()

                if (title != null && title.title.isNotBlank()) {
                    cache.put(cacheKey, json.encodeToString(title))
                    return@safeCall title
                }

                // Fallback — Firecrawl scrape + markdown parser
                val scraped = firecrawlClient?.scrape(url)
                    ?: throw IOException("Primary fetch failed and no Firecrawl API key configured")

                val fallback = markdownParser.parseTitle(imdbId, scraped.markdown, scraped.posterUrl)
                if (fallback.title.isNotBlank()) {
                    cache.put(cacheKey, json.encodeToString(fallback))
                }
                fallback
            }
        }

    override suspend fun getActor(actorId: String): ImdbResult<ImdbActor> =
        withContext(Dispatchers.IO) {
            safeCall {
                val url = actorParser.buildUrl(actorId)
                val cacheKey = "actor:$actorId"

                val cached = cache.get(cacheKey)
                if (cached != null) {
                    return@safeCall json.decodeFromString<ImdbActor>(cached)
                }

                val html = fetch(url)
                val actor = actorParser.parse(actorId, html)

                cache.put(cacheKey, json.encodeToString(actor))
                actor
            }
        }

    override suspend fun getTrending(type: TrendingType): ImdbResult<List<ImdbSearchItem>> =
        withContext(Dispatchers.IO) {
            safeCall {
                val url = trendingParser.buildUrl(type)
                val cacheKey = "trending:${type.name}"

                cache.get(cacheKey)?.let {
                    return@safeCall json.decodeFromString<List<ImdbSearchItem>>(it)
                }

                // Primary path
                val results = runCatching {
                    trendingParser.parse(fetch(url))
                }.getOrNull()

                if (!results.isNullOrEmpty()) {
                    cache.put(cacheKey, json.encodeToString(results))
                    return@safeCall results
                }

                // Fallback — Firecrawl search for trending IMDb titles
                val fallback = firecrawlClient?.let { fc ->
                    markdownParser.parseSearchHits(fc.search("top movies imdb"))
                }
                if (!fallback.isNullOrEmpty()) {
                    cache.put(cacheKey, json.encodeToString(fallback))
                }
                fallback ?: results ?: emptyList()
            }
        }

    override suspend fun getSeasonEpisodes(imdbId: String, season: Int): ImdbResult<Season> =
        withContext(Dispatchers.IO) {
            safeCall {
                val url = episodeParser.buildUrl(imdbId, season)
                val cacheKey = "episodes:$imdbId:$season"

                val cached = cache.get(cacheKey)
                if (cached != null) {
                    return@safeCall json.decodeFromString<Season>(cached)
                }

                val html = fetch(url)
                val episodes = episodeParser.parse(html, imdbId, season)
                val seasonData = Season(number = season, episodes = episodes)

                cache.put(cacheKey, json.encodeToString(seasonData))
                seasonData
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fetch(url: String): String {
        val response = httpClient.get(url)
        val error: Exception? = when {
            response.code == 404 -> NotFoundException("Not found: $url")
            !response.isSuccessful -> IOException("HTTP ${response.code}: $url")
            else -> null
        }
        if (error != null) throw error
        return response.body?.string() ?: throw IOException("Empty response body from: $url")
    }

    private inline fun <T> safeCall(block: () -> T): ImdbResult<T> = runCatching {
        val result = block()
        if (result is List<*> && result.isEmpty()) {
            ImdbResult.Empty
        } else {
            ImdbResult.Success(result)
        }
    }.getOrElse { e ->
        when (e) {
            is NotFoundException -> ImdbResult.Error(e.message ?: "Not found", e, ErrorType.NOT_FOUND)
            is IOException -> ImdbResult.Error(e.message ?: "Network error", e, ErrorType.NETWORK)
            is kotlinx.serialization.SerializationException ->
                ImdbResult.Error("Failed to parse response: ${e.message}", e, ErrorType.PARSING)
            else -> ImdbResult.Error(e.message ?: "Unknown error", e, ErrorType.UNKNOWN)
        }
    }

    private class NotFoundException(message: String) : Exception(message)
}
