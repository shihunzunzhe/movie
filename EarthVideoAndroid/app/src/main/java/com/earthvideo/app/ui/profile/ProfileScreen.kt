package com.earthvideo.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.earthvideo.app.data.model.UserProfile
import com.earthvideo.app.data.repository.MovieRepository
import com.earthvideo.app.ui.theme.*

data class ProfileMenuItem(
    val icon: ImageVector,
    val label: String,
    val subtitle: String = "",
    val action: String = ""
)

@Composable
fun ProfileScreen(
    repository: MovieRepository,
    onNavigateToHistory: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    var userProfile by remember { mutableStateOf(UserProfile()) }

    val toast = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast.value) {
        toast.value?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast.value = null
        }
    }

    LaunchedEffect(Unit) {
        try {
            userProfile = repository.getUserProfile()
        } catch (_: Exception) {}
    }

    val menuItems = listOf(
        ProfileMenuItem(Icons.Default.History, "观看历史", "看过的影片"),
        ProfileMenuItem(Icons.Default.FavoriteBorder, "我的收藏", "喜欢的影片"),
        ProfileMenuItem(Icons.Default.Download, "我的下载", "离线观看"),
        ProfileMenuItem(Icons.Default.UploadFile, "上传视频", "分享你的视频"),
        ProfileMenuItem(Icons.Default.QuestionMark, "意见反馈", "帮助我们改进"),
        ProfileMenuItem(Icons.Default.Settings, "设置", "偏好与隐私"),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Profile header with gradient + blurred background
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                AsyncImage(
                    model = "https://placehold.co/400x220/3B6EE5/FFFFFF?text=大地视频",
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(24.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Primary.copy(alpha = 0.85f),
                                    PrimaryDark.copy(alpha = 0.95f)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(White.copy(alpha = 0.25f))
                            .border(2.dp, White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "登陆/注册",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "开启大地视频之旅",
                            fontSize = 13.sp,
                            color = White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Stats card floating over header
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(Dimens.cardRadius),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        count = "${userProfile.historyCount}",
                        label = "观看历史",
                        onClick = onNavigateToHistory
                    )
                    VerticalDivider()
                    StatItem(
                        count = "${userProfile.favoriteCount}",
                        label = "我的收藏",
                        onClick = onNavigateToFavorites
                    )
                    VerticalDivider()
                    StatItem(
                        count = "${userProfile.downloadCount}",
                        label = "我的下载",
                        onClick = onNavigateToDownloads
                    )
                }
            }
        }

        // Menu list
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = CardBg,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.cardRadius))
            ) {
                Column {
                    menuItems.forEachIndexed { index, item ->
                        MenuRow(item) {
                            when (item.label) {
                                "观看历史" -> onNavigateToHistory()
                                "我的收藏" -> onNavigateToFavorites()
                                "我的下载" -> onNavigateToDownloads()
                                "设置" -> onNavigateToSettings()
                                "上传视频" -> { toast.value = "上传功能开发中" }
                                "意见反馈" -> { toast.value = "请发送反馈至 support@earthvideo.com" }
                            }
                        }
                        if (index < menuItems.lastIndex) {
                            Divider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = Divider,
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }

        // Footer
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                "大地视频 v1.0.0",
                fontSize = 12.sp,
                color = TextHint,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatItem(count: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Text(
            count,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = TextHint)
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(Divider)
    )
}

@Composable
private fun MenuRow(item: ProfileMenuItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.label,
                fontSize = 15.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            if (item.subtitle.isNotEmpty()) {
                Text(item.subtitle, fontSize = 11.sp, color = TextHint)
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextHint,
            modifier = Modifier.size(18.dp)
        )
    }
}
