package io.github.ajitkumarmaurya.imdbkt.sample.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ajitkumarmaurya.imdbkt.sample.ui.detail.DetailScreen
import io.github.ajitkumarmaurya.imdbkt.sample.ui.search.SearchScreen

sealed class Screen(val route: String) {
    object Search : Screen("search")
    object Detail : Screen("detail/{imdbId}") {
        fun createRoute(imdbId: String) = "detail/$imdbId"
    }
}

@Composable
fun ImdbNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Search.route,
    ) {
        composable(Screen.Search.route) {
            SearchScreen(
                onTitleClick = { imdbId ->
                    navController.navigate(Screen.Detail.createRoute(imdbId))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("imdbId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val imdbId = backStackEntry.arguments?.getString("imdbId") ?: return@composable
            DetailScreen(
                imdbId = imdbId,
                onBack = { navController.popBackStack() },
                onCastClick = { actorId ->
                    // Future: navigate to actor detail
                }
            )
        }
    }
}
