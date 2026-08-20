package com.earthvideo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthvideo.app.data.repository.MovieRepository
import com.earthvideo.app.ui.components.MovieCard
import com.earthvideo.app.ui.components.SkeletonGrid
import com.earthvideo.app.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: MovieRepository,
    onSearchClick: () -> Unit,
    onMovieClick: (String) -> Unit,
    onHistoryClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(key = "home", factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
    })
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    // More robust load-more detection using snapshotFlow
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Pair(totalItems, lastVisible)
        }.collect { (totalItems, lastVisible) ->
            if (totalItems > 0 && lastVisible >= totalItems - 2) {
                val state = viewModel.uiState.value
                if (!state.isLoadingMore && state.hasMore && !state.isLoading) {
                    viewModel.loadMore()
                }
            }
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() }
    )

    val categories = listOf(
        "recommend" to "推荐",
        "new" to "新剧",
        "oversea" to "国外热映",
        "tv" to "电视剧",
        "movie" to "电影",
        "variety" to "综艺"
    )

    Column(modifier = Modifier.fillMaxSize().background(PageBg)) {
        // Header with subtle gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Primary, PrimaryDark)))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search bar
                Surface(
                    color = White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.headerSearchHeight)
                        .clickable(onClick = onSearchClick)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "搜索影片",
                            fontSize = 13.sp,
                            color = White.copy(alpha = 0.85f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                TopBarAction(
                    icon = Icons.Default.History,
                    label = "历史",
                    onClick = onHistoryClick
                )
                TopBarAction(
                    icon = Icons.Default.Download,
                    label = "下载",
                    onClick = onDownloadsClick
                )
            }
        }

        // Category tabs with divider
        Surface(color = CardBg, shadowElevation = 1.dp) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categories) { (key, label) ->
                    val isSelected = key == uiState.currentCategory
                    Column(
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .clickable { viewModel.selectCategory(key) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            label,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Primary else TextSecondary
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .width(24.dp)
                                    .height(Dimens.tabIndicatorHeight)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Primary)
                            )
                        }
                    }
                }
            }
        }

        // Content
        when {
            uiState.isLoading && uiState.movies.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    SkeletonGrid(columns = 2)
                }
            }
            !uiState.isLoading && uiState.movies.isEmpty() -> {
                EmptyState(
                    title = "暂无数据",
                    subtitle = if (uiState.error != null) uiState.error!! else "请稍后再试",
                    onRetry = { viewModel.refresh() }
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().background(PageBg),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.movies, key = { it.id }) { movie ->
                            MovieCard(
                                movie = movie,
                                onClick = { onMovieClick(movie.id) }
                            )
                        }
                        // Load more indicator
                        if (uiState.isLoadingMore) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("加载中...", fontSize = 13.sp, color = TextHint)
                                    }
                                }
                            }
                        }
                    }

                    PullRefreshIndicator(
                        refreshing = uiState.isRefreshing,
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
private fun TopBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                tint = TextHint,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 16.sp, color = TextHint)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = TextHint)
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text("点击重试", color = Primary)
            }
        }
    }
}
