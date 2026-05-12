package io.github.ajitkumarmaurya.imdbkt.parser.selectors

/**
 * Centralised CSS selectors and JSON paths for every IMDb page type.
 * When IMDb changes its HTML, update only this file.
 */
internal object ImdbSelectors {

    // ── Base URLs ─────────────────────────────────────────────────────────────
    const val BASE_URL = "https://www.imdb.com"
    const val SUGGESTION_URL = "https://v3.sg.media-imdb.com/suggestion/x/%s.json"
    const val TITLE_URL = "$BASE_URL/title/%s/"
    const val ACTOR_URL = "$BASE_URL/name/%s/"
    const val CHART_URL = "$BASE_URL/%s"
    const val FULL_CREDITS_URL = "$BASE_URL/title/%s/fullcredits"
    const val EPISODES_URL = "$BASE_URL/title/%s/episodes/?season=%d"

    // ── Page-level selectors ──────────────────────────────────────────────────
    /** Script tag containing Next.js page data. */
    const val NEXT_DATA_SCRIPT = "script#__NEXT_DATA__"

    /** JSON-LD structured data block. */
    const val JSON_LD_SCRIPT = "script[type='application/ld+json']"

    // ── Title page fallback selectors ─────────────────────────────────────────
    const val TITLE_HERO_TITLE = "h1[data-testid='hero__pageTitle'] span"
    const val TITLE_ORIGINAL = "div.sc-ec65ba05-1"
    const val TITLE_RATING = "div[data-testid='hero-rating-bar__aggregate-rating__score'] span"
    const val TITLE_VOTE_COUNT = "div[data-testid='hero-rating-bar__aggregate-rating'] div.sc-bde20123-3"
    const val TITLE_GENRES = "div[data-testid='genres'] a span"
    const val TITLE_PLOT = "p[data-testid='plot'] span[data-testid='plot-xl']"
    const val TITLE_POSTER = "div[data-testid='hero-media__poster'] img"
    const val TITLE_YEAR = "a.ipc-link[href*='/releaseinfo']"
    const val TITLE_RUNTIME = "li[data-testid='title-techspec_runtime'] div"
    const val TITLE_CERTIFICATE = "a.ipc-link[href*='/parentalguide/certificates']"
    const val TITLE_CAST_ROW = "div[data-testid='title-cast-item']"
    const val TITLE_CAST_ACTOR_LINK = "a[data-testid='title-cast-item__actor']"
    const val TITLE_CAST_CHAR = "a[data-testid='title-cast-item__character']"
    const val TITLE_CAST_IMAGE = "div[data-testid='title-cast-item__avatar'] img"
    const val TITLE_DIRECTOR = "div[data-testid='title-pc-principal-credit']:has(a[href*='?ref_=tt_ov_dr']) a"
    const val TITLE_WRITER = "div[data-testid='title-pc-principal-credit']:has(a[href*='?ref_=tt_ov_wr']) a"
    const val TITLE_SEASONS = "select#browse-episodes-season option"
    const val TITLE_RELATED = "div[data-testid='sm-border-radius-16'] a[href*='/title/tt']"
    const val TITLE_PROD_COMPANY = "li[data-testid='title-details-companies'] a"

    // ── Actor page fallback selectors ─────────────────────────────────────────
    const val ACTOR_NAME = "h1[data-testid='hero__pageTitle'] span"
    const val ACTOR_BIO = "div[data-testid='bio-content'] div.ipc-html-content-inner-div"
    const val ACTOR_PHOTO = "img[data-testid='hero-media__img']"
    const val ACTOR_BIRTH_DATE = "div[data-testid='birth-and-death-birthdate'] span"
    const val ACTOR_BIRTH_PLACE = "div[data-testid='birth-and-death-birthdate'] a"
    const val ACTOR_FILMOGRAPHY_SECTION = "div[id^='accordion-item-actor'] div.ipc-metadata-list-summary-item__tc"
    const val ACTOR_FILMOGRAPHY_LINK = "a.ipc-metadata-list-summary-item__t"
    const val ACTOR_KNOWN_FOR = "div[data-testid='nm_pd_kf'] div[data-testid='shoveler-items-container'] a[href*='/title/tt']"

    // ── Chart / Trending page selectors ───────────────────────────────────────
    const val CHART_ITEM = "li.ipc-metadata-list-summary-item"
    const val CHART_ITEM_LINK = "a.ipc-title-link-wrapper"
    const val CHART_ITEM_TITLE = "h3.ipc-title__text"
    const val CHART_ITEM_YEAR = "span.cli-title-metadata-item"
    const val CHART_ITEM_RATING = "span.ipc-rating-star--rating"
    const val CHART_ITEM_IMAGE = "img.ipc-image"

    // ── __NEXT_DATA__ JSON paths (dot-separated for path() helper) ────────────
    object NextData {
        // Title paths
        const val ABOVE_FOLD = "props.pageProps.aboveTheFoldData"
        const val MAIN_COLUMN = "props.pageProps.mainColumnData"
        const val TITLE_ID = "id"
        const val TITLE_TEXT = "titleText.text"
        const val ORIGINAL_TITLE = "originalTitleText.text"
        const val TITLE_TYPE_ID = "titleType.id"
        const val TITLE_TYPE_IS_SERIES = "titleType.isSeries"
        const val RELEASE_YEAR = "releaseYear.year"
        const val END_YEAR = "releaseYear.endYear"
        const val RATING = "ratingsSummary.aggregateRating"
        const val VOTE_COUNT = "ratingsSummary.voteCount"
        const val GENRES = "genres.genres"
        const val RUNTIME_SECONDS = "runtime.seconds"
        const val PLOT = "plot.plotText.plainText"
        const val STORYLINE = "storyline.plotText.plainText"
        const val PRIMARY_IMAGE_URL = "primaryImage.url"
        const val CERTIFICATE = "certificate.rating"
        const val COUNTRIES = "countriesOfOrigin.countries"
        const val LANGUAGES = "spokenLanguages.spokenLanguages"
        const val RELEASE_DATE = "releaseDate"
        const val CAST_EDGES = "cast.edges"
        const val DIRECTORS = "directors"
        const val WRITERS = "writers"
        const val CREATORS = "creators"
        const val RELATED = "moreLikeThisTitles.edges"
        const val SEASON_COUNT = "episodes.seasons"
        const val KEYWORDS = "keywords.edges"
        const val BOX_OFFICE = "productionBudget"
        const val PRODUCTION_COMPANIES = "production.edges"
    }
}
