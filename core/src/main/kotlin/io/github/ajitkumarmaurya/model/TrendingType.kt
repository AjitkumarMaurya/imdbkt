package io.github.ajitkumarmaurya.imdbkt.model

enum class TrendingType(val path: String) {
    MOVIES("chart/moviemeter/"),
    TV("chart/tvmeter/"),
    TOP_250_MOVIES("chart/top/"),
    TOP_250_TV("chart/toptv/"),
    MOST_POPULAR_MOVIES("chart/moviemeter/"),
    BOX_OFFICE("chart/boxoffice/"),
}
