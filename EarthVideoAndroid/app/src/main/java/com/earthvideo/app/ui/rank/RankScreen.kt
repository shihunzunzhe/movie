package com.earthvideo.app.ui.rank

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
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
import kotlinx.coroutines.launch
import com.earthvideo.app.data.model.RankItem
import kotlinx.coroutines.launch
import com.earthvideo.app.data.repository.MovieRepository
import kotlinx.coroutines.launch
import com.earthvideo.app.ui.components.RankListItem
import kotlinx.coroutines.launch
import com.earthvideo.app.ui.components.SkeletonList
import kotlinx.coroutines.launch
import com.earthvideo.app.ui.theme.*

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RankScreen(
    repository: MovieRepository,
    onMovieClick: (String) -> Unit
) {
    var items by remember { mutableStateOf(listOf<RankItem>()) }
    var selectedType by remember { mutableStateOf("hot") }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val rankTabs = listOf(
        "hot" to "热播榜",
        "rising" to "飙升榜",
        "search" to "热搜榜",
        "new" to "新片榜",
        "tv" to "电视剧",
        "movie" to "电影"
    )

    suspend fun loadRankList(type: String, page: Int, refresh: Boolean = false) {
        if (!refresh && page == 1) isLoading = true
        try {
            val data = repository.getRankList(type, page)
            if (page == 1 || refresh) {
                items = data.list
            } else {
                items = items + data.list
            }
            hasMore = data.hasMore
        } catch (_: Exception) {}
        isLoading = false
        isRefreshing = false
        isLoadingMore = false
    }

    LaunchedEffect(selectedType) {
        currentPage = 1
        items = emptyList()
        loadRankList(selectedType, 1)
    }

    // Load-more detection
    val layoutInfo = listState.layoutInfo
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    val totalItems = layoutInfo.totalItemsCount
    LaunchedEffect(lastVisible, totalItems) {
        if (!isLoading && !isLoadingMore && hasMore && totalItems > 0 && lastVisible >= totalItems - 2) {
            isLoadingMore = true
            val nextPage = currentPage + 1
            currentPage = nextPage
            loadRankList(selectedType, nextPage)
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                currentPage = 1
                loadRankList(selectedType, 1, refresh = true)
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize().background(PageBg)) {
        // Banner with brand gradient + decorative circles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Primary, PrimaryDark)
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 24.dp)
                    .size(80.dp)
                    .background(
                        color = White.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 80.dp, bottom = 12.dp)
                    .size(48.dp)
                    .background(
                        color = White.copy(alpha = 0.10f),
                        shape = CircleShape
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .statusBarsPadding()
                    .padding(start = 24.dp)
            ) {
                Text(
                    "排行榜",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "PAI HANG BANG",
                    fontSize = 11.sp,
                    color = White.copy(alpha = 0.6f),
                    letterSpacing = 4.sp
                )
            }
        }

        // Rank tabs
        Surface(color = CardBg, shadowElevation = 1.dp) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(rankTabs) { (key, label) ->
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
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Primary else TextSecondary
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .width(20.dp)
                                    .height(Dimens.tabIndicatorHeight)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Primary)
                            )
                        }
                    }
                }
            }
        }

        // List with pull-refresh
        if (isLoading && items.isEmpty()) {
            SkeletonList(items = 6)
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无排行数据", fontSize = 14.sp, color = TextHint)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("请稍后再试", fontSize = 12.sp, color = TextHint)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(items, key = { "${it.rank}_${it.movieId}" }) { item ->
                        RankListItem(rankItem = item, onClick = { onMovieClick(item.movieId) })
                    }
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
