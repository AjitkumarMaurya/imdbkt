package io.github.ajitkumarmaurya.imdbkt.repository

import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.cache.TieredCache
import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbEpisode
import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType
import io.github.ajitkumarmaurya.imdbkt.network.FirecrawlClient
import io.github.ajitkumarmaurya.imdbkt.network.HttpClient
import io.github.ajitkumarmaurya.imdbkt.parser.ActorParser
import io.github.ajitkumarmaurya.imdbkt.parser.EpisodeParser
import io.github.ajitkumarmaurya.imdbkt.parser.FirecrawlMarkdownParser
import io.github.ajitkumarmaurya.imdbkt.parser.SearchParser
import io.github.ajitkumarmaurya.imdbkt.parser.TitleParser
import io.github.ajitkumarmaurya.imdbkt.parser.TrendingParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

class ImdbRepositoryImpl(
    private val config: ImdbConfig,
    private val httpClient: HttpClient,
    private val firecrawlClient: FirecrawlClient,
    private val cache: TieredCache,
    private val titleParser: TitleParser = TitleParser(),
    private val searchParser: SearchParser = SearchParser(),
    private val actorParser: ActorParser = ActorParser(),
    private val episodeParser: EpisodeParser = EpisodeParser(),
    private val trendingParser: TrendingParser = TrendingParser(),
    private val markdownParser: FirecrawlMarkdownParser = FirecrawlMarkdownParser()
) : ImdbRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): ImdbResult<List<ImdbSearchItem>> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return ImdbResult.Success(emptyList())

        val cacheKey = "search:$clean"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<List<ImdbSearchItem>>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val firstChar = clean.first().let { if (it.isLetterOrDigit()) it else '_' }
        val encoded = URLEncoder.encode(clean, Charsets.UTF_8.name())
        val url = "https://v3.sg.media-imdb.com/suggestion/$firstChar/$encoded.json"

        return when (val resp = httpClient.get(url, acceptJson = true)) {
            is ImdbResult.Success -> searchParser.parse(resp.data).onSuccess { items ->
                cache.put(cacheKey, json.encodeToString(items), config.searchCacheTtlMs)
            }
            is ImdbResult.Failure -> resp
        }
    }

    override suspend fun getTitle(titleId: String): ImdbResult<ImdbTitle> {
        val cacheKey = "title:$titleId"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<ImdbTitle>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val url = "https://www.imdb.com/title/$titleId/"
        val primaryResult = when (val htmlResult = httpClient.get(url)) {
            is ImdbResult.Success -> titleParser.parse(titleId, htmlResult.data)
            is ImdbResult.Failure -> htmlResult
        }

        if (primaryResult is ImdbResult.Success) {
            cache.put(cacheKey, json.encodeToString(primaryResult.data), config.metadataCacheTtlMs)
            return primaryResult
        }

        if (config.firecrawlApiKey != null && primaryResult.errorOrNull() !is ImdbError.NotFound) {
            val fallback = when (val mdResult = firecrawlClient.scrapeMarkdown(url)) {
                is ImdbResult.Success -> markdownParser.parseTitle(titleId, mdResult.data)
                is ImdbResult.Failure -> mdResult
            }
            if (fallback is ImdbResult.Success) {
                cache.put(cacheKey, json.encodeToString(fallback.data), config.metadataCacheTtlMs)
                return fallback
            }
        }

        return primaryResult
    }

    override suspend fun getActor(actorId: String): ImdbResult<ImdbActor> {
        val cacheKey = "actor:$actorId"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<ImdbActor>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val url = "https://www.imdb.com/name/$actorId/"
        return when (val resp = httpClient.get(url)) {
            is ImdbResult.Success -> actorParser.parse(actorId, resp.data).onSuccess { actor ->
                cache.put(cacheKey, json.encodeToString(actor), config.metadataCacheTtlMs)
            }
            is ImdbResult.Failure -> resp
        }
    }

    override suspend fun getEpisodes(titleId: String, season: Int): ImdbResult<List<ImdbEpisode>> {
        val cacheKey = "episodes:$titleId:s$season"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<List<ImdbEpisode>>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val url = "https://www.imdb.com/title/$titleId/episodes/?season=$season"
        return when (val resp = httpClient.get(url)) {
            is ImdbResult.Success -> episodeParser.parse(season, resp.data).onSuccess { episodes ->
                cache.put(cacheKey, json.encodeToString(episodes), config.metadataCacheTtlMs)
            }
            is ImdbResult.Failure -> resp
        }
    }

    override suspend fun getTrending(type: TrendingType): ImdbResult<List<ImdbSearchItem>> {
        val cacheKey = "trending:${type.name}"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<List<ImdbSearchItem>>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val url = "https://www.imdb.com${type.path}"
        return when (val resp = httpClient.get(url)) {
            is ImdbResult.Success -> trendingParser.parse(resp.data).onSuccess { items ->
                cache.put(cacheKey, json.encodeToString(items), config.searchCacheTtlMs)
            }
            is ImdbResult.Failure -> resp
        }
    }
}
