package com.earthvideo.app.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.earthvideo.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: com.earthvideo.app.data.repository.MovieRepository? = null,
    onBack: () -> Unit
) {
    var wifiOnly by remember { mutableStateOf(repository?.isWifiOnly() ?: true) }
    var keepScreenOn by remember { mutableStateOf(repository?.isKeepScreenOn() ?: true) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(PageBg)) {
        TopAppBar(
            title = { Text("设置", fontWeight = FontWeight.Bold) },
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Playback section
            SettingsSection(title = "播放设置") {
                SettingsToggle(
                    icon = Icons.Default.Wifi,
                    title = "仅WiFi下播放",
                    subtitle = "移动网络下不自动播放视频",
                    checked = wifiOnly,
                    onCheckedChange = { wifiOnly = it; repository?.setWifiOnly(it) }
                )
                SettingsToggle(
                    icon = Icons.Default.Lightbulb,
                    title = "播放时保持屏幕常亮",
                    subtitle = "避免观看时屏幕自动息屏",
                    checked = keepScreenOn,
                    onCheckedChange = { keepScreenOn = it; repository?.setKeepScreenOn(it) }
                )
            }

            // Storage section
            SettingsSection(title = "存储与缓存") {
                SettingsAction(
                    icon = Icons.Default.Storage,
                    title = "清除缓存",
                    subtitle = "清理图片、视频缓存，释放存储空间",
                    onClick = { showClearCacheDialog = true }
                )
                SettingsAction(
                    icon = Icons.Default.HistoryToggleOff,
                    title = "清空观看历史",
                    subtitle = "删除所有观看记录",
                    onClick = { showClearHistoryDialog = true }
                )
            }

            // About section
            SettingsSection(title = "关于") {
                SettingsAction(
                    icon = Icons.Default.Info,
                    title = "关于大地视频",
                    subtitle = "v1.0.0 · Build 1",
                    onClick = { showAboutDialog = true }
                )
                SettingsAction(
                    icon = Icons.Default.PrivacyTip,
                    title = "隐私政策",
                    subtitle = "了解我们如何保护你的数据",
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清除缓存") },
            text = { Text("确定要清除所有缓存吗？这将清理已下载的图片与视频缓存数据。") },
            confirmButton = {
                TextButton(onClick = {
                    // Clear PlaybackPrefetch cache + Coil image cache
                    runCatching {
                        val c = context.cacheDir
                        c.listFiles()?.filter { it.name == "media_cache" || it.name == "image_cache" || it.name == "coil-cache" }
                            ?.forEach { it.deleteRecursively() }
                    }
                    showClearCacheDialog = false
                }) {
                    Text("清除", color = HotRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("清空观看历史") },
            text = { Text("确定要删除所有观看记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    repository?.let { r ->
                        r.clearLocalHistory()
                    }
                    showClearHistoryDialog = false
                }) {
                    Text("清空", color = HotRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Primary, PrimaryDark))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("大", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("大地视频", fontWeight = FontWeight.Bold)
                        Text("EarthVideo v1.0.0", fontSize = 12.sp, color = TextHint)
                    }
                }
            },
            text = {
                Column {
                    Text("一个简洁、流畅的视频播放与发现平台。", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("© 2026 大地视频团队", fontSize = 12.sp, color = TextHint)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("关闭", color = Primary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            fontSize = 12.sp,
            color = TextHint,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp)
        )
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(Dimens.cardRadius),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = TextHint)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = Primary,
                uncheckedThumbColor = White,
                uncheckedTrackColor = TabBg
            )
        )
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = TextHint)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextHint,
            modifier = Modifier.size(18.dp)
        )
    }
}
