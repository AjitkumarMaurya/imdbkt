package io.github.ajitkumarmaurya.imdbkt

import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.Season
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType
import io.github.ajitkumarmaurya.imdbkt.network.HttpClient
import io.github.ajitkumarmaurya.imdbkt.repository.ImdbRepository
import io.github.ajitkumarmaurya.imdbkt.repository.ImdbRepositoryImpl

/**
 * Main entry point for the imdb-kt library.
 *
 * Create one instance per application and reuse it — the underlying OkHttpClient
 * and caches are expensive to initialise.
 *
 * ### Quick start
 * ```kotlin
 * val imdb = Imdb()
 *
 * // Search
 * val results = imdb.search("Interstellar")
 *
 * // Full title details
 * val movie = imdb.getTitle("tt0816692")
 * println(movie.getOrNull()?.rating)
 *
 * // Actor info
 * val actor = imdb.getActor("nm0000190")
 *
 * // Trending
 * val trending = imdb.getTrending(TrendingType.MOVIES)
 * ```
 *
 * @param config Optional [ImdbConfig] to customise timeouts, caching, and logging.
 */
class Imdb(config: ImdbConfig = ImdbConfig()) {

    private val httpClient = HttpClient(config)
    private val cache = config.buildCache()

    private val repository: ImdbRepository = ImdbRepositoryImpl(
        httpClient = httpClient,
        cache = cache,
    )

    /**
     * Search IMDb for titles, people, and episodes matching [query].
     *
     * Uses the IMDb suggestion API for fast, structured results.
     *
     * @param query Search terms (e.g. "Interstellar", "Chris Nolan").
     * @return [ImdbResult.Success] with a list of [ImdbSearchItem], or [ImdbResult.Error].
     */
    suspend fun search(query: String): ImdbResult<List<ImdbSearchItem>> =
        repository.search(query)

    /**
     * Fetch complete metadata for a movie or series.
     *
     * @param imdbId IMDb title ID (e.g. "tt0816692").
     * @return [ImdbResult.Success] with an [ImdbTitle], or [ImdbResult.Error].
     */
    suspend fun getTitle(imdbId: String): ImdbResult<ImdbTitle> =
        repository.getTitle(imdbId)

    /**
     * Fetch person/actor details including filmography.
     *
     * @param actorId IMDb name ID (e.g. "nm0000190").
     * @return [ImdbResult.Success] with an [ImdbActor], or [ImdbResult.Error].
     */
    suspend fun getActor(actorId: String): ImdbResult<ImdbActor> =
        repository.getActor(actorId)

    /**
     * Fetch trending / popular titles from the given IMDb chart.
     *
     * @param type [TrendingType] chart to fetch (default: [TrendingType.MOVIES]).
     * @return [ImdbResult.Success] with a list of [ImdbSearchItem], or [ImdbResult.Error].
     */
    suspend fun getTrending(type: TrendingType = TrendingType.MOVIES): ImdbResult<List<ImdbSearchItem>> =
        repository.getTrending(type)

    /**
     * Fetch all episodes for a given season of a series.
     *
     * @param imdbId Series IMDb ID (e.g. "tt5753856").
     * @param season Season number (1-based).
     * @return [ImdbResult.Success] with a [Season], or [ImdbResult.Error].
     */
    suspend fun getSeasonEpisodes(imdbId: String, season: Int): ImdbResult<Season> =
        repository.getSeasonEpisodes(imdbId, season)

    /** Release OkHttp resources.  Call when the client is no longer needed. */
    fun close() = httpClient.close()
}
