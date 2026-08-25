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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.earthvideo.app.data.download.DownloadManager
import com.earthvideo.app.data.download.DownloadTask
import com.earthvideo.app.data.repository.MovieRepository
import com.earthvideo.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    repository: MovieRepository,
    onBack: () -> Unit,
    onMovieClick: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("downloaded") }
    var showClearDialog by remember { mutableStateOf(false) }

    val allTasks by repository.downloadTasks.collectAsState()
    val downloaded = remember(allTasks) { allTasks.filter { it.state == DownloadTask.STATE_DONE } }
    val downloading = remember(allTasks) {
        allTasks.filter { it.state != DownloadTask.STATE_DONE }
    }

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
                if (selectedTab == "downloaded" && downloaded.isNotEmpty()) {
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
            Row(modifier = Modifier.fillMaxWidth()) {
                tabs.forEach { (key, label) ->
                    val isSelected = key == selectedTab
                    val count = if (key == "downloaded") downloaded.size else downloading.size
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = key }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Primary else TextSecondary
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "$count",
                                    fontSize = 11.sp,
                                    color = if (isSelected) Primary else TextHint
                                )
                            }
                        }
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

        val items = if (selectedTab == "downloaded") downloaded else downloading

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (selectedTab == "downloaded") Icons.Default.DownloadDone else Icons.Default.HourglassBottom,
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
                        "在播放页面点击「下载」即可离线观看",
                        fontSize = 13.sp,
                        color = TextHint
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items, key = { it.dirName }) { task ->
                    DownloadCard(
                        task = task,
                        onPlay = { onMovieClick(task.dirName) },
                        onDelete = { repository.deleteDownload(task.dirName) }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空下载") },
            text = { Text("确定要删除所有已下载内容吗？文件将被移除。") },
            confirmButton = {
                TextButton(onClick = {
                    repository.clearDownloads()
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
private fun DownloadCard(
    task: DownloadTask,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = task.state == DownloadTask.STATE_DONE
    val isFailed = task.state == DownloadTask.STATE_FAILED

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = task.movie.posterUrl,
                contentDescription = task.movie.title,
                modifier = Modifier
                    .width(68.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.movie.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "删除",
                            tint = TextHint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // Episode tag
                Surface(
                    color = PrimaryLight.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "第${task.episode}集",
                        fontSize = 11.sp,
                        color = Primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    isDone -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "已下载 · ${formatMb(task.sizeBytes)} · ${formatDate(task.createdAt)}",
                                fontSize = 11.sp,
                                color = TextHint
                            )
                        }
                    }
                    isFailed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = HotRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "下载失败：${task.error.take(18)}",
                                fontSize = 11.sp,
                                color = HotRed,
                                maxLines = 1
                            )
                        }
                    }
                    else -> {
                        val isQueued = task.state == DownloadTask.STATE_QUEUED
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = TextHint,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isQueued) "排队中" else "下载中 ${task.progress}%",
                                fontSize = 11.sp,
                                color = if (isQueued) TextHint else TextSecondary
                            )
                        }
                        if (!isQueued) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { task.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Primary,
                                trackColor = TabBg
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isDone) {
                FilledIconButton(
                    onClick = onPlay,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Primary,
                        contentColor = White
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String {
    if (bytes <= 0) return "--MB"
    val mb = bytes / 1024f / 1024f
    return if (mb >= 1024) "%.1fGB".format(mb / 1024f) else "%.1fMB".format(mb)
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))