package io.github.ajitkumarmaurya.imdbkt.repository

import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.cache.Cache
import io.github.ajitkumarmaurya.imdbkt.model.ErrorType
import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.Season
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType
import io.github.ajitkumarmaurya.imdbkt.network.HttpClient
import io.github.ajitkumarmaurya.imdbkt.parser.ActorParser
import io.github.ajitkumarmaurya.imdbkt.parser.EpisodeParser
import io.github.ajitkumarmaurya.imdbkt.parser.SearchParser
import io.github.ajitkumarmaurya.imdbkt.parser.TitleParser
import io.github.ajitkumarmaurya.imdbkt.parser.TrendingParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URLEncoder

internal class ImdbRepositoryImpl(
    private val config: ImdbConfig,
    private val httpClient: HttpClient,
    private val cache: Cache,
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

    override suspend fun search(query: String): ImdbResult<List<ImdbSearchItem>> =
        withContext(Dispatchers.IO) {
            safeCall {
                val url = searchParser.buildUrl(query)
                val cacheKey = "search:$query"

                val cached = cache.get(cacheKey)
                if (cached != null) {
                    return@safeCall json.decodeFromString<List<ImdbSearchItem>>(cached)
                }

                val body = fetch(url)
                val results = searchParser.parse(body)

                if (results.isNotEmpty()) {
                    cache.put(cacheKey, json.encodeToString(results))
                }
                results
            }
        }

    override suspend fun getTitle(imdbId: String): ImdbResult<ImdbTitle> =
        withContext(Dispatchers.IO) {
            safeCall {
                val url = titleParser.buildUrl(imdbId)
                val cacheKey = "title:$imdbId"

                val cached = cache.get(cacheKey)
                if (cached != null) {
                    return@safeCall json.decodeFromString<ImdbTitle>(cached)
                }

                val html = fetch(url)
                val title = titleParser.parse(imdbId, html)

                cache.put(cacheKey, json.encodeToString(title))
                title
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

                val cached = cache.get(cacheKey)
                if (cached != null) {
                    return@safeCall json.decodeFromString<List<ImdbSearchItem>>(cached)
                }

                val html = fetch(url)
                val results = trendingParser.parse(html)

                if (results.isNotEmpty()) {
                    cache.put(cacheKey, json.encodeToString(results))
                }
                results
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
        if (response.code == 404) throw NotFoundException("Not found: $url")
        if (response.code == 429) throw RateLimitException("Rate limited by IMDb")
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $url")
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
            is RateLimitException -> ImdbResult.Error(e.message ?: "Rate limited", e, ErrorType.RATE_LIMITED)
            is IOException -> ImdbResult.Error(e.message ?: "Network error", e, ErrorType.NETWORK)
            is kotlinx.serialization.SerializationException ->
                ImdbResult.Error("Failed to parse response: ${e.message}", e, ErrorType.PARSING)
            else -> ImdbResult.Error(e.message ?: "Unknown error", e, ErrorType.UNKNOWN)
        }
    }

    private class NotFoundException(message: String) : Exception(message)
    private class RateLimitException(message: String) : Exception(message)
}
