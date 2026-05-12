package io.github.ajitkumarmaurya.imdbkt.model

import kotlinx.serialization.Serializable

@Serializable
data class ImdbTitle(
    val imdbId: String,
    val title: String,
    val originalTitle: String? = null,
    val type: TitleType = TitleType.UNKNOWN,
    val year: String? = null,
    val endYear: String? = null,
    val description: String? = null,
    val storyline: String? = null,
    val rating: Float? = null,
    val voteCount: Long? = null,
    val metascore: Int? = null,
    val genres: List<String> = emptyList(),
    val releaseDate: String? = null,
    val runtimeMinutes: Int? = null,
    val languages: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val certificate: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val cast: List<CastMember> = emptyList(),
    val directors: List<Credit> = emptyList(),
    val writers: List<Credit> = emptyList(),
    val creators: List<Credit> = emptyList(),
    val productionCompanies: List<String> = emptyList(),
    val seasons: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val relatedTitles: List<ImdbSearchItem> = emptyList(),
    val awards: String? = null,
    val boxOffice: BoxOffice? = null,
    val trailerUrl: String? = null,
    val keywords: List<String> = emptyList(),
)

@Serializable
data class CastMember(
    val imdbId: String,
    val name: String,
    val characters: List<String> = emptyList(),
    val profileImage: String? = null,
)

@Serializable
data class Credit(
    val imdbId: String,
    val name: String,
    val job: String? = null,
)

@Serializable
data class Episode(
    val imdbId: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val airDate: String? = null,
    val rating: Float? = null,
    val description: String? = null,
    val poster: String? = null,
)

@Serializable
data class Season(
    val number: Int,
    val episodes: List<Episode>,
)

@Serializable
data class BoxOffice(
    val budget: String? = null,
    val openingWeekendUS: String? = null,
    val grossUS: String? = null,
    val cumulativeWorldwideGross: String? = null,
)
