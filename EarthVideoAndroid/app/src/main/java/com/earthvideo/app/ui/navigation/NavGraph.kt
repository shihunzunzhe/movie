package com.earthvideo.app.ui.navigation
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.remember

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.earthvideo.app.ui.home.HomeScreen
import com.earthvideo.app.ui.search.SearchScreen
import com.earthvideo.app.ui.searchresult.SearchResultScreen
import com.earthvideo.app.ui.profile.ProfileScreen
import com.earthvideo.app.ui.discover.DiscoverScreen
import com.earthvideo.app.ui.rank.RankScreen
import com.earthvideo.app.ui.player.PlayerScreen
import com.earthvideo.app.ui.user.HistoryScreen
import com.earthvideo.app.ui.user.FavoritesScreen
import com.earthvideo.app.ui.user.DownloadsScreen
import com.earthvideo.app.ui.settings.SettingsScreen
import com.earthvideo.app.data.repository.MovieRepository
import androidx.compose.ui.Modifier

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SEARCH_RESULT = "search_result/{keyword}"
    const val PROFILE = "profile"
    const val DISCOVER = "discover"
    const val RANK = "rank"
    const val PLAYER = "player/{movieId}/{episode}"
    const val PLAYER_LOCAL = "player_local/{dirName}"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"

    fun searchResult(keyword: String) = "search_result/$keyword"
    fun player(movieId: String, episode: Int = 1) = "player/$movieId/$episode"
    fun playerLocal(dirName: String) = "player_local/$dirName"
}

@Composable
fun AppNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { MovieRepository(context) }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        // Quick, light transitions: avoids the heavy default cross-fade that made
        // exiting the fullscreen player feel janky.
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(180)) },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(180)) }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onMovieClick = { movieId ->
                    navController.navigate(Routes.player(movieId))
                },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onDownloadsClick = { navController.navigate(Routes.DOWNLOADS) }
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onSearch = { keyword -> navController.navigate(Routes.searchResult(keyword)) }
            )
        }
        composable(
            route = Routes.SEARCH_RESULT,
            arguments = listOf(navArgument("keyword") { type = NavType.StringType })
        ) { backStackEntry ->
            val keyword = backStackEntry.arguments?.getString("keyword") ?: ""
            SearchResultScreen(
                keyword = keyword,
                repository = repository,
                onBack = { navController.popBackStack() },
                onSearchClick = { navController.popBackStack() },
                onMovieClick = { movieId -> navController.navigate(Routes.player(movieId)) }
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                repository = repository,
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToFavorites = { navController.navigate(Routes.FAVORITES) },
                onNavigateToDownloads = { navController.navigate(Routes.DOWNLOADS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.DISCOVER) {
            DiscoverScreen(
                repository = repository,
                onMovieClick = { movieId -> navController.navigate(Routes.player(movieId)) },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onTopicSearch = { keyword -> navController.navigate(Routes.searchResult(keyword)) }
            )
        }
        composable(Routes.RANK) {
            RankScreen(
                repository = repository,
                onMovieClick = { movieId -> navController.navigate(Routes.player(movieId)) }
            )
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("movieId") { type = NavType.StringType },
                navArgument("episode") { type = NavType.IntType; defaultValue = 1 }
            )
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString("movieId") ?: ""
            val episode = backStackEntry.arguments?.getInt("episode") ?: 1
            PlayerScreen(
                movieId = movieId,
                initialEpisode = episode,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PLAYER_LOCAL,
            arguments = listOf(navArgument("dirName") { type = NavType.StringType })
        ) { backStackEntry ->
            val dirName = backStackEntry.arguments?.getString("dirName") ?: ""
            PlayerScreen(
                movieId = "",
                initialEpisode = 1,
                repository = repository,
                onBack = { navController.popBackStack() },
                localDir = dirName
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onMovieClick = { movieId -> navController.navigate(Routes.player(movieId)) }
            )
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onMovieClick = { movieId -> navController.navigate(Routes.player(movieId)) }
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onMovieClick = { dirName -> navController.navigate(Routes.playerLocal(dirName)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
