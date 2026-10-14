package io.github.ajitkumarmaurya.imdbkt.repository

import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbEpisode
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType

interface ImdbRepository {
    suspend fun search(query: String): ImdbResult<List<ImdbSearchItem>>
    suspend fun getTitle(titleId: String): ImdbResult<ImdbTitle>
    suspend fun getActor(actorId: String): ImdbResult<ImdbActor>
    suspend fun getEpisodes(titleId: String, season: Int = 1): ImdbResult<List<ImdbEpisode>>
    suspend fun getTrending(type: TrendingType = TrendingType.MOVIES): ImdbResult<List<ImdbSearchItem>>
}
