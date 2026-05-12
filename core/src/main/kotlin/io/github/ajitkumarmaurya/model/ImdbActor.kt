package io.github.ajitkumarmaurya.imdbkt.model

import kotlinx.serialization.Serializable

@Serializable
data class ImdbActor(
    val imdbId: String,
    val name: String,
    val bio: String? = null,
    val profileImage: String? = null,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val deathDate: String? = null,
    val height: String? = null,
    val knownFor: List<ImdbSearchItem> = emptyList(),
    val filmography: List<FilmographyItem> = emptyList(),
    val awards: String? = null,
    val spousesCount: Int? = null,
    val otherNames: List<String> = emptyList(),
)

@Serializable
data class FilmographyItem(
    val imdbId: String,
    val title: String,
    val year: String? = null,
    val role: String? = null,
    val characters: List<String> = emptyList(),
    val type: TitleType = TitleType.UNKNOWN,
    val poster: String? = null,
)
