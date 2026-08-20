package com.earthvideo.app.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    val code: Int = 200,
    val message: String = "success",
    val data: T
)

data class PageData<T>(
    val list: List<T>,
    val page: Int,
    val size: Int,
    val total: Int,
    val hasMore: Boolean
)

data class Movie(
    val id: String,
    val title: String,
    val highlightTitle: String? = null,
    val description: String = "",
    val posterUrl: String,
    val type: String,
    val region: String = "",
    val year: Int = 0,
    val genre: List<String> = emptyList(),
    val director: String = "",
    val actors: List<String> = emptyList(),
    val episodeTotal: Int = 0,
    val episodeUpdated: Int = 0,
    val episodeTag: String = "",
    val hotTag: Boolean = false,
    val rating: Double = 0.0,
    val tags: String = "",
    val source: String = "",
    val sourceAvatar: String = "",
    val introduction: String = ""
)

data class Episode(
    val episodeNumber: Int,
    val title: String,
    val duration: Int,
    val current: Boolean = false
)

data class EpisodesResponse(
    val movieId: String,
    val total: Int,
    val updated: Int,
    val episodes: List<Episode>
)

data class PlayUrlResponse(
    val movieId: String,
    val episode: Int,
    val url: String,
    val sources: List<PlaySource>
)

data class PlaySource(
    val sourceId: String,
    val sourceName: String,
    val priority: Int
)

data class SearchHistory(
    val keywords: List<String>
)

data class HotSearchItem(
    val keyword: String,
    val tag: String,
    val description: String
)

data class HotSearchData(
    val list: List<HotSearchItem>
)

data class RankItem(
    val rank: Int,
    val movieId: String,
    val movie: Movie
)

data class UserProfile(
    val isLogin: Boolean = false,
    val nickname: String = "",
    val avatar: String = "",
    val historyCount: Int = 0,
    val favoriteCount: Int = 0,
    val downloadCount: Int = 0
)

data class SuggestData(
    val suggestions: List<String>
)

// For category list simplified
data class CategoryItem(
    val id: String,
    val title: String,
    val posterUrl: String,
    val episodeTag: String = "",
    val hotTag: Boolean = false
)
