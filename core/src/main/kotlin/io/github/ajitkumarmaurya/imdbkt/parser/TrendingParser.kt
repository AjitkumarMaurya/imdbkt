package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.utils.extractTitleId
import io.github.ajitkumarmaurya.imdbkt.utils.extractYear
import org.jsoup.Jsoup

class TrendingParser {
    private val leadingRankRegex = Regex("""^\d+\.\s*""")

    fun parse(html: String): ImdbResult<List<ImdbSearchItem>> = runCatching {
        val doc = Jsoup.parse(html)
        val items = doc.select(ImdbSelectors.CHART_ITEM).mapIndexedNotNull { idx, item ->
            val link = item.selectFirst("a[href*=/title/tt]") ?: return@mapIndexedNotNull null
            val id = link.attr("href").extractTitleId() ?: return@mapIndexedNotNull null
            val rawTitle = item.selectFirst(ImdbSelectors.CHART_TITLE)?.text()?.trim().orEmpty()
            val title = rawTitle.replace(leadingRankRegex, "")
            val year = item.select(".cli-title-metadata-item").firstOrNull()?.text()?.extractYear()
            val rating = item.selectFirst(".ipc-rating-star--rating")?.text()?.toDoubleOrNull()
            val poster = item.selectFirst("img.ipc-image")?.attr("src")

            ImdbSearchItem(
                id = id,
                title = title,
                year = year,
                posterUrl = poster,
                rank = idx + 1,
                rating = rating
            )
        }
        ImdbResult.Success(items)
    }.getOrElse { e ->
        ImdbResult.Failure(ImdbError.ParsingError("TrendingParser", "Failed to parse trending list: ${e.message}", e))
    }
}
