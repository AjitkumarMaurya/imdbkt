package io.github.ajitkumarmaurya.imdbkt.parser.selectors

object ImdbSelectors {
    const val JSON_LD = "script[type=application/ld+json]"
    const val NEXT_DATA = "script#__NEXT_DATA__"
    const val TITLE_HEADER = "[data-testid=hero__pageTitle], h1"
    const val PLOT = "[data-testid=plot-xl], [data-testid=plot-l]"
    const val RATING_SCORE = "[data-testid=hero-rating-bar__aggregate-rating__score] span"
    const val POSTER_IMG = "[data-testid=hero-media__poster] img.ipc-image"
    const val GENRE_CHIP = "[data-testid=genres] a.ipc-chip, .ipc-chip-list__scroller a"
    const val CAST_ITEM = "[data-testid=title-cast-item]"
    const val CAST_ACTOR_LINK = "[data-testid=title-cast-item__actor]"
    const val CAST_CHARACTER = "[data-testid=cast-item-characters-link] span"
    const val ACTOR_NAME = "[data-testid=hero__pageTitle] span, h1"
    const val ACTOR_BIO = "[data-testid=bio-content] div.ipc-html-content-inner-div"
    const val ACTOR_IMAGE = "[data-testid=hero-media__poster] img.ipc-image"
    const val KNOWN_FOR_CARD = "[data-testid=nm_flmg_kwn_for] .ipc-primary-image-list-card"
    const val EPISODE_CARD = "article.episode-item-wrapper, [data-testid=tab-season-entry]"
    const val CHART_ITEM = "li.ipc-metadata-list-summary-item"
    const val CHART_TITLE = "h3.ipc-title__text"
}
