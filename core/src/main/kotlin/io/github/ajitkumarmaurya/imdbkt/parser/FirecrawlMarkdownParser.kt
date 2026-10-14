package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.utils.extractYear

class FirecrawlMarkdownParser {
    private val h1Regex = Regex("""^#\s+(.+)$""", RegexOption.MULTILINE)
    private val ratingRegex = Regex("""(\d\.\d)\s*/\s*10""")

    fun parseTitle(titleId: String, markdown: String): ImdbResult<ImdbTitle> {
        val heading = h1Regex.find(markdown)?.groupValues?.getOrNull(1)?.trim()
            ?: return ImdbResult.Failure(ImdbError.ParsingError("FirecrawlMarkdownParser", "No H1 title in markdown"))
        val rating = ratingRegex.find(markdown)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val year = markdown.extractYear()

        return ImdbResult.Success(ImdbTitle(id = titleId, title = heading, year = year, rating = rating))
    }
}
