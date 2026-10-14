package io.github.ajitkumarmaurya.imdbkt.model

import kotlinx.serialization.Serializable

@Serializable
data class ImdbActor(
    val id: String,
    val name: String,
    val bio: String? = null,
    val birthDate: String? = null,
    val imageUrl: String? = null,
    val knownFor: List<KnownForTitle> = emptyList()
)

@Serializable
data class KnownForTitle(
    val id: String,
    val title: String,
    val year: Int? = null,
    val role: String? = null,
    val posterUrl: String? = null
)
