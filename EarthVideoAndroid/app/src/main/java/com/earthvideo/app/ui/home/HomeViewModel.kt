package com.earthvideo.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.earthvideo.app.data.model.Movie
import com.earthvideo.app.data.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val movies: List<Movie> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentCategory: String = "recommend",
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val error: String? = null
)

class HomeViewModel(private val repository: MovieRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val pageSize = 20

    init {
        loadRecommend()
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(
            currentCategory = category,
            currentPage = 1,
            movies = emptyList()
        )
        loadRecommend(category, 1, isRefresh = false)
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            currentPage = 1
        )
        loadRecommend(
            _uiState.value.currentCategory,
            1,
            isRefresh = true
        )
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        loadRecommend(
            state.currentCategory,
            state.currentPage + 1,
            isLoadMore = true
        )
    }

    fun loadRecommend(
        category: String = _uiState.value.currentCategory,
        page: Int = 1,
        isRefresh: Boolean = false,
        isLoadMore: Boolean = false
    ) {
        viewModelScope.launch {
            if (!isRefresh && !isLoadMore) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            try {
                val data = repository.getHomeRecommend(category, page, pageSize)
                val currentMovies = _uiState.value.movies
                val newMovies = if (page == 1 || isRefresh) data.list
                else currentMovies + data.list
                _uiState.value = _uiState.value.copy(
                    movies = newMovies,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    currentPage = page,
                    hasMore = data.hasMore,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }
}
