package com.earthvideo.app.ui.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.earthvideo.app.data.api.RetrofitClient
import com.earthvideo.app.data.model.*
import com.earthvideo.app.data.repository.MovieRepository
import com.earthvideo.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val CONTROLS_AUTO_HIDE_MS = 3500L
private const val SEEK_STEP_MS = 10000L

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnsafeOptInUsageError", "SourceLockedOrientationActivity")
@Composable
fun PlayerScreen(
    movieId: String,
    initialEpisode: Int,
    repository: MovieRepository,
    onBack: () -> Unit
) {
    var movie by remember { mutableStateOf<Movie?>(null) }
    var episodes by remember { mutableStateOf(listOf<Episode>()) }
    var currentEpisode by remember { mutableIntStateOf(initialEpisode.coerceAtLeast(1)) }
    var playUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(false) }
    var showIntroduction by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var currentSources by remember { mutableStateOf(listOf<PlaySource>()) }
    var selectedSource by remember { mutableStateOf("default") }
    var showEpisodePanel by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    val speedOptions = listOf(1.0f, 1.5f, 2.0f, 3.0f, 0.5f)
    var userInteracting by remember { mutableStateOf(false) }
    // Swipe seek state
    var swipeSeekDelta by remember { mutableLongStateOf(0L) }
    var isSwiping by remember { mutableStateOf(false) }
    var swipeStartPosition by remember { mutableLongStateOf(0L) }
    // Data loaded flag for detail page
    var detailLoaded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val baseUrl = remember { RetrofitClient.getBaseUrl() }

    // Toggle fullscreen
    fun toggleFullscreen(full: Boolean) {
        try {
            val activity = context as? android.app.Activity
            if (activity != null) {
                if (full) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(isLandscape) {
        if (isLandscape) {
            try {
                val activity = context as? android.app.Activity
                activity?.window?.decorView?.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            } catch (_: Exception) {}
        } else {
            try {
                val activity = context as? android.app.Activity
                activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            } catch (_: Exception) {}
        }
    }

    val dataSourceFactory = remember { DefaultHttpDataSource.Factory() }
    val player = remember {
        ExoPlayer.Builder(context)
            .build().apply {
            playWhenReady = true
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        playerError = null
                        durationMs = duration.coerceAtLeast(0L)
                        detailLoaded = true
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlayerError(error: PlaybackException) {
                    playerError = "播放出错: " + (error.message ?: "未知错误")
                }
            })
        }
    }

    // Position tracking loop
    LaunchedEffect(Unit) {
        while (true) {
            if (player.isPlaying || isSwiping) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.coerceAtLeast(durationMs)
            }
            delay(500L)
        }
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, userInteracting) {
        if (controlsVisible && !userInteracting && !isSwiping) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    fun loadUrl(url: String) {
        if (url.isEmpty()) return
        playerError = null
        var finalUrl = if (url.startsWith("/")) {
            baseUrl.trimEnd('/') + url
        } else {
            url
        }
        try {
            val effectiveUrl: String
            if (finalUrl.contains(".m3u8", ignoreCase = true) && !finalUrl.contains("/proxy/hls")) {
                val encoded = java.net.URLEncoder.encode(finalUrl, "UTF-8")
                effectiveUrl = baseUrl.trimEnd('/') + "/api/proxy/hls?url=" + encoded + "&skip_seconds=5"
            } else {
                effectiveUrl = finalUrl
            }
            player.stop()
            player.clearMediaItems()
            val isHls = effectiveUrl.contains(".m3u8", ignoreCase = true) || effectiveUrl.contains("/proxy/hls", ignoreCase = true) || effectiveUrl.contains("application/vnd.apple.mpegurl")
            val mediaItem = MediaItem.fromUri(effectiveUrl)
            if (isHls) {
                val hlsSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                player.setMediaSources(listOf(hlsSource))
            } else {
                player.setMediaItem(mediaItem)
            }
            player.prepare()
        } catch (e: Exception) {
            playerError = "加载失败: " + (e.message ?: "未知错误")
        }
    }

    LaunchedEffect(playUrl) {
        if (playUrl.isNotEmpty()) {
            loadUrl(playUrl)
        }
    }

    // Load data - always show data as soon as it arrives
    LaunchedEffect(movieId) {
        try {
            movie = repository.getMovieDetail(movieId)
            // Mark detail loaded immediately, don't wait for episodes
            detailLoaded = true
            val epResp = repository.getMovieEpisodes(movieId)
            episodes = epResp.episodes.sortedBy { it.episodeNumber }
            if (currentEpisode > episodes.size) currentEpisode = 1
            val urlResp = repository.getPlayUrl(movieId, currentEpisode)
            playUrl = urlResp.url
            currentSources = urlResp.sources
            isFavorite = repository.getFavoritesStatus(movieId)
            repository.addHistory(movieId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isLoading = false
    }

    LaunchedEffect(currentEpisode) {
        playerError = null
        if (currentEpisode > 0 && movie != null) {
            try {
                val urlResp = repository.getPlayUrl(movieId, currentEpisode)
                playUrl = urlResp.url
                positionMs = 0L
                swipeSeekDelta = 0L
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    BackHandler(enabled = isLandscape) {
        if (isLandscape) {
            toggleFullscreen(false)
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLandscape) PlayerBg else PageBg)
    ) {
        // ============ Player section ============
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isLandscape) Modifier.fillMaxHeight()
                    else Modifier.aspectRatio(16f / 9f)
                )
                .background(PlayerBg)
        ) {
            PlayerSurface(
                player = player,
                playUrl = playUrl,
                playerError = playerError,
                isBuffering = isBuffering,
                isLoading = isLoading && !detailLoaded,
                currentEpisode = currentEpisode,
                onTap = { controlsVisible = !controlsVisible },
                onRetry = {
                    if (playUrl.isNotEmpty()) loadUrl(playUrl)
                },
                isSwiping = isSwiping,
                swipeSeekDelta = swipeSeekDelta,
                onSwipeStart = {
                    isSwiping = true
                    swipeStartPosition = player.currentPosition.coerceAtLeast(0L)
                    swipeSeekDelta = 0L
                    userInteracting = true
                },
                onSwipe = { delta ->
                    swipeSeekDelta = (swipeSeekDelta + delta).coerceIn(-300000L, 300000L)
                },
                onSwipeEnd = {
                    isSwiping = false
                    val newPos = (swipeStartPosition + swipeSeekDelta).coerceIn(0L, durationMs.coerceAtLeast(1L))
                    player.seekTo(newPos)
                    positionMs = newPos
                    swipeSeekDelta = 0L
                    userInteracting = false
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top bar overlay - compact in landscape
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible && !isSwiping,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                PlayerTopBar(
                    title = movie?.title ?: "",
                    episodeInfo = if ((movie?.episodeTotal ?: 0) > 1) "第${currentEpisode}集" else "",
                    onBack = {
                        if (isLandscape) toggleFullscreen(false) else onBack()
                    },
                    isLandscape = isLandscape
                )
            }

            // Swipe seek indicator - centered during swipe
            if (isSwiping) {
                val seekPos = (swipeStartPosition + swipeSeekDelta).coerceIn(0L, durationMs.coerceAtLeast(1L))
                SwipeSeekIndicator(
                    seekMs = seekPos,
                    deltaMs = swipeSeekDelta,
                    durationMs = durationMs,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Bottom controls bar - single row integrated design
            if (!isSwiping) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    PlayerBottomBar(
                        isPlaying = isPlaying,
                        playbackSpeed = playbackSpeed,
                        currentMs = positionMs,
                        durationMs = durationMs,
                        onSeek = { newPos ->
                            player.seekTo(newPos)
                            positionMs = newPos
                        },
                        onTogglePlay = {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        onSpeed = {
                            val currentIdx = speedOptions.indexOf(playbackSpeed)
                            val nextIdx = (currentIdx + 1) % speedOptions.size
                            playbackSpeed = speedOptions[nextIdx]
                            player.setPlaybackSpeed(playbackSpeed)
                        },
                        onSeekStart = { userInteracting = true },
                        onSeekEnd = { userInteracting = false },
                        onFullscreen = { toggleFullscreen(!isLandscape) },
                        isFullscreen = isLandscape
                    )
                }
            }

            // Center play button when paused and controls hidden
            androidx.compose.animation.AnimatedVisibility(
                visible = !isPlaying && !controlsVisible && !isSwiping,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable {
                            player.play()
                            controlsVisible = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Episode selector panel (below player, portrait only)
        if (showEpisodePanel && episodes.isNotEmpty() && !isLandscape) {
            EpisodeSelectorPanel(
                episodes = episodes,
                currentEpisode = currentEpisode,
                onSelect = { ep ->
                    currentEpisode = ep
                    showEpisodePanel = false
                },
                onClose = { showEpisodePanel = false }
            )
        }

        // ============ Info section (portrait only) ============
        if (!isLandscape) {
            if (isLoading && !detailLoaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                val m = movie
                Box(modifier = Modifier.fillMaxSize().background(PageBg)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Title + action buttons row
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    m?.title ?: "",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (episodes.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = { showEpisodePanel = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = PrimaryLight.copy(alpha = 0.15f),
                                            contentColor = Primary
                                        )
                                    ) {
                                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("第${currentEpisode}集 / 共${episodes.size}集", fontSize = 12.sp)
                                    }
                                }
                                if (currentSources.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    SourceChip(label = "播放源", onClick = { showSourceDialog = true })
                                }
                            }
                        }

                        // Action buttons row
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                PlayerAction(
                                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    label = "收藏",
                                    tint = if (isFavorite) HotRed else TextSecondary,
                                    onClick = {
                                        scope.launch {
                                            val fav = repository.toggleFavorite(movieId)
                                            isFavorite = fav
                                        }
                                    }
                                )
                                PlayerAction(
                                    icon = Icons.Default.Comment,
                                    label = "评论",
                                    tint = TextSecondary,
                                    onClick = { toast("评论功能即将上线") }
                                )
                                PlayerAction(
                                    icon = Icons.Default.Share,
                                    label = "分享",
                                    tint = TextSecondary,
                                    onClick = { toast("分享功能即将上线") }
                                )
                            }
                        }

                        // Rating + meta info
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (m?.rating != null && m.rating > 0) {
                                    Text(
                                        "★ %.1f".format(m.rating),
                                        fontSize = 14.sp,
                                        color = Gold,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                if (m?.year != null && m.year > 0) {
                                    Text("${m.year}", fontSize = 13.sp, color = TextHint)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (m?.type != null && m.type.isNotEmpty()) {
                                    Surface(color = TabBg, shape = RoundedCornerShape(4.dp)) {
                                        Text(m.type, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                if (m?.region != null && m.region.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(color = TabBg, shape = RoundedCornerShape(4.dp)) {
                                        Text(m.region, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }

                        // Director and actors (show if available)
                        if (m?.director != null && m.director.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("导演：${m.director}", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                        if (m?.actors != null && m.actors.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("主演：${m.actors.take(5).joinToString(" / ")}", fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        // Introduction
                        val intro = m?.introduction ?: ""
                        if (intro.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                val maxLines = if (showIntroduction) Int.MAX_VALUE else 3
                                Text(
                                    intro,
                                    fontSize = 14.sp,
                                    color = TextPrimary,
                                    lineHeight = 22.sp,
                                    maxLines = maxLines,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (intro.length > 100) {
                                    TextButton(
                                        onClick = { showIntroduction = !showIntroduction },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            if (showIntroduction) "收起" else "展开全部",
                                            fontSize = 13.sp,
                                            color = Primary
                                        )
                                    }
                                }
                            }
                        }

                        // Episode list grid (inline)
                        if (episodes.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("选集", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            val chunkedEps = episodes.take(99).chunked(5)
                            items(chunkedEps.size) { rowIdx ->
                                val rowEps = chunkedEps[rowIdx]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowEps.forEach { ep ->
                                        val selected = ep.episodeNumber == currentEpisode
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) Primary else TabBg)
                                                .clickable {
                                                    currentEpisode = ep.episodeNumber
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "${ep.episodeNumber}",
                                                fontSize = 14.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) White else TextPrimary
                                            )
                                        }
                                    }
                                    val remaining = 5 - rowEps.size
                                    if (remaining > 0) {
                                        repeat(remaining) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // Bottom spacing
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    // Speed selection dialog
    

    // Source selection dialog
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("选择播放源", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    currentSources.forEach { src ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSource = src.sourceId; showSourceDialog = false }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSource == src.sourceId,
                                onClick = { selectedSource = src.sourceId; showSourceDialog = false },
                                colors = RadioButtonDefaults.colors(selectedColor = Primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(src.sourceName, fontSize = 15.sp, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) {
                    Text("关闭", color = TextSecondary)
                }
            }
        )
    }
}

// ========== REUSABLE COMPONENTS ==========

@Composable
private fun PlayerSurface(
    player: ExoPlayer,
    playUrl: String,
    playerError: String?,
    isBuffering: Boolean,
    isLoading: Boolean,
    currentEpisode: Int,
    onTap: () -> Unit,
    onRetry: () -> Unit,
    isSwiping: Boolean,
    swipeSeekDelta: Long,
    onSwipeStart: () -> Unit,
    onSwipe: (Long) -> Unit,
    onSwipeEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (playUrl.isNotEmpty() && playerError == null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = {
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { onSwipeStart() },
                            onDragEnd = { onSwipeEnd() },
                            onDragCancel = { onSwipeEnd() },
                            onHorizontalDrag = { _, dragAmount ->
                                val seekDelta = (dragAmount * 300).toLong()
                                onSwipe(seekDelta)
                            }
                        )
                    }
            )
        }

        // Loading overlay - only show if not even basic data loaded
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(PlayerBg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = White.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("正在加载...", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        } else if (playerError != null) {
            // Error state
            Box(modifier = Modifier.fillMaxSize().background(PlayerBg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = White.copy(alpha = 0.7f), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("视频加载失败", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("第${currentEpisode}集", color = White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text((playerError ?: "").take(60), color = White.copy(alpha = 0.4f), fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = White.copy(alpha = 0.15f), contentColor = White)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重新加载", fontSize = 13.sp)
                    }
                }
            }
        } else if (isBuffering && !isSwiping) {
            // Buffering overlay
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            }
        }

        // Empty state when no URL yet
        if (playUrl.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(PlayerBg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Movie, contentDescription = null,
                        tint = White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("加载播放地址...", color = White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SwipeSeekIndicator(
    seekMs: Long, deltaMs: Long, durationMs: Long, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (deltaMs > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null, tint = White, modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(formatTimeFull(seekMs), color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("/ ${formatTimeFull(durationMs)}", color = White.copy(alpha = 0.5f), fontSize = 12.sp)
            if (abs(deltaMs) > 1000) {
                val deltaStr = if (deltaMs > 0) "+" else ""
                Spacer(modifier = Modifier.height(2.dp))
                Text("${deltaStr}${formatTimeCompact(abs(deltaMs))}",
                    color = if (deltaMs > 0) Green else HotRed,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    episodeInfo: String,
    onBack: () -> Unit,
    isLandscape: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                    startY = 0f
                )
            )
            .height(if (isLandscape) 52.dp else 56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(start = 2.dp, end = 4.dp).statusBarsPadding()
        ) {
            // Back button - use margin/padding to position properly
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Title info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episodeInfo.isNotEmpty()) {
                    Text(
                        episodeInfo,
                        fontSize = 11.sp,
                        color = White.copy(alpha = 0.7f)
                    )
                }
            }

            // Spacer instead of fullscreen button (moved to bottom bar)
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

// Integrated bottom bar: play/pause, progress slider, time, speed - all on one row
@Composable
private fun PlayerBottomBar(
    isPlaying: Boolean,
    playbackSpeed: Float,
    currentMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSpeed: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onFullscreen: () -> Unit = {},
    isFullscreen: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    startY = -40f
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = White,
                    modifier = Modifier.size(24.dp)
                )
            }

            SeekBarWidget(
                currentMs = currentMs,
                durationMs = durationMs,
                onSeek = onSeek,
                onSeekStart = onSeekStart,
                onSeekEnd = onSeekEnd,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = formatTimeCompact(currentMs),
                    color = White,
                    fontSize = 11.sp,
                )
                Text(
                    text = " / ",
                    color = White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
                Text(
                    text = formatTimeCompact(durationMs),
                    color = White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }

            TextButton(
                onClick = onSpeed,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = if (abs(playbackSpeed - 1.0f) < 0.01f) "1x" else "${playbackSpeed}x",
                    color = White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            IconButton(
                onClick = onFullscreen,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                    tint = White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** Improved draggable progress bar with larger touch target */
@Composable
private fun SeekBarWidget(
    currentMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragRatio by remember { mutableFloatStateOf(0f) }
    val ratio = (currentMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val displayRatio = if (isDragging) dragRatio else ratio

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onSeekStart()
                        dragRatio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek((dragRatio * safeDuration).toLong())
                        onSeekEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        onSeekEnd()
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragRatio = (dragRatio + dragAmount / size.width.toFloat()).coerceIn(0f, 1f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val tapRatio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek((tapRatio * safeDuration).toLong())
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(White.copy(alpha = 0.2f))
        ) {
            // Progress fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = displayRatio)
                    .background(Primary)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
        // Thumb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = displayRatio)
                    .background(Primary)
                    .clip(RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Primary)
                        .background(
                            Brush.horizontalGradient(listOf(Primary, PrimaryLight))
                        )
                )
            }
        }
    }
}

// Compact episode selector panel
@Composable
private fun EpisodeSelectorPanel(
    episodes: List<Episode>,
    currentEpisode: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    val displayEps = episodes.take(99)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        color = White,
        shadowElevation = 4.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选集", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭",
                        tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            // Episode grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayEps, key = { it.episodeNumber }) { ep ->
                    val selected = ep.episodeNumber == currentEpisode
                    Box(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Primary else TabBg)
                            .clickable { onSelect(ep.episodeNumber) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${ep.episodeNumber}",
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) White else TextPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// Time formatting helpers
private fun formatTimeCompact(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${String.format("%02d", minutes)}:${String.format("%02d", seconds)}"
    } else {
        "${minutes}:${String.format("%02d", seconds)}"
    }
}

private fun formatTimeFull(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// UI helper composables
@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = tint,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun SourceChip(label: String, onClick: () -> Unit) {
    Surface(
        color = PrimarySoft,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null,
                tint = Primary, modifier = Modifier.size(16.dp))
        }
    }
}

private fun <T> List<T>.chunked(size: Int): List<List<T>> {
    val result = mutableListOf<List<T>>()
    for (i in indices step size) {
        result.add(subList(i, (i + size).coerceAtMost(this.size)))
    }
    return result
}
