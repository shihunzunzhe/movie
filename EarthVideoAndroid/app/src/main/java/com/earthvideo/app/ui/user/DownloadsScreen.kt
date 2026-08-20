package com.earthvideo.app.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.earthvideo.app.ui.theme.*

data class DownloadItem(
    val movie: Movie,
    val progress: Int = 100,
    val status: String = "已完成",
    val sizeMb: Int = 256
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onMovieClick: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("downloaded") }
    var showClearDialog by remember { mutableStateOf(false) }

    // Simulated download items (would come from local DB in production)
    val downloadedItems = remember { mutableStateOf(listOf<DownloadItem>()) }
    val downloadingItems = remember { mutableStateOf(listOf<DownloadItem>()) }

    val tabs = listOf("downloaded" to "已下载", "downloading" to "下载中")

    Column(modifier = Modifier.fillMaxSize().background(PageBg)) {
        TopAppBar(
            title = { Text("我的下载", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                val hasItems = selectedTab == "downloaded" && downloadedItems.value.isNotEmpty()
                if (hasItems) {
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

        // Tab row
        Surface(color = CardBg, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                tabs.forEach { (key, label) ->
                    val isSelected = key == selectedTab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = key }
                            .padding(vertical = 14.dp),
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

        val items = if (selectedTab == "downloaded") downloadedItems.value else downloadingItems.value

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (selectedTab == "downloaded") Icons.Default.CloudDownload else Icons.Default.HourglassBottom,
                        contentDescription = null,
                        tint = TextHint,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (selectedTab == "downloaded") "暂无已下载内容" else "没有正在下载的任务",
                        fontSize = 16.sp,
                        color = TextHint
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "在播放页面可以下载视频",
                        fontSize = 13.sp,
                        color = TextHint
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.movie.id }) { item ->
                    DownloadListItem(item = item, onClick = { onMovieClick(item.movie.id) })
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空下载") },
            text = { Text("确定要删除所有已下载内容吗？") },
            confirmButton = {
                TextButton(onClick = {
                    downloadedItems.value = emptyList()
                    showClearDialog = false
                }) {
                    Text("确定", color = HotRed, fontWeight = FontWeight.Bold)
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
fun DownloadListItem(item: DownloadItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.movie.posterUrl,
            contentDescription = item.movie.title,
            modifier = Modifier
                .width(80.dp)
                .height(108.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.movie.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.movie.episodeTag.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    item.movie.episodeTag,
                    fontSize = 12.sp,
                    color = Primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.progress == 100) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = if (item.progress == 100) Primary else TextHint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${item.status} · ${item.sizeMb}MB",
                    fontSize = 12.sp,
                    color = TextHint
                )
            }
            if (item.progress < 100) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = item.progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Primary,
                    trackColor = TabBg
                )
            }
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "播放",
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 108.dp)
            .height(0.5.dp)
            .background(Divider)
    )
}
