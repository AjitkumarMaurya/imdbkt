package io.github.ajitkumarmaurya.imdbkt.model

import kotlinx.serialization.Serializable

@Serializable
data class ImdbTitle(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val type: String = "Movie",
    val year: Int? = null,
    val plot: String? = null,
    val rating: Double? = null,
    val voteCount: Long? = null,
    val contentRating: String? = null,
    val durationMinutes: Int? = null,
    val posterUrl: String? = null,
    val genres: List<String> = emptyList(),
    val directors: List<CreditSummary> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val episodes: List<ImdbEpisode> = emptyList()
)

@Serializable
data class CreditSummary(val id: String, val name: String)

@Serializable
data class CastMember(
    val id: String,
    val name: String,
    val character: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class ImdbEpisode(
    val id: String,
    val season: Int,
    val episodeNumber: Int,
    val title: String,
    val airDate: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val thumbnailUrl: String? = null
)
