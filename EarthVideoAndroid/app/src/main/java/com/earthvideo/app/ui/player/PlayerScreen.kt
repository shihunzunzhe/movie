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
import androidx.compose.ui.draw.rotate
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
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import com.earthvideo.app.data.api.RetrofitClient
import com.earthvideo.app.data.download.PlaybackPrefetch
import com.earthvideo.app.data.model.*
import com.earthvideo.app.data.repository.MovieRepository
import com.earthvideo.app.ui.theme.*
import com.earthvideo.app.ui.components.decodeHtml
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
    onBack: () -> Unit,
    localDir: String? = null
) {
    var movie by remember { mutableStateOf<Movie?>(null) }
    var episodes by remember { mutableStateOf(listOf<Episode>()) }
    var currentEpisode by remember { mutableIntStateOf(initialEpisode.coerceAtLeast(1)) }
    var playUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(false) }
    var showIntroduction by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    var currentSources by remember { mutableStateOf(listOf<PlaySource>()) }
    var selectedSource by remember { mutableStateOf("default") }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    val speedOptions = listOf(1.0f, 1.5f, 2.0f, 3.0f, 0.5f)
    var userInteracting by remember { mutableStateOf(false) }
    // Auto-switch guard: prevents re-entrant source switching when
    // onPlayerError fires while LaunchedEffect is already reloading.
    var _autoSwitching by remember { mutableStateOf(false) }
    // Direct CDN play first; flip to true to retry through /api/proxy/hls.
    var useProxy by remember { mutableStateOf(false) }
    // Swipe seek state
    var swipeSeekDelta by remember { mutableLongStateOf(0L) }
    var isSwiping by remember { mutableStateOf(false) }
    var swipeStartPosition by remember { mutableLongStateOf(0L) }
    // Data loaded flag for detail page
    var detailLoaded by remember { mutableStateOf(false) }
    // Track fullscreen state separately (portrait videos stay portrait)
    var isFullscreen by remember { mutableStateOf(false) }
    // Actual video aspect ratio (width / height), updated from player
    
    val DEFAULT_ASPECT = 16f / 9f
    var videoAspect by remember { mutableFloatStateOf(DEFAULT_ASPECT) }
    // Preloaded next-episode play URL (prevents stall when switching episodes)
    var preloadedEpisode by remember { mutableIntStateOf(-1) }
    var preloadedUrl by remember { mutableStateOf("") }
    // Whole-episode disk prefetch state
    var prefetchedKeys by remember { mutableStateOf(listOf<String>()) }
    var prefetchProgress by remember { mutableIntStateOf(-1) } // -1 idle/done; 0..99 prefetching
    // Batch download dialog selection
    var selectedDownloadEps by remember { mutableStateOf(setOf<Int>()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val baseUrl = remember { RetrofitClient.getBaseUrl() }

    // Toggle fullscreen — landscape for wide videos (aspect >= 1), stay portrait
    // for portrait videos so the user can see the full frame clearly.
    // Portrait videos use RESIZE_MODE_FIT and show completely (with side bars).
    val isPortraitVideo = videoAspect < 1.0f
    fun toggleFullscreen(full: Boolean) {
        isFullscreen = full
        try {
            val activity = context as? android.app.Activity
            if (activity != null) {
                if (full) {
                    activity.requestedOrientation = if (isPortraitVideo) {
                        // Portrait video: stay portrait, expand to fill width
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else {
                        // Landscape video: rotate to landscape
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                } else {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(isLandscape, isFullscreen) {
        val immersive = isLandscape || isFullscreen
        if (immersive) {
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

    // HTTP source ＋ shared 300MB disk cache. Play the CDN URL directly (same
    // path a browser uses) so nested HLS masters like
    //   index.m3u8 → 2000k/hls/index.m3u8 → foo.ts
    // resolve against the media playlist directory. The server proxy is only
    // a fallback when the CDN is unreachable from the device.
    val playerUa = PlaybackPrefetch.PLAYER_UA
    val dataSourceFactory = remember {
        CacheDataSource.Factory()
            .setCache(PlaybackPrefetch.cache(context))
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setUserAgent(playerUa)
                    .setAllowCrossProtocolRedirects(true)
            )
    }
    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(60000, 250000, 10000, 30000)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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
                    // Episode finished: auto-play next episode if available.
                    if (playbackState == Player.STATE_ENDED) {
                        if (currentEpisode < episodes.size) {
                            val nextEp = currentEpisode + 1
                            scope.launch {
                                controlsVisible = true
                                // Show a brief toast notification
                                toast("即将播放第${nextEp}集")
                                // Small delay so user sees the toast
                                kotlinx.coroutines.delay(600)
                                currentEpisode = nextEp
                            }
                        } else {
                            // All episodes done — show completion state briefly.
                            scope.launch {
                                controlsVisible = true
                                toast("全部播放完成")
                            }
                            // Release disk cache.
                            if (prefetchedKeys.isNotEmpty()) {
                                PlaybackPrefetch.releaseSegments(context, prefetchedKeys)
                                prefetchedKeys = emptyList()
                                prefetchProgress = -1
                            }
                        }
                    }
                    // Clean up disk cache when switching episodes
                    if (playbackState == Player.STATE_READY) {
                        if (prefetchedKeys.isNotEmpty()) {
                            // Keep prefetch from the new episode LaunchedEffect
                        }
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    // Adjust the player area to the video's real resolution/aspect ratio.
                    var w = videoSize.width
                    var h = videoSize.height
                    val rot = videoSize.unappliedRotationDegrees
                    if (rot == 90 || rot == 270) {
                        val t = w; w = h; h = t
                    }
                    if (w > 0 && h > 0) {
                        videoAspect = w.toFloat() / h.toFloat()
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    val msg = error.message ?: ""
                    val cause = error.cause?.message ?: ""
                    val isHttpFail = msg.contains("Source error", ignoreCase = true) ||
                        cause.contains("Response code", ignoreCase = true) ||
                        cause.contains("404") || cause.contains("403") ||
                        cause.contains("Unable to connect", ignoreCase = true)
                    // Direct CDN play failed: retry once through the HLS proxy so
                    // the phone never has to reach the CDN itself.
                    if (isHttpFail && !useProxy && playUrl.isNotEmpty() &&
                        !playUrl.startsWith("file://")
                    ) {
                        useProxy = true
                        return
                    }
                    // Auto-switch to the next source when the current one is dead.
                    if (currentSources.size > 1 && !_autoSwitching) {
                        val idx = currentSources.indexOfFirst { it.sourceId == selectedSource }
                        if (idx >= 0 && idx < currentSources.size - 1) {
                            _autoSwitching = true
                            val next = currentSources[idx + 1]
                            selectedSource = next.sourceId
                            return  // LaunchedEffect(currentEpisode, selectedSource) reloads
                        }
                    }
                    playerError = "播放出错: " + (msg.take(80))
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
            if (prefetchedKeys.isNotEmpty()) {
                PlaybackPrefetch.releaseSegments(context, prefetchedKeys)
                prefetchedKeys = emptyList()
            }
            player.stop()
            player.release()
        }
    }

    // Proxy the m3u8 through the server. Used as a fallback when the device
    // cannot reach the CDN. Nested playlists stay nested; the proxy rewrites
    // each URI against THAT playlist's URL.
    fun proxyHlsUrl(finalUrl: String): String {
        val encoded = java.net.URLEncoder.encode(finalUrl, "UTF-8")
        return baseUrl.trimEnd('/') + "/api/proxy/hls?url=" + encoded
    }

    fun loadUrl(url: String) {
        if (url.isEmpty()) return
        playerError = null
        // Reset to the default area ratio until the new video reports its real size.
        videoAspect = DEFAULT_ASPECT
        val finalUrl = if (url.startsWith("/")) {
            baseUrl.trimEnd('/') + url
        } else {
            url
        }
        try {
            // Play CDN URLs directly (browser path). Nested HLS masters then
            // resolve relative segments against the media playlist directory,
            // which is the only path the CDN actually serves.
            val effectiveUrl = finalUrl
            player.stop()
            player.clearMediaItems()
            val isLocal = effectiveUrl.startsWith("file://")
            val isHls = isLocal ||
                effectiveUrl.contains(".m3u8", ignoreCase = true) ||
                effectiveUrl.contains("/proxy/hls", ignoreCase = true) ||
                effectiveUrl.contains("application/vnd.apple.mpegurl")
            val mediaItem = MediaItem.fromUri(effectiveUrl)
            if (isHls) {
                val factory = if (isLocal) {
                    DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory())
                } else {
                    dataSourceFactory
                }
                val hlsSource = HlsMediaSource.Factory(factory).createMediaSource(mediaItem)
                player.setMediaSources(listOf(hlsSource))
            } else {
                player.setMediaItem(mediaItem)
            }
            player.prepare()
        } catch (e: Exception) {
            playerError = "加载失败: " + (e.message ?: "未知错误")
        }
    }

    // Actively prefetch the WHOLE current episode into the shared disk cache.
    // Segments previously cached are released first so the full episode fits.
    LaunchedEffect(currentEpisode, movieId, playUrl) {
        if (prefetchedKeys.isNotEmpty()) {
            PlaybackPrefetch.releaseSegments(context, prefetchedKeys)
            prefetchedKeys = emptyList()
            prefetchProgress = -1
        }
        if (playUrl.isEmpty() || playUrl.startsWith("file://")) return@LaunchedEffect
        try {
            prefetchProgress = 0
            val keys = PlaybackPrefetch.prefetchEpisode(
                context,
                playUrl
            ) { done, total ->
                if (total > 0) prefetchProgress = done * 100 / total
            }
            prefetchedKeys = keys
            prefetchProgress = -1
        } catch (e: Exception) {
            prefetchProgress = -1
        }
    }

    LaunchedEffect(playUrl, useProxy) {
        if (playUrl.isEmpty()) return@LaunchedEffect
        if (useProxy && !playUrl.startsWith("file://")) {
            loadUrl(proxyHlsUrl(playUrl))
        } else {
            loadUrl(playUrl)
        }
    }

    // Load data - always show data as soon as it arrives
    LaunchedEffect(movieId) {
        try {
            if (localDir != null) {
                // Offline playback of a downloaded episode (no network needed).
                val localEpisode = repository.localEpisode(localDir)
                movie = repository.localMovie(localDir)
                episodes = listOf(
                    Episode(
                        episodeNumber = localEpisode,
                        title = "第${localEpisode}集",
                        duration = 0,
                        current = true
                    )
                )
                currentEpisode = localEpisode
                val idxPath = repository.localIndexPath(localDir)
                playUrl = if (idxPath != null) "file://$idxPath" else ""
                currentSources = emptyList()
                detailLoaded = true
            } else {
                movie = repository.getMovieDetail(movieId)
                val epResp = repository.getMovieEpisodes(movieId)
                episodes = epResp.episodes.sortedBy { it.episodeNumber }
                // Mark detail loaded AFTER movie, episodes, and play URL are all fetched
                detailLoaded = true
                if (currentEpisode > episodes.size) currentEpisode = 1
                val urlResp = repository.getPlayUrl(movieId, currentEpisode)
                playUrl = urlResp.url
                useProxy = false
                currentSources = urlResp.sources
                selectedSource = urlResp.sources.firstOrNull()?.sourceId ?: "default"
                isFavorite = repository.isLocalFavorite(movieId)
                movie?.let { repository.addLocalHistory(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isLoading = false
    }

    LaunchedEffect(currentEpisode, selectedSource) {
        _autoSwitching = false
        playerError = null
        if (currentEpisode > 0 && movie != null) {
            try {
                // Use the preloaded URL when it matches the target episode.
                val urlResp: PlayUrlResponse =
                    if (preloadedEpisode == currentEpisode && preloadedUrl.isNotEmpty()) {
                        PlayUrlResponse(movieId, currentEpisode, preloadedUrl, currentSources)
                    } else {
                        repository.getPlayUrl(movieId, currentEpisode, selectedSource)
                    }
                playUrl = urlResp.url
                useProxy = false
                positionMs = 0L
                swipeSeekDelta = 0L
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Background preload of the NEXT episode, so switching episodes starts instantly.
    LaunchedEffect(movieId, currentEpisode, selectedSource, episodes.size) {
        if (currentEpisode < episodes.size && movie != null) {
            try {
                val resp = repository.getPlayUrl(movieId, currentEpisode + 1, selectedSource)
                if (resp.url.isNotEmpty()) {
                    preloadedEpisode = currentEpisode + 1
                    preloadedUrl = resp.url
                }
            } catch (_: Exception) {}
        }
    }

    fun shareMovie() {
        val title = movie?.title ?: "视频"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, "看「$title」第${currentEpisode}集，推荐给你！\n$baseUrl")
            putExtra(android.content.Intent.EXTRA_SUBJECT, "大地视频 - $title")
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享到"))
    }

    BackHandler(enabled = isLandscape || isFullscreen) {
        if (isLandscape || isFullscreen) {
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
        // Portrait: fixed 16:9 container height (same for landscape & portrait videos).
        // Portrait videos (9:16) show letterboxed (side bars) via RESIZE_MODE_FIT,
        // so the info section below is always visible and the player never swallows
        // the entire screen.
        // Portrait fullscreen: expand to fill available height (video still letterboxed).
        // Landscape (fullscreen): cover the entire screen area.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        isLandscape -> Modifier.fillMaxSize()
                        isFullscreen -> Modifier.fillMaxHeight() // portrait fullscreen
                        else -> Modifier.aspectRatio(16f / 9f)
                    }
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
                isLandscape = isLandscape,
                onTap = { controlsVisible = !controlsVisible },
                onRetry = {
                    scope.launch {
                        try {
                            // Force the server to re-fetch from 5o5k.com
                            // (skip its cached dead URL).
                            val urlResp = repository.getPlayUrl(
                                movieId, currentEpisode, selectedSource, force = 1
                            )
                            if (urlResp.url.isNotEmpty()) {
                                playUrl = urlResp.url
                                currentSources = urlResp.sources
                                selectedSource = urlResp.actualSource ?: selectedSource
                                loadUrl(playUrl)
                            } else {
                                playerError = "该资源暂时无法播放，请稍后重试"
                            }
                        } catch (e: Exception) {
                            playerError = "加载失败: ${e.message?.take(40) ?: "未知错误"}"
                        }
                    }
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
                showSourceSwitch = currentSources.size > 1,
                onSwitchSource = { showSourceDialog = true },
                modifier = Modifier.fillMaxSize()
            )

            // Prefetch progress overlay removed (was "整集缓存 N%")
            // The prefetch still runs in the background to fill the disk cache.

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
                        onFullscreen = { toggleFullscreen(!isFullscreen) },
                        isFullscreen = isFullscreen
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // ===== Title =====
                        item {
                            Text(
                                decodeHtml(m?.title ?: ""),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // ===== Rating + meta tags =====
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (m?.rating != null && m.rating > 0) {
                                    Text(
                                        "★ %.1f".format(m.rating),
                                        fontSize = 15.sp,
                                        color = Gold,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                if (m?.year != null && m.year > 0) {
                                    Text("${m.year}", fontSize = 13.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (m?.type != null && m.type.isNotEmpty()) {
                                    MetaTag(decodeHtml(m.type))
                                }
                                if (m?.region != null && m.region.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    MetaTag(decodeHtml(m.region))
                                }
                            }
                        }

                        // ===== Director / actors =====
                        if (m?.director != null && m.director.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("导演：${decodeHtml(m.director)}", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                        if (m?.actors != null && m.actors.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "主演：${m.actors.take(5).joinToString(" / ")}",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // ===== Action row =====
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                RoundAction(
                                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    label = "收藏",
                                    tint = if (isFavorite) HotRed else TextSecondary,
                                    active = isFavorite,
                                    onClick = {
                                        scope.launch {
                                            movie?.let { fm -> isFavorite = repository.toggleLocalFavorite(fm) }
                                        }
                                    }
                                )
                                RoundAction(
                                    icon = Icons.Default.Comment,
                                    label = "评论",
                                    tint = TextSecondary,
                                    active = false,
                                    onClick = { showCommentDialog = true }
                                )
                                RoundAction(
                                    icon = Icons.Default.Share,
                                    label = "分享",
                                    tint = TextSecondary,
                                    active = false,
                                    onClick = { shareMovie() }
                                )
                                if (localDir == null && playUrl.isNotEmpty() && !playUrl.startsWith("file://")) {
                                    RoundAction(
                                        icon = Icons.Default.CloudDownload,
                                        label = "下载",
                                        tint = TextSecondary,
                                        active = false,
                                        onClick = {
                                            selectedDownloadEps = setOf(currentEpisode)
                                            showDownloadDialog = true
                                        }
                                    )
                                }
                            }
                        }

                        // ===== Play source selector =====
                        if (currentSources.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = CardBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showSourceDialog = true }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("播放源", fontSize = 13.sp, color = TextSecondary)
                                        Spacer(modifier = Modifier.weight(1f))
                                        val currentSourceName = currentSources
                                            .find { it.sourceId == selectedSource }?.sourceName
                                            ?: currentSources.firstOrNull()?.sourceName
                                            ?: "默认播放"
                                        Text(
                                            currentSourceName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Primary
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // ===== Introduction card =====
                        val intro = m?.introduction ?: ""
                        if (intro.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = CardBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("简介", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val maxLines = if (showIntroduction) Int.MAX_VALUE else 4
                                        Text(
                                            decodeHtml(intro),
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
                            }
                        }

                        // ===== Episode list card (only after data loaded) =====
                        if (detailLoaded && episodes.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = CardBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "选集",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "共${episodes.size}集",
                                                fontSize = 12.sp,
                                                color = TextHint
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val chunkedEps = episodes.take(99).chunked(5)
                                        chunkedEps.forEach { rowEps ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                rowEps.forEach { ep ->
                                                    val selected = ep.episodeNumber == currentEpisode
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(40.dp)
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
                                }
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
    

    // Batch download dialog (multiple episodes; DownloadManager runs them one by one)
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "批量下载",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        selectedDownloadEps = episodes.map { it.episodeNumber }.toSet()
                    }) {
                        Text("全选", color = Primary)
                    }
                }
            },
            text = {
                Column {
                    Text(
                        "「${movie?.title ?: ""}」已选 ${selectedDownloadEps.size} 集（共${episodes.size}集）",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.heightIn(max = 260.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(episodes.take(150), key = { it.episodeNumber }) { ep ->
                            val selected = ep.episodeNumber in selectedDownloadEps
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Primary else TabBg)
                                    .clickable {
                                        selectedDownloadEps = if (selected) {
                                            selectedDownloadEps - ep.episodeNumber
                                        } else {
                                            selectedDownloadEps + ep.episodeNumber
                                        }
                                    },
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
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "将逐个串行下载（并发 1），可随时在「我的下载」查看进度并离线观看。",
                        fontSize = 12.sp,
                        color = TextHint
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sel = selectedDownloadEps.sorted()
                    showDownloadDialog = false
                    if (sel.isEmpty()) return@TextButton
                    scope.launch {
                        val urls = mutableListOf<Pair<Int, String>>()
                        for (ep in sel) {
                            val u = if (ep == currentEpisode) {
                                playUrl
                            } else {
                                runCatching {
                                    repository.getPlayUrl(movieId, ep, selectedSource).url
                                }.getOrNull() ?: ""
                            }
                            urls.add(ep to u)
                        }
                        movie?.let { m -> repository.downloadMovies(m, urls) }
                        toast("已加入下载队列（${urls.size} 集）")
                    }
                }) {
                    Text("开始下载", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    // Comment dialog (local, persisted)
    if (showCommentDialog) {
        val comments = remember(movieId, showCommentDialog) { repository.getComments(movieId) }
        AlertDialog(
            onDismissRequest = { showCommentDialog = false; commentInput = "" },
            title = { Text("评论", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp)) {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (comments.isEmpty()) {
                            item {
                                Text("暂无评论，来写第一条吧", fontSize = 13.sp, color = TextHint,
                                    modifier = Modifier.padding(vertical = 16.dp))
                            }
                        } else {
                            items(comments, key = { it.time }) { c ->
                                Text(c.text, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(vertical = 6.dp))
                                Text(
                                    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(c.time)),
                                    fontSize = 10.sp, color = TextHint
                                )
                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it.take(200) },
                            placeholder = { Text("说点什么...", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary, unfocusedBorderColor = Divider
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                if (commentInput.isNotBlank()) {
                                    repository.addComment(movieId, commentInput.trim())
                                    commentInput = ""
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "发送", tint = White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCommentDialog = false; commentInput = "" }) {
                    Text("关闭", color = TextSecondary)
                }
            }
        )
    }

    // Source selection dialog
    if (showSourceDialog) {
        val dialogSourceName = currentSources
            .find { it.sourceId == selectedSource }?.sourceName
            ?: "播放源"
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("切换播放源", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "当前: $dialogSourceName",
                        fontSize = 12.sp,
                        color = Primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
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
    isLandscape: Boolean,
    onTap: () -> Unit,
    onRetry: () -> Unit,
    isSwiping: Boolean,
    swipeSeekDelta: Long,
    onSwipeStart: () -> Unit,
    onSwipe: (Long) -> Unit,
    onSwipeEnd: () -> Unit,
    showSourceSwitch: Boolean,
    onSwitchSource: () -> Unit,
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
                update = { view ->
                    // Always use FIT so the complete video is visible.
                    // Portrait (9:16) videos show with side bars in landscape;
                    // landscape (16:9) videos fill normally.
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
            // Error state — clean, minimalist design
            Box(modifier = Modifier.fillMaxSize().background(PlayerBg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Clean play icon with a subtle error accent
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(White.copy(alpha = 0.07f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Play icon
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = White.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        )
                        // Thin red slash across
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 2.dp)
                                .rotate(45f)
                                .background(HotRed.copy(alpha = 0.6f))
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text("视频加载失败", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("第${currentEpisode}集", color = White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text((playerError ?: "").take(60), color = White.copy(alpha = 0.4f), fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = White.copy(alpha = 0.15f),
                            contentColor = White
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重新加载", fontSize = 13.sp)
                    }
                    // Show source-switch button when multiple sources exist
                    if (showSourceSwitch) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onRetry(); onSwitchSource() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = White.copy(alpha = 0.8f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, White.copy(alpha = 0.25f))
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("切换播放源", fontSize = 12.sp)
                        }
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

// Small rounded meta label (e.g. 剧情, 大陆)
@Composable
private fun MetaTag(text: String) {
    Surface(
        color = TabBg,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

// Circular icon action with label (favorite / comment / share)
@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (active) tint.copy(alpha = 0.15f)
                    else PrimaryLight.copy(alpha = 0.10f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) tint else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = if (active) tint else TextSecondary,
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
