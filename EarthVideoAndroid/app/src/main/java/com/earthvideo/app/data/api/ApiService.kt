package com.earthvideo.app.data.api

import com.earthvideo.app.data.model.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @GET("api/home/recommend")
    suspend fun getHomeRecommend(
        @Query("category") category: String = "recommend",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageData<Movie>>

    @GET("api/search/history")
    suspend fun getSearchHistory(): ApiResponse<SearchHistory>

    @POST("api/search/history/clear")
    suspend fun clearSearchHistory(): ApiResponse<SearchHistory>

    @GET("api/search/hot")
    suspend fun getHotSearch(): ApiResponse<HotSearchData>

    @GET("api/search/suggest")
    suspend fun getSearchSuggest(
        @Query("keyword") keyword: String
    ): ApiResponse<SuggestData>

    @GET("api/search")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("type") type: String = "all",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageData<Movie>>

    @GET("api/category/list")
    suspend fun getCategoryList(
        @Query("type") type: String = "all",
        @Query("genre") genre: String = "all",
        @Query("region") region: String = "all",
        @Query("year") year: String = "all",
        @Query("sort") sort: String = "最新",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageData<CategoryItem>>

    @GET("api/rank/list")
    suspend fun getRankList(
        @Query("type") type: String = "hot",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageData<RankItem>>

    @GET("api/movie/detail")
    suspend fun getMovieDetail(
        @Query("id") id: String
    ): ApiResponse<Movie>

    @GET("api/movie/episodes")
    suspend fun getMovieEpisodes(
        @Query("id") id: String
    ): ApiResponse<EpisodesResponse>

    @GET("api/movie/playUrl")
    suspend fun getPlayUrl(
        @Query("id") id: String,
        @Query("episode") episode: Int = 1,
        @Query("source") source: String = "default",
        @Query("force") force: Int = 0
    ): ApiResponse<PlayUrlResponse>

    @GET("api/user/profile")
    suspend fun getUserProfile(): ApiResponse<UserProfile>

    @GET("api/user/history")
    suspend fun getHistory(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageData<Movie>>

    @POST("api/user/history/add")
    suspend fun addHistory(
        @Body body: Map<String, String>
    ): ApiResponse<Map<String, Any>>

    @POST("api/user/history/clear")
    suspend fun clearHistory(): ApiResponse<Map<String, Any>>

    @GET("api/user/favorites")
    suspend fun getFavorites(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageData<Movie>>

    @POST("api/user/favorites/toggle")
    suspend fun toggleFavorite(
        @Body body: Map<String, String>
    ): ApiResponse<Map<String, Any>>

    @GET("api/user/favorites/status")
    suspend fun getFavoritesStatus(
        @Query("movie_id") movieId: String
    ): ApiResponse<Map<String, Any>>

    @GET("api/health")
    suspend fun healthCheck(): Map<String, Any>
}
