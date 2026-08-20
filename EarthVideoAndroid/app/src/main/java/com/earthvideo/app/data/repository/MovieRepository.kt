package com.earthvideo.app.data.repository

import com.earthvideo.app.data.api.RetrofitClient
import com.earthvideo.app.data.model.*

class MovieRepository {
    private val api = RetrofitClient.apiService

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

    suspend fun getPlayUrl(id: String, episode: Int = 1): PlayUrlResponse {
        return api.getPlayUrl(id, episode).data
    }

    suspend fun getUserProfile(): UserProfile {
        return api.getUserProfile().data
    }

    // History
    suspend fun getHistory(page: Int = 1): PageData<Movie> {
        return api.getHistory(page).data
    }

    suspend fun addHistory(movieId: String) {
        api.addHistory(mapOf("movie_id" to movieId))
    }

    suspend fun clearHistory() {
        api.clearHistory()
    }

    // Favorites
    suspend fun getFavorites(page: Int = 1): PageData<Movie> {
        return api.getFavorites(page, 20).data
    }

    suspend fun toggleFavorite(movieId: String): Boolean {
        val result = api.toggleFavorite(mapOf("movie_id" to movieId)).data
        return result["isFavorite"] as? Boolean ?: false
    }

    suspend fun getFavoritesStatus(movieId: String): Boolean {
        return try {
            val result = api.getFavoritesStatus(movieId).data
            result["isFavorite"] as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    // Health
    suspend fun healthCheck(): Boolean {
        return try {
            val result = api.healthCheck()
            result["status"] == "ok"
        } catch (e: Exception) {
            false
        }
    }
}
