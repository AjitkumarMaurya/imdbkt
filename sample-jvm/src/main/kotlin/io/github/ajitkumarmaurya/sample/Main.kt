package io.github.ajitkumarmaurya.imdbkt.sample

import io.github.ajitkumarmaurya.imdbkt.Imdb
import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val imdb = Imdb(
        ImdbConfig(
            enableLogging = true,
        )
    )

    // ── Search ──────────────────────────────────────────────────────────────
    println("\n=== Search: 'Interstellar' ===")
    when (val result = imdb.search("Interstellar")) {
        is ImdbResult.Success -> result.data.take(5).forEach { item ->
            println("  [${item.type}] ${item.title} (${item.year ?: "?"}) — ${item.imdbId}")
            item.subtitle?.let { println("    $it") }
        }
        is ImdbResult.Error -> println("  Error: ${result.message}")
        ImdbResult.Empty -> println("  No results.")
    }

    // ── Title Details ────────────────────────────────────────────────────────
    println("\n=== Title: tt0816692 (Interstellar) ===")
    when (val result = imdb.getTitle("tt0816692")) {
        is ImdbResult.Success -> {
            val m = result.data
            println("  Title:    ${m.title}")
            println("  Year:     ${m.year}")
            println("  Rating:   ${m.rating} (${m.voteCount} votes)")
            println("  Runtime:  ${m.runtimeMinutes} min")
            println("  Genres:   ${m.genres.joinToString()}")
            println("  Countries:${m.countries.joinToString()}")
            println("  Director: ${m.directors.firstOrNull()?.name}")
            println("  Cast:     ${m.cast.take(3).joinToString { it.name }}")
            println("  Plot:     ${m.description?.take(120)}…")
        }
        is ImdbResult.Error -> println("  Error: ${result.message}")
        ImdbResult.Empty -> println("  No data.")
    }

    // ── Series: Dark ─────────────────────────────────────────────────────────
    println("\n=== Title: tt5753856 (Dark) ===")
    when (val result = imdb.getTitle("tt5753856")) {
        is ImdbResult.Success -> {
            val s = result.data
            println("  Title:   ${s.title}")
            println("  Type:    ${s.type}")
            println("  Seasons: ${s.seasons}")
            println("  Rating:  ${s.rating}")
        }
        is ImdbResult.Error -> println("  Error: ${result.message}")
        ImdbResult.Empty -> println("  No data.")
    }

    // ── Actor ────────────────────────────────────────────────────────────────
    println("\n=== Actor: nm0000190 (Matthew McConaughey) ===")
    when (val result = imdb.getActor("nm0000190")) {
        is ImdbResult.Success -> {
            val a = result.data
            println("  Name:       ${a.name}")
            println("  Born:       ${a.birthDate} in ${a.birthPlace}")
            println("  Known for:  ${a.knownFor.take(3).joinToString { it.title }}")
            println("  Filmography:${a.filmography.take(3).joinToString { it.title }}")
        }
        is ImdbResult.Error -> println("  Error: ${result.message}")
        ImdbResult.Empty -> println("  No data.")
    }

    // ── Trending ─────────────────────────────────────────────────────────────
    println("\n=== Trending Movies ===")
    when (val result = imdb.getTrending(TrendingType.MOVIES)) {
        is ImdbResult.Success -> result.data.take(5).forEachIndexed { i, item ->
            println("  ${i + 1}. ${item.title} (${item.year ?: "?"})  ${item.subtitle ?: ""}")
        }
        is ImdbResult.Error -> println("  Error: ${result.message}")
        ImdbResult.Empty -> println("  No results.")
    }

    // ── Season Episodes ───────────────────────────────────────────────────────
    println("\n=== Dark — Season 1 Episodes ===")
    when (val result = imdb.getSeasonEpisodes("tt5753856", 1)) {
        is ImdbResult.Success -> result.data.episodes.take(5).forEach { ep ->
            println("  E${ep.episode}: ${ep.title} — ★${ep.rating ?: "?"}")
        }
        is ImdbResult.Error -> println("  Error: ${result.message}")
        ImdbResult.Empty -> println("  No episodes.")
    }

    imdb.close()
    println("\nDone.")
}
