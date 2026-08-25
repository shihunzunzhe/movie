package com.earthvideo.app.data.repository

import android.content.Context
import com.earthvideo.app.data.api.RetrofitClient
import com.earthvideo.app.data.download.DownloadManager
import com.earthvideo.app.data.download.DownloadTask
import com.earthvideo.app.data.model.*
import kotlinx.coroutines.flow.StateFlow

class MovieRepository(context: Context) {
    private val api = RetrofitClient.apiService
    private val local = LocalStorage(context)

    init {
        DownloadManager.init(context)
    }

    suspend fun getHomeRecommend(category: String = "recommend", page: Int = 1, size: Int = 20): PageData<Movie> {
        return api.getHomeRecommend(category, page, size).data
    }

    suspend fun getSearchHistory(): SearchHistory {
        return api.getSearchHistory().data
    }

    suspend fun clearSearchHistory(): SearchHistory {
        return api.clearSearchHistory().data
    }

    suspend fun getHotSearch(): List<HotSearchItem> {
        return api.getHotSearch().data.list
    }

    suspend fun getSearchSuggest(keyword: String): List<String> {
        return api.getSearchSuggest(keyword).data.suggestions
    }

    suspend fun search(keyword: String, type: String = "all", page: Int = 1): PageData<Movie> {
        return api.search(keyword, type, page, 20).data
    }

    suspend fun getCategoryList(
        type: String = "all", genre: String = "all", region: String = "all",
        year: String = "all", sort: String = "最热", page: Int = 1
    ): PageData<CategoryItem> {
        return api.getCategoryList(type, genre, region, year, sort, page).data
    }

    suspend fun getRankList(type: String = "hot", page: Int = 1): PageData<RankItem> {
        return api.getRankList(type, page).data
    }

    suspend fun getMovieDetail(id: String): Movie {
        return api.getMovieDetail(id).data
    }

    suspend fun getMovieEpisodes(id: String): EpisodesResponse {
        return api.getMovieEpisodes(id).data
    }

    suspend fun getPlayUrl(
        id: String,
        episode: Int = 1,
        source: String = "default",
        force: Int = 0
    ): PlayUrlResponse {
        return api.getPlayUrl(id, episode, source, force).data
    }

    suspend fun getUserProfile(): UserProfile {
        return api.getUserProfile().data
    }

    // History (local storage — persists across restarts)
    fun getLocalHistory(): List<Movie> = local.getHistoryMovies()

    fun addLocalHistory(movie: Movie) = local.addHistoryMovie(movie)

    fun clearLocalHistory() = local.clearHistory()

    fun getLocalHistoryCount(): Int = local.getHistoryCount()

    // Favorites (local storage — persists across restarts)
    fun getLocalFavorites(): List<Movie> = local.getFavoriteMovies()

    fun toggleLocalFavorite(movie: Movie): Boolean = local.toggleFavorite(movie)

    fun isLocalFavorite(movieId: String): Boolean = local.isFavorite(movieId)

    fun getLocalFavoriteCount(): Int = local.getFavoriteCount()

    // ------------------------------------------------------------------
    // User
    // ------------------------------------------------------------------
    fun getNickname(): String = local.getNickname()
    fun setNickname(name: String) = local.setNickname(name)
    fun isLoggedIn(): Boolean = local.isLoggedIn()
    fun logout() = local.logout()

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------
    fun isWifiOnly(): Boolean = local.isWifiOnly()
    fun setWifiOnly(v: Boolean) = local.setWifiOnly(v)
    fun isKeepScreenOn(): Boolean = local.isKeepScreenOn()
    fun setKeepScreenOn(v: Boolean) = local.setKeepScreenOn(v)

    // ------------------------------------------------------------------
    // Comments
    // ------------------------------------------------------------------
    fun getComments(movieId: String) = local.getComments(movieId)
    fun addComment(movieId: String, text: String) = local.addComment(movieId, text)

    // ------------------------------------------------------------------
    // Downloads (offline HLS)
    // ------------------------------------------------------------------

    val downloadTasks: StateFlow<List<DownloadTask>> get() = DownloadManager.tasks

    fun downloadMovie(movie: Movie, episode: Int, url: String) =
        DownloadManager.enqueue(movie, episode, url)

    /** Batch download several episodes; DownloadManager processes them serially (concurrency=1). */
    fun downloadMovies(movie: Movie, episodes: List<Pair<Int, String>>) =
        DownloadManager.enqueueBatch(movie, episodes)

    fun cancelDownload(dirName: String) = DownloadManager.cancel(dirName)

    fun deleteDownload(dirName: String) = DownloadManager.delete(dirName)

    fun clearDownloads() = DownloadManager.clearAll()

    fun localIndexPath(dirName: String): String? = DownloadManager.localIndexPath(dirName)

    fun localMovie(dirName: String): Movie? = DownloadManager.movieForDir(dirName)

    fun localEpisode(dirName: String): Int = DownloadManager.episodeForDir(dirName)
}
