package com.earthvideo.app.data.download

import android.content.Context
import android.util.Log
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Process-wide disk cache shared by every player. Exposes a way to actively
 * prefetch the whole current episode into the cache so playback never stalls
 * on slow networks, and to release the episode's segments after playback ends.
 */
object PlaybackPrefetch {

    private const val TAG = "PlaybackPrefetch"
    private const val MAX_CACHE_BYTES = 300L * 1024 * 1024
    const val PLAYER_UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cache: SimpleCache? = null

    /** Get (or create once) the shared disk cache. */
    fun cache(context: Context): SimpleCache {
        val existing = cache
        if (existing != null) return existing
        synchronized(this) {
            cache?.let { return it }
            val created = SimpleCache(
                File(context.cacheDir, "media_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            )
            cache = created
            return created
        }
    }

    /**
     * Fetch [playlistUrl] (usually the ad-removal proxy URL), resolve the media
     * playlist, and force every segment into [cache]. Network-sourced segments
     * land in the same cache the player reads from, so the whole episode is
     * buffered on disk before/during playback.
     *
     * @return list of segment cache keys that were prefetched.
     */
    suspend fun prefetchEpisode(
        context: Context,
        playlistUrl: String,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        val shard = cache(context)
        // 1. Follow master → media playlist.
        var mediaUrl = playlistUrl
        var text = fetchText(mediaUrl)
        while (HlsPlaylistParser.pickVariant(text) != null) {
            val variant = HlsPlaylistParser.pickVariant(text)!!
            mediaUrl = HlsPlaylistParser.resolveUrl(mediaUrl.substringBeforeLast('/'), variant)
            text = fetchText(mediaUrl)
        }
        val segments = HlsPlaylistParser.parseSegments(text, mediaUrl.substringBeforeLast('/'))
        if (segments.isEmpty()) return@withContext emptyList()

        val ua = PLAYER_UA
        val done = IntArray(1)
        for (url in segments) {
            if (!isActive) break
            try {
                // Use OkHttp (browser UA) instead of DefaultHttpDataSource,
                // so CDNs like svip.ryplay16.com don't block us.
                val req = Request.Builder().url(url)
                    .header("User-Agent", ua)
                    .header("Referer", refererFor(url))
                    .build()
                val resp = http.newCall(req).execute()
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val body = resp.body?.bytes() ?: throw IllegalStateException("empty body")

                // Write into the shared disk cache so the player can read it back.
                val sink = CacheDataSink(shard, Long.MAX_VALUE)
                val dataSpec = DataSpec(android.net.Uri.parse(url))
                sink.open(dataSpec)
                sink.write(body, 0, body.size)
                sink.close()
            } catch (e: Exception) {
                Log.w(TAG, "prefetch segment failed: ${e.message}")
            } finally {
                done[0]++
                onProgress(done[0], segments.size)
            }
        }
        Log.i(TAG, "prefetched ${done[0]}/${segments.size} segments for $playlistUrl")
        segments
    }

    /** Evict the given segment URLs from the disk cache (called when an episode ends). */
    fun releaseSegments(context: Context, segmentUrls: List<String>) {
        if (segmentUrls.isEmpty()) return
        val shard = cache(context)
        var released = 0
        for (url in segmentUrls) {
            try {
                shard.removeResource(url)
                released++
            } catch (e: Exception) {
                // ignored
            }
        }
        Log.i(TAG, "released $released/${segmentUrls.size} cached segments")
    }

    private fun fetchText(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", PLAYER_UA)
            .header("Referer", refererFor(url))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} for $url")
            return resp.body?.string() ?: ""
        }
    }

    /** Origin of [url] as Referer. Hardcoding 5o5k.com 404s some CDNs. */
    fun refererFor(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val scheme = uri.scheme ?: return "https://www.5o5k.com/"
            val host = uri.host ?: return "https://www.5o5k.com/"
            "$scheme://$host/"
        } catch (_: Exception) {
            "https://www.5o5k.com/"
        }
    }
}