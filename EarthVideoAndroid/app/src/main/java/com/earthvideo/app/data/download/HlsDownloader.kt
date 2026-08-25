package com.earthvideo.app.data.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class DownloadResult(
    val segments: Int,
    val bytes: Long
)

/**
 * Downloads an HLS (m3u8) stream to a local directory and rewrites the
 * playlist so every segment / key references local files. The resulting
 * index.m3u8 can be played offline by ExoPlayer.
 *
 * [onProgress] is invoked per segment; returning false cancels the download.
 */
class HlsDownloader(private val client: OkHttpClient) {

    suspend fun download(
        m3u8Url: String,
        destDir: File,
        onProgress: (done: Int, total: Int) -> Boolean = { _, _ -> true }
    ): DownloadResult = withContext(Dispatchers.IO) {
        destDir.mkdirs()

        // Resolve master playlist → concrete media playlist.
        val mediaUrl = resolveMediaPlaylist(m3u8Url)
        val base = mediaUrl.substringBeforeLast('/')
        val playlist = fetchText(mediaUrl)

        val lines = playlist.split("\n")
        val totalSegments = lines.count { it.isNotBlank() && !it.startsWith("#") }

        val newLines = mutableListOf<String>()
        var done = 0
        var totalBytes = 0L
        var cancelled = false
        val keyUriRegex = Regex("URI=\"([^\"]+)\"")

        for (line in lines) {
            val t = line.trim()
            when {
                // AES-128 key: download it and rewrite the reference to a local file.
                t.startsWith("#EXT-X-KEY") -> {
                    val m = keyUriRegex.find(t)
                    if (m != null) {
                        val keyFile = File(destDir, "enc.key")
                        runCatching { downloadFile(resolveUrl(base, m.groupValues[1]), keyFile) }
                        newLines.add(t.replace(m.groupValues[1], "enc.key"))
                    } else {
                        newLines.add(line)
                    }
                }
                t.startsWith("#") -> newLines.add(line)
                t.isEmpty() -> newLines.add(line)
                else -> {
                    val segUrl = resolveUrl(base, t)
                    val segName = "seg_%04d.ts".format(done)
                    val segFile = File(destDir, segName)
                    val bytes = downloadFile(segUrl, segFile)
                    totalBytes += bytes
                    newLines.add(segName)
                    done++
                    if (!onProgress(done, totalSegments)) {
                        cancelled = true
                        break
                    }
                }
            }
        }

        if (cancelled && !destDir.name.endsWith("_ep0")) {
            // leave partial directory; DownloadManager decides whether to keep it
        }
        File(destDir, "index.m3u8").writeText(newLines.joinToString("\n") + "\n")
        DownloadResult(done, totalBytes)
    }

    /** Follow a master playlist to its highest-bandwidth variant (or the only one). */
    private fun fetchMediaPlaylist(url: String): String {
        val text = fetchText(url)
        val variant = HlsPlaylistParser.pickVariant(text) ?: return url
        return fetchMediaPlaylist(
            HlsPlaylistParser.resolveUrl(url.substringBeforeLast('/'), variant)
        )
    }

    private fun resolveMediaPlaylist(url: String): String = fetchMediaPlaylist(url)

    private fun resolveUrl(base: String, uri: String): String =
        HlsPlaylistParser.resolveUrl(base, uri)

    private fun fetchText(url: String): String {
        val req = Request.Builder().url(url).header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
        ).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} for $url")
            return resp.body?.string() ?: ""
        }
    }

    private fun downloadFile(url: String, target: File): Long {
        val req = Request.Builder().url(url).header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
        ).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} for $url")
            val body = resp.body ?: return 0L
            target.outputStream().use { out ->
                body.byteStream().copyTo(out)
            }
            return body.contentLength().takeIf { it > 0 } ?: target.length()
        }
    }
}