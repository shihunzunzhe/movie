package com.earthvideo.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistParserTest {

    private val mediaPlaylist = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:8
        #EXT-X-MEDIA-SEQUENCE:0
        #EXT-X-KEY:METHOD=AES-128,URI="enc.key",IV=0x1234
        #EXTINF:6.27,
        seg1.ts?hash=abc
        #EXTINF:3.33,
        https://cdn.example.com/v/seg2.ts
        #EXTINF:3.33,
        //cdn2.example.com/v/seg3.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test
    fun parseSegments_resolvesRelativeAbsoluteAndProtocolRelative() {
        val segs = HlsPlaylistParser.parseSegments(mediaPlaylist, "https://cdn.example.com/v")
        assertEquals(3, segs.size)
        assertEquals("https://cdn.example.com/v/seg1.ts?hash=abc", segs[0])
        assertEquals("https://cdn.example.com/v/seg2.ts", segs[1])
        assertEquals("https://cdn2.example.com/v/seg3.ts", segs[2])
    }

    @Test
    fun parseSegments_emptyPlaylist_returnsEmpty() {
        assertTrue(HlsPlaylistParser.parseSegments("#EXTM3U\n#EXT-X-VERSION:3\n", "https://a/b").isEmpty())
    }

    @Test
    fun pickVariant_mediaPlaylist_returnsNull() {
        assertNull(HlsPlaylistParser.pickVariant(mediaPlaylist))
    }

    @Test
    fun pickVariant_masterPlaylist_picksHighestBandwidth() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720
            https://cdn.example.com/v/720p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1920x1080
            https://cdn.example.com/v/1080p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360
            https://cdn.example.com/v/360p/index.m3u8
        """.trimIndent()
        assertEquals("https://cdn.example.com/v/1080p/index.m3u8", HlsPlaylistParser.pickVariant(master))
    }

    @Test
    fun rewriteLocalPlaylist_rewritesSegmentsAndKey() {
        val rewritten = HlsPlaylistParser.rewriteLocalPlaylist(
            mediaPlaylist,
            keyFileName = "enc.key",
            segmentFileNameFor = { i -> "seg_%04d.ts".format(i) }
        )
        assertTrue(rewritten.contains("URI=\"enc.key\""))
        assertTrue(rewritten.contains("seg_0000.ts"))
        assertTrue(rewritten.contains("seg_0001.ts"))
        assertTrue(rewritten.contains("seg_0002.ts"))
        assertFalse(rewritten.contains("seg1.ts?hash=abc"))
        // header/EXTINF tags are preserved
        assertTrue(rewritten.contains("#EXT-X-KEY:METHOD=AES-128"))
        assertTrue(rewritten.contains("#EXTINF:6.27,"))
        assertTrue(rewritten.endsWith("\n"))
    }

    @Test
    fun rewriteLocalPlaylist_noKey_keepsTags() {
        val simple = "#EXTM3U\n#EXTINF:5.0,\nseg.ts\n#EXT-X-ENDLIST\n"
        val rewritten = HlsPlaylistParser.rewriteLocalPlaylist(simple, null) { i -> "s$i.ts" }
        assertEquals("#EXTM3U\n#EXTINF:5.0,\ns0.ts\n#EXT-X-ENDLIST\n", rewritten)
    }

    @Test
    fun resolveUrl_handlesAllForms() {
        assertEquals("https://x/v/b.ts", HlsPlaylistParser.resolveUrl("https://x/v", "b.ts"))
        assertEquals("https://x/v/c.ts", HlsPlaylistParser.resolveUrl("https://x/v", "/c.ts"))
        assertEquals("https://a/b.ts", HlsPlaylistParser.resolveUrl("https://x/v", "https://a/b.ts"))
        assertEquals("https://a/b.ts", HlsPlaylistParser.resolveUrl("https://x/v", "//a/b.ts"))
    }

    @Test
    fun pickVariant_nestedRelativeMaster_keepsRelativePath() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=800000,RESOLUTION=1080x608
            2000k/hls/index.m3u8
        """.trimIndent()
        assertEquals("2000k/hls/index.m3u8", HlsPlaylistParser.pickVariant(master))
    }

    @Test
    fun resolveUrl_nestedMediaPlaylist_doesNotDropSubdir() {
        val master = "https://svip.ryplay16.com/20260822/662735_f185c4b9/index.m3u8"
        val media = HlsPlaylistParser.resolveUrl(master.substringBeforeLast('/'), "2000k/hls/index.m3u8")
        assertEquals(
            "https://svip.ryplay16.com/20260822/662735_f185c4b9/2000k/hls/index.m3u8",
            media
        )
        val seg = HlsPlaylistParser.resolveUrl(media.substringBeforeLast('/'), "3d5a246b314000000.ts")
        assertEquals(
            "https://svip.ryplay16.com/20260822/662735_f185c4b9/2000k/hls/3d5a246b314000000.ts",
            seg
        )
    }
}