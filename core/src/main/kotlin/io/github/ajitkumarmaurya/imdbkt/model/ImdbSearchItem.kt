package io.github.ajitkumarmaurya.imdbkt.model

import kotlinx.serialization.Serializable

@Serializable
data class ImdbSearchItem(
    val imdbId: String,
    val title: String,
    val year: String? = null,
    val type: TitleType = TitleType.UNKNOWN,
    val poster: String? = null,
    /** Typically the top-billed stars or episode subtitle. */
    val subtitle: String? = null,
    val rank: Int? = null,
)

enum class TitleType {
    MOVIE,
    TV_SERIES,
    TV_MINI_SERIES,
    TV_EPISODE,
    TV_MOVIE,
    SHORT,
    VIDEO,
    PERSON,
    UNKNOWN;

    companion object {
        fun from(raw: String?): TitleType = when (raw?.lowercase()) {
            "movie", "feature" -> MOVIE
            "tvseries", "tv series", "tvshow" -> TV_SERIES
            "tvminiseries", "tv mini series", "miniseries" -> TV_MINI_SERIES
            "tvepisode", "tv episode" -> TV_EPISODE
            "tvmovie", "tv movie" -> TV_MOVIE
            "short" -> SHORT
            "video" -> VIDEO
            "nm" -> PERSON
            else -> UNKNOWN
        }
    }
}
