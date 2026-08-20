package com.earthvideo.app.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.earthvideo.app.data.model.Movie
import com.earthvideo.app.data.repository.MovieRepository
import com.earthvideo.app.ui.components.SkeletonList
import com.earthvideo.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: MovieRepository,
    onBack: () -> Unit,
    onMovieClick: (String) -> Unit
) {
    var movies by remember { mutableStateOf(listOf<Movie>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        movies = repository.getLocalHistory()
        isLoading = false
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = false,
        onRefresh = {
            scope.launch {
                movies = repository.getLocalHistory()
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize().background(PageBg)) {
        TopAppBar(
            title = { Text("观看历史", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (movies.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空", tint = White)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Primary,
                titleContentColor = White,
                navigationIconContentColor = White
            )
        )

        when {
            isLoading && movies.isEmpty() -> SkeletonList(items = 4)
            movies.isEmpty() -> EmptyHistory()
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(movies, key = { it.id }) { movie ->
                            HistoryListItem(movie = movie, onClick = { onMovieClick(movie.id) })
                        }
                    }
                    PullRefreshIndicator(
                        refreshing = false,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        contentColor = Primary
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空观看历史") },
            text = { Text("确定要清空所有观看历史吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    repository.clearLocalHistory()
                    movies = emptyList()
                }) {
                    Text("清空", color = HotRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun EmptyHistory() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.HistoryToggleOff,
                contentDescription = null,
                tint = TextHint,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无观看记录", fontSize = 16.sp, color = TextHint)
            Spacer(modifier = Modifier.height(4.dp))
            Text("快去发现好片吧", fontSize = 13.sp, color = TextHint)
        }
    }
}

@Composable
private fun HistoryListItem(movie: Movie, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            if (movie.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    modifier = Modifier
                        .width(96.dp)
                        .height(128.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(128.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = movie.title.take(1),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary.copy(alpha = 0.3f)
                    )
                }
            }
            if (movie.episodeTag.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(PlayerOverlayLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        movie.episodeTag,
                        fontSize = 10.sp,
                        color = White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                movie.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (movie.description.isNotEmpty()) {
                Text(
                    movie.description,
                    fontSize = 12.sp,
                    color = TextHint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (movie.director.isNotEmpty()) {
                Text(
                    "导演：${movie.director}",
                    fontSize = 12.sp,
                    color = TextHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (movie.actors.isNotEmpty()) {
                Text(
                    "主演：${movie.actors.take(3).joinToString(" ")}",
                    fontSize = 12.sp,
                    color = TextHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 124.dp)
            .height(0.5.dp)
            .background(Divider)
    )
}
