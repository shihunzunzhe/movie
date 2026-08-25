package com.earthvideo.app.data.download

/**
 * Pure HLS playlist parsing helpers (unit-testable, no Android deps except URI strings).
 */
object HlsPlaylistParser {

    /**
     * Parse a media playlist text into its segment URIs (non-comment lines),
     * resolved against [baseUrl] (directory of the playlist).
     */
    fun parseSegments(playlistText: String, baseUrl: String): List<String> {
        val out = mutableListOf<String>()
        for (line in playlistText.split("\n")) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            out.add(resolveUrl(baseUrl, t))
        }
        return out
    }

    /**
     * If [text] is a master playlist (#EXT-X-STREAM-INF), return the URI of the
     * highest-bandwidth variant; otherwise return null.
     */
    fun pickVariant(playlistText: String): String? {
        if (!playlistText.contains("#EXT-X-STREAM-INF")) return null
        var best: String? = null
        var bestBw = -1L
        val lines = playlistText.split("\n")
        var i = 0
        while (i < lines.size) {
            val t = lines[i].trim()
            if (t.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(t)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val uri = lines.getOrNull(i + 1)?.trim()
                if (!uri.isNullOrEmpty() && !uri.startsWith("#") && bw >= bestBw) {
                    bestBw = bw
                    best = uri
                }
            }
            i++
        }
        return best
    }

    fun hasExtXKey(playlistText: String): Boolean = playlistText.contains("#EXT-X-KEY")

    fun resolveUrl(baseUrl: String, uri: String): String {
        val u = uri.trim()
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        if (u.startsWith("//")) return "https:" + u
        return baseUrl.trimEnd('/') + "/" + u.removePrefix("/")
    }

    /**
     * Rewrite a downloaded playlist: segments become local filenames, the AES key
     * reference is rewritten to a local file. Returns the new playlist text.
     */
    fun rewriteLocalPlaylist(
        original: String,
        keyFileName: String?,
        segmentFileNameFor: (Int) -> String
    ): String {
        val out = mutableListOf<String>()
        var segIndex = 0
        val keyUriRegex = Regex("URI=\"([^\"]+)\"")
        for (line in original.split("\n")) {
            val t = line.trim()
            when {
                t.startsWith("#EXT-X-KEY") -> {
                    val m = keyUriRegex.find(t)
                    if (m != null && keyFileName != null) {
                        out.add(t.replace(m.groupValues[1], keyFileName))
                    } else {
                        out.add(line)
                    }
                }
                t.startsWith("#") -> out.add(line)
                t.isEmpty() -> out.add(line)
                else -> {
                    out.add(segmentFileNameFor(segIndex))
                    segIndex++
                }
            }
        }
        return out.joinToString("\n").trimEnd('\n') + "\n"
    }
}