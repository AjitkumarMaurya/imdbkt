package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbEpisode
import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.utils.extractTitleId
import org.jsoup.Jsoup

class EpisodeParser {
    private val prefixRegex = Regex("""^S\d+\.E\d+\s*∙\s*""")

    fun parse(season: Int, html: String): ImdbResult<List<ImdbEpisode>> = runCatching {
        val doc = Jsoup.parse(html)
        val episodes = doc.select(ImdbSelectors.EPISODE_CARD).mapIndexedNotNull { index, el ->
            val titleLink = el.selectFirst("a[href*=/title/tt]") ?: return@mapIndexedNotNull null
            val epId = titleLink.attr("href").extractTitleId() ?: return@mapIndexedNotNull null
            val cleanTitle = titleLink.text().trim().replace(prefixRegex, "")
            val plot = el.selectFirst(".ipc-html-content-inner-div")?.text()?.trim()
            val rating = el.selectFirst(".ipc-rating-star--rating")?.text()?.toDoubleOrNull()
            val img = el.selectFirst("img.ipc-image")?.attr("src")
            ImdbEpisode(
                id = epId,
                season = season,
                episodeNumber = index + 1,
                title = cleanTitle.ifBlank { "Episode ${index + 1}" },
                rating = rating,
                plot = plot,
                thumbnailUrl = img
            )
        }
        ImdbResult.Success(episodes)
    }.getOrElse { e ->
        ImdbResult.Failure(ImdbError.ParsingError("EpisodeParser", "Failed to parse episodes: ${e.message}", e))
    }
}
