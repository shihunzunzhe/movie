"""HLS playlist rewriting for the playback proxy.

CDN short-drama streams are often a *master* playlist whose variant and
segment URIs are relative (e.g. ``2000k/hls/index.m3u8`` then ``foo.ts``).
If those relative paths are resolved against the master URL instead of the
media playlist URL, the CDN returns 404 and ExoPlayer surfaces "视频加载失败".

This module never flattens a master into a media playlist. It rewrites every
URI in the playlist against *that playlist's own URL*, then wraps the absolute
result in the appropriate proxy endpoint so ExoPlayer can follow the HLS
structure itself.
"""

from __future__ import annotations

import re
from urllib.parse import quote, urljoin, urlparse


_URI_ATTR_RE = re.compile(r'URI="([^"]+)"', re.IGNORECASE)


def is_playlist_uri(uri: str) -> bool:
    path = urlparse(uri).path.lower()
    return path.endswith(".m3u8") or path.endswith(".m3u")


def wrap_proxied_url(abs_url: str, proxy_hls: str, proxy_segment: str) -> str:
    endpoint = proxy_hls if is_playlist_uri(abs_url) else proxy_segment
    return f"{endpoint}?url={quote(abs_url, safe='')}"


def rewrite_hls_playlist(
    playlist: str,
    playlist_url: str,
    proxy_hls: str,
    proxy_segment: str,
) -> str:
    """Resolve every URI in [playlist] against [playlist_url] and proxy it.

    Handles:
      * media segment lines (``.ts``, ``.m4s``, …)
      * variant playlist lines after ``#EXT-X-STREAM-INF``
      * ``URI="..."`` attributes (``#EXT-X-KEY``, ``#EXT-X-MAP``,
        ``#EXT-X-MEDIA``, ``#EXT-X-I-FRAME-STREAM-INF``, …)
    """

    def wrap(abs_url: str) -> str:
        return wrap_proxied_url(abs_url, proxy_hls, proxy_segment)

    def abs_of(uri: str) -> str:
        return urljoin(playlist_url, uri.strip())

    out = []
    for line in playlist.splitlines():
        stripped = line.strip()
        if not stripped:
            out.append(line)
            continue
        if stripped.startswith("#"):
            if "URI=" in stripped.upper():
                stripped = _URI_ATTR_RE.sub(
                    lambda m: f'URI="{wrap(abs_of(m.group(1)))}"',
                    stripped,
                )
            out.append(stripped)
            continue
        out.append(wrap(abs_of(stripped)))

    result = "\n".join(out)
    if playlist.endswith("\n"):
        result += "\n"
    return result


def cdn_headers(url: str, user_agent: str) -> dict:
    """Browser-like headers. Referer is the URL origin, not a hardcoded site."""
    headers = {"User-Agent": user_agent}
    parsed = urlparse(url)
    if parsed.scheme and parsed.netloc:
        headers["Referer"] = f"{parsed.scheme}://{parsed.netloc}/"
    return headers
