package com.earthvideo.app.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.earthvideo.app.data.model.Movie
import kotlinx.coroutines.launch
import com.earthvideo.app.data.repository.MovieRepository
import kotlinx.coroutines.launch
import com.earthvideo.app.ui.components.MovieCard
import kotlinx.coroutines.launch
import com.earthvideo.app.ui.components.SkeletonGrid
import kotlinx.coroutines.launch
import com.earthvideo.app.ui.theme.*

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    repository: MovieRepository,
    onBack: () -> Unit,
    onMovieClick: (String) -> Unit
) {
    var movies by remember { mutableStateOf(listOf<Movie>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    suspend fun loadFavs(page: Int, refresh: Boolean = false) {
        if (!refresh && page == 1) isLoading = true
        try {
            val data = repository.getFavorites(page)
            if (page == 1 || refresh) {
                movies = data.list
            } else {
                movies = movies + data.list
            }
            hasMore = data.hasMore
        } catch (_: Exception) {}
        isLoading = false
        isRefreshing = false
        isLoadingMore = false
    }

    LaunchedEffect(Unit) {
        loadFavs(1)
    }

    // Load-more
    val totalItemsCount = gridState.layoutInfo.totalItemsCount
    val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(totalItemsCount, lastVisibleItemIndex) {
        if (!isLoading && !isLoadingMore && hasMore && totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 2) {
            isLoadingMore = true
            val nextPage = currentPage + 1
            currentPage = nextPage
            loadFavs(nextPage)
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                currentPage = 1
                loadFavs(1, refresh = true)
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize().background(PageBg)) {
        TopAppBar(
            title = { Text("我的收藏", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Primary,
                titleContentColor = White,
                navigationIconContentColor = White
            )
        )

        when {
            isLoading && movies.isEmpty() -> SkeletonGrid(columns = 2, rows = 4)
            movies.isEmpty() -> EmptyFavorites()
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(movies, key = { it.id }) { movie ->
                            MovieCard(
                                movie = movie,
                                onClick = { onMovieClick(movie.id) }
                            )
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("加载中...", fontSize = 13.sp, color = TextHint)
                                    }
                                }
                            }
                        }
                    }
                    PullRefreshIndicator(
                        refreshing = isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        contentColor = Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFavorites() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = TextHint,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("还没有收藏", fontSize = 16.sp, color = TextHint)
            Spacer(modifier = Modifier.height(6.dp))
            Text("看到喜欢的记得收藏哦", fontSize = 13.sp, color = TextHint)
        }
    }
}
