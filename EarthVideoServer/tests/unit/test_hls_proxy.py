"""Unit tests for HLS playlist rewriting.

Regression: nested short-drama playlists (master → 2000k/hls/index.m3u8 →
relative .ts) must not resolve segments against the master directory, which
is a 404 on CDNs such as svip.ryplay16.com.
"""

from src.hls_proxy import cdn_headers, is_playlist_uri, rewrite_hls_playlist, wrap_proxied_url


PROXY_HLS = "http://192.168.1.36:8808/api/proxy/hls"
PROXY_SEG = "http://192.168.1.36:8808/api/proxy/segment"

MASTER_URL = "https://svip.ryplay16.com/20260822/662735_f185c4b9/index.m3u8"
MEDIA_URL = "https://svip.ryplay16.com/20260822/662735_f185c4b9/2000k/hls/index.m3u8"

MASTER = """#EXTM3U
#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=800000,RESOLUTION=1080x608
2000k/hls/index.m3u8
"""

MEDIA = """#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:8
#EXT-X-MEDIA-SEQUENCE:0
#EXTINF:4.733333,
3d5a246b314000000.ts
#EXTINF:6.233333,
3d5a246b314000001.ts
#EXT-X-ENDLIST
"""


def test_is_playlist_uri():
    assert is_playlist_uri("https://cdn.example/a/index.m3u8")
    assert is_playlist_uri("https://cdn.example/a/index.m3u")
    assert not is_playlist_uri("https://cdn.example/a/seg.ts")
    assert not is_playlist_uri("https://cdn.example/a/init.mp4")


def test_master_variant_is_rewritten_to_hls_proxy_not_segment_proxy():
    rewritten = rewrite_hls_playlist(MASTER, MASTER_URL, PROXY_HLS, PROXY_SEG)
    assert "2000k/hls/index.m3u8" not in rewritten.split("\n")[2]
    assert f"{PROXY_HLS}?url=" in rewritten
    assert MEDIA_URL.replace(":", "%3A").replace("/", "%2F") in rewritten
    # Must NOT wrap the nested playlist as a media segment.
    assert f"{PROXY_SEG}?url=" not in rewritten


def test_media_segments_resolve_against_media_playlist_not_master():
    rewritten = rewrite_hls_playlist(MEDIA, MEDIA_URL, PROXY_HLS, PROXY_SEG)
    # Absolute CDN path must include the nested 2000k/hls directory.
    assert "2000k%2Fhls%2F3d5a246b314000000.ts" in rewritten
    assert "2000k%2Fhls%2F3d5a246b314000001.ts" in rewritten
    # Resolving against the master directory would produce this 404 path:
    assert "662735_f185c4b9%2F3d5a246b314000000.ts" not in rewritten
    assert f"{PROXY_SEG}?url=" in rewritten
    assert f"{PROXY_HLS}?url=" not in rewritten


def test_wrong_base_is_exactly_the_404_path():
    """If we accidentally rewrite media segments against the master URL, the
    CDN path loses ``2000k/hls/`` and 404s. This test documents that failure."""
    wrong = rewrite_hls_playlist(MEDIA, MASTER_URL, PROXY_HLS, PROXY_SEG)
    assert "662735_f185c4b9%2F3d5a246b314000000.ts" in wrong
    assert "2000k%2Fhls%2F3d5a246b314000000.ts" not in wrong


def test_ext_x_key_and_map_are_rewritten():
    playlist = """#EXTM3U
#EXT-X-KEY:METHOD=AES-128,URI="enc.key",IV=0x1
#EXT-X-MAP:URI="init.mp4"
#EXTINF:1.0,
seg.ts
"""
    rewritten = rewrite_hls_playlist(
        playlist,
        "https://cdn.example.com/v/index.m3u8",
        PROXY_HLS,
        PROXY_SEG,
    )
    assert "v%2Fenc.key" in rewritten
    assert "v%2Finit.mp4" in rewritten
    assert "v%2Fseg.ts" in rewritten
    assert rewritten.count(PROXY_SEG + "?url=") == 3


def test_absolute_segment_is_proxied_without_re_resolving():
    playlist = """#EXTM3U
#EXTINF:1.0,
https://other.cdn/a.ts
"""
    rewritten = rewrite_hls_playlist(
        playlist, MASTER_URL, PROXY_HLS, PROXY_SEG
    )
    assert "other.cdn%2Fa.ts" in rewritten
    assert rewritten.strip().endswith("a.ts") or "a.ts" in rewritten


def test_cdn_headers_use_url_origin_not_hardcoded_site():
    h = cdn_headers(
        "https://svip.ryplay16.com/20260822/x/index.m3u8",
        "Mozilla/5.0",
    )
    assert h["User-Agent"] == "Mozilla/5.0"
    assert h["Referer"] == "https://svip.ryplay16.com/"


def test_wrap_proxied_url_encodes_query_safely():
    url = wrap_proxied_url(
        "https://cdn.example/a/index.m3u8?token=1/2",
        PROXY_HLS,
        PROXY_SEG,
    )
    assert url.startswith(PROXY_HLS + "?url=")
    assert "token%3D1%2F2" in url
