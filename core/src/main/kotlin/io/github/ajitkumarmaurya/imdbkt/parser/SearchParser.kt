package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SearchParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class SuggestResponse(@SerialName("d") val results: List<SuggestItem> = emptyList())

    @Serializable
    private data class SuggestItem(
        val id: String = "",
        @SerialName("l") val title: String = "",
        @SerialName("y") val year: Int? = null,
        @SerialName("qid") val type: String = "title",
        @SerialName("s") val stars: String? = null,
        @SerialName("rank") val rank: Int? = null,
        @SerialName("i") val image: SuggestImage? = null
    )

    @Serializable
    private data class SuggestImage(val imageUrl: String? = null)

    fun parse(jsonBody: String): ImdbResult<List<ImdbSearchItem>> = runCatching {
        val parsed = json.decodeFromString<SuggestResponse>(jsonBody)
        val items = parsed.results
            .filter { it.id.startsWith("tt") || it.id.startsWith("nm") }
            .map {
                ImdbSearchItem(
                    id = it.id,
                    title = it.title,
                    year = it.year,
                    type = it.type,
                    stars = it.stars,
                    posterUrl = it.image?.imageUrl,
                    rank = it.rank
                )
            }
        ImdbResult.Success(items)
    }.getOrElse { e ->
        ImdbResult.Failure(ImdbError.ParsingError("SearchParser", "Failed to parse search JSON: ${e.message}", e))
    }
}
