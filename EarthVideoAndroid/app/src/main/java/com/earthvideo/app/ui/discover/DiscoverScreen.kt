package com.earthvideo.app.ui.discover

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
import androidx.compose.material.icons.filled.Search
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
import com.earthvideo.app.data.model.CategoryItem
import kotlinx.coroutines.launch
import com.earthvideo.app.data.repository.MovieRepository
import kotlinx.coroutines.launch
import com.earthvideo.app.ui.components.CategoryGridItem
import kotlinx.coroutines.launch
import com.earthvideo.app.ui.components.SkeletonGrid
import kotlinx.coroutines.launch
import com.earthvideo.app.ui.theme.*

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DiscoverScreen(
    repository: MovieRepository,
    onMovieClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onTopicSearch: (String) -> Unit = { onSearchClick() }
) {
    var items by remember { mutableStateOf(listOf<CategoryItem>()) }
    var selectedType by remember { mutableStateOf("all") }
    var selectedGenre by remember { mutableStateOf("all") }
    var selectedRegion by remember { mutableStateOf("all") }
    var selectedYear by remember { mutableStateOf("all") }
    var selectedSort by remember { mutableStateOf("最新") }
    var selectedTab by remember { mutableStateOf("category") }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val typeOptions = listOf(
        "all" to "全部", "连续剧" to "连续剧", "电影" to "电影",
        "综艺" to "综艺", "动漫" to "动漫", "短剧" to "短剧"
    )
    val genreOptions = listOf(
        "all" to "全部", "爱情" to "爱情", "喜剧" to "喜剧",
        "悬疑" to "悬疑", "犯罪" to "犯罪", "古装" to "古装",
        "都市" to "都市", "科幻" to "科幻"
    )
    val regionOptions = listOf(
        "all" to "全部", "内地" to "内地", "美国" to "美国",
        "中国香港" to "港", "中国台湾" to "台", "韩国" to "韩国",
        "日本" to "日本"
    )
    val yearOptions = listOf(
        "all" to "全部", "2026" to "2026", "2025" to "2025",
        "2024" to "2024", "2023" to "2023", "更早" to "更早"
    )
    val sortOptions = listOf("最新" to "最新", "最热" to "最热", "评分" to "评分", "最新上线" to "最新")

    suspend fun loadList(page: Int, refresh: Boolean = false) {
        if (selectedTab != "category") return
        if (!refresh && page == 1) isLoading = true
        try {
            val data = repository.getCategoryList(selectedType, selectedGenre, selectedRegion, selectedYear, selectedSort, page)
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

    LaunchedEffect(selectedType, selectedGenre, selectedRegion, selectedYear, selectedSort, selectedTab) {
        currentPage = 1
        items = emptyList()
        loadList(1)
    }

    // Load-more detection
    val totalItemsCount = gridState.layoutInfo.totalItemsCount
    val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(totalItemsCount, lastVisibleItemIndex) {
        if (!isLoading && !isLoadingMore && hasMore && totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 2) {
            isLoadingMore = true
            val nextPage = currentPage + 1
            currentPage = nextPage
            loadList(nextPage)
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                currentPage = 1
                loadList(1, refresh = true)
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize().background(PageBg)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Primary, PrimaryDark)))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "找片",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onSearchClick() }
                )
            }
        }

        // Category / Topic tabs
        Surface(color = CardBg, shadowElevation = 1.dp) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(listOf("category" to "分类", "topic" to "专题")) { (key, label) ->
                    val isSelected = key == selectedTab
                    Column(
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .clickable { selectedTab = key }
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

        if (selectedTab == "category") {
            // Filter rows
            Column {
                FilterRow(
                    label = "类型",
                    options = typeOptions,
                    selected = selectedType,
                    onSelect = { selectedType = it }
                )
                FilterRow(
                    label = "风格",
                    options = genreOptions,
                    selected = selectedGenre,
                    onSelect = { selectedGenre = it }
                )
                FilterRow(
                    label = "地区",
                    options = regionOptions,
                    selected = selectedRegion,
                    onSelect = { selectedRegion = it }
                )
                FilterRow(
                    label = "年份",
                    options = yearOptions,
                    selected = selectedYear,
                    onSelect = { selectedYear = it }
                )
                FilterRow(
                    label = "排序",
                    options = sortOptions,
                    selected = selectedSort,
                    onSelect = { selectedSort = it }
                )
            }

            // Grid with pull-refresh
            if (isLoading && items.isEmpty()) {
                SkeletonGrid(columns = 3, rows = 4)
            } else if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无符合条件的影片", fontSize = 14.sp, color = TextHint)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("试试调整筛选条件", fontSize = 12.sp, color = TextHint)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            CategoryGridItem(item = item, onClick = { onMovieClick(item.id) })
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(3) }) {
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
        } else {
            // Topic tab
            TopicView(onTopicClick = onTopicSearch)
        }
    }
}

@Composable
private fun TopicView(onTopicClick: (String) -> Unit) {
    val topics = listOf(
        Triple("高分经典", "IMDb Top 250 精选", "FF9800"),
        Triple("国产佳作", "华语影视巅峰", "F44336"),
        Triple("韩剧精选", "高分韩剧合集", "673AB7"),
        Triple("动漫专区", "经典动漫推荐", "4CAF50"),
        Triple("治愈系列", "温暖你的心", "03A9F4"),
        Triple("烧脑悬疑", "反转不断", "795548"),
        Triple("爆笑喜剧", "笑到肚子痛", "FFC107"),
        Triple("科幻巨制", "未来已来", "1A237E")
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(topics) { (title, subtitle, color) ->
            TopicCard(title = title, subtitle = subtitle, color = color, onClick = { onTopicClick(title) })
        }
    }
}

@Composable
private fun TopicCard(title: String, subtitle: String, color: String, onClick: () -> Unit) {
    val colorLong = color.toLong(16)
    val colorInt = (0xFF000000L or colorLong).toInt()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color(colorInt))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                fontSize = 12.sp,
                color = White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun FilterRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = TextHint,
            modifier = Modifier.width(36.dp)
        )
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options) { (key, optLabel) ->
                val isSel = key == selected
                Surface(
                    color = if (isSel) Primary else TabBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable { onSelect(key) }
                ) {
                    Text(
                        optLabel,
                        fontSize = 12.sp,
                        color = if (isSel) White else TextSecondary,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Divider)
    )
}
