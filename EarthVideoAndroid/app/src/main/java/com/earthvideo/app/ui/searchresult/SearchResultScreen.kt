package com.earthvideo.app.ui.searchresult

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthvideo.app.data.model.Movie
import com.earthvideo.app.data.repository.MovieRepository
import com.earthvideo.app.ui.components.SearchResultItem
import com.earthvideo.app.ui.theme.*

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SearchResultScreen(
    keyword: String,
    repository: MovieRepository,
    onBack: () -> Unit,
    onMovieClick: (String) -> Unit
) {
    var movies by remember { mutableStateOf(listOf<Movie>()) }
    var selectedType by remember { mutableStateOf("all") }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var totalResults by remember { mutableIntStateOf(0) }

    val typeTabs = listOf(
        "all" to "全部",
        "电视剧" to "电视剧",
        "电影" to "电影",
        "综艺" to "综艺",
        "动漫" to "动漫",
        "短剧" to "短剧"
    )

    LaunchedEffect(keyword, selectedType) {
        isLoading = true
        currentPage = 1
        try {
            val data = repository.search(keyword, selectedType, 1)
            movies = data.list
            totalResults = data.total
            hasMore = data.hasMore
        } catch (_: Exception) {}
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(PageBg)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Primary)
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = White,
                modifier = Modifier.size(24.dp).clickable { onBack() })
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(White.copy(alpha = 0.2f))
                    .clickable { },
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null,
                        tint = White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(keyword, fontSize = 14.sp, color = White)
                }
            }
        }

        // Type tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(White),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(typeTabs) { (key, label) ->
                val isSelected = key == selectedType
                Column(
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .clickable { selectedType = key }
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
                                .padding(top = 4.dp)
                                .width(20.dp)
                                .height(2.dp)
                                .background(Primary)
                        )
                    }
                }
            }
        }

        // Scroll state for load-more
        val scrollState = rememberLazyListState()
        
        // Detect load-more
        val layoutInfo = scrollState.layoutInfo
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = layoutInfo.totalItemsCount
        
        LaunchedEffect(lastVisible, totalItems) {
            if (!isLoading && !isLoadingMore && totalItems > 0 && lastVisible >= totalItems - 2 && hasMore) {
                isLoadingMore = true
                currentPage += 1
            }
        }
        
        LaunchedEffect(currentPage) {
            if (currentPage > 1) {
                try {
                    val data = repository.search(keyword, selectedType, currentPage)
                    movies = movies + data.list
                    hasMore = data.hasMore
                } catch (_: Exception) {}
                isLoadingMore = false
            }
        }
        
        val pullRefreshState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                currentPage = 1
            }
        )
        
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                try {
                    val data = repository.search(keyword, selectedType, 1)
                    movies = data.list
                    hasMore = data.hasMore
                } catch (_: Exception) {}
                isRefreshing = false
            }
        }
        
        if (isLoading && movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, contentDescription = null,
                        tint = TextHint, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("未找到相关结果", fontSize = 16.sp, color = TextHint)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("试试其他关键词", fontSize = 13.sp, color = TextHint)
                }
            }
        } else if (movies.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState
                ) {
                    item {
                        Text(
                            "找到 $totalResults 个结果",
                            fontSize = 13.sp,
                            color = TextHint,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(movies, key = { it.id }) { movie ->
                        SearchResultItem(movie = movie, onClick = { onMovieClick(movie.id) })
                    }
                    // Load more indicator
                    if (isLoadingMore) {
                        item {
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
                                    Text("加载更多...", fontSize = 13.sp, color = TextHint)
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
