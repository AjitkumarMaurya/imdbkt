package io.github.ajitkumarmaurya.imdbkt.model

import kotlinx.serialization.Serializable

@Serializable
data class ImdbSearchItem(
    val id: String,
    val title: String,
    val year: Int? = null,
    val type: String = "title",
    val stars: String? = null,
    val posterUrl: String? = null,
    val rank: Int? = null,
    val rating: Double? = null
)
