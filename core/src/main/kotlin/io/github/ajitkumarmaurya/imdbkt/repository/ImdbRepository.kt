package io.github.ajitkumarmaurya.imdbkt.repository

import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.Season
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType

/**
 * Contract for all IMDb data operations.
 * Implementations may use caching, retries, or alternate data sources.
 */
interface ImdbRepository {

    /** Search IMDb titles, people, and episodes by [query]. */
    suspend fun search(query: String): ImdbResult<List<ImdbSearchItem>>

    /** Fetch complete metadata for a title (movie or series). */
    suspend fun getTitle(imdbId: String): ImdbResult<ImdbTitle>

    /** Fetch person/actor details. */
    suspend fun getActor(actorId: String): ImdbResult<ImdbActor>

    /** Fetch trending/popular titles for the given [type]. */
    suspend fun getTrending(type: TrendingType): ImdbResult<List<ImdbSearchItem>>

    /**
     * Fetch all episodes for a series season.
     * [imdbId] must be a series ID (e.g. tt5753856).
     */
    suspend fun getSeasonEpisodes(imdbId: String, season: Int): ImdbResult<Season>
}
