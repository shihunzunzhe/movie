"""Live test: hit the real upstream sites directly (no DB, no cache).

Exercises the exact code paths in src/data_sources.py with force=True so every
request is made to the current live site, then verifies the extracted play URL.
"""
import asyncio
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

import httpx

from src.data_sources import YutuHtmlSource, MoguHtmlSource

UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"


async def verify_play_url(url: str) -> str:
    """Check that the extracted play URL is actually reachable and HLS-like."""
    if not url:
        return "NO URL"
    try:
        async with httpx.AsyncClient(timeout=15.0, follow_redirects=True) as client:
            resp = await client.get(url, headers={"User-Agent": UA})
            text = (resp.text or "").strip()
    except Exception as e:
        return f"FETCH FAIL: {e}"
    if resp.status_code != 200:
        return f"HTTP {resp.status_code}"
    if text.startswith("#EXTM3U"):
        infos = [ln for ln in text.splitlines() if "RESOLUTION=" in ln]
        return f"HLS OK ({resp.status_code}) resolutions={infos[:2] or 'single'}"
    return f"HTTP 200 but not m3u8 (len={len(text)})"


async def test_source(src, name: str, want_catalog: int = 5):
    print(f"\n{'=' * 60}\n[{name}] force catalog fetch (live)\n{'=' * 60}")
    movies = await src.fetch_movies(force=True)
    print(f"catalog: {len(movies)} movies from live site")
    if not movies:
        print("!! empty catalog")
        return

    for m in movies[:want_catalog]:
        print(f"  sample: id={m.id} | title={m.title[:24]!r} | cat={m.type!r} | poster={m.posterUrl[:44]}")

    # --- detail page (live) ---
    mid = movies[0].id
    print(f"\n[detail] {mid} (live fetch)")
    detail = await src.fetch_detail(mid)
    if not detail:
        print("!! detail fetch failed / returned None")
        return
    print(f"  title={detail.title[:24]!r} episodeTotal={detail.episodeTotal} "
          f"sources={list(detail.playUrls.keys()) if detail.playUrls else 'none'}")

    # --- play URL for episode 1 (live, one extra request) ---
    url = await src.get_play_url_for_episode(mid, 1)
    print(f"  playUrl[ep1] = {url}")
    print(f"  verify      -> {await verify_play_url(url)}")

    # Also test a non-first episode if the movie has several
    if detail and detail.episodeTotal and detail.episodeTotal > 1:
        url2 = await src.get_play_url_for_episode(mid, 2)
        print(f"  playUrl[ep2] = {url2}")
        print(f"  verify       -> {await verify_play_url(url2)}")


async def main():
    # 1) 蘑菇影视 (5o5k.com) — regular film/TV content
    await test_source(MoguHtmlSource("https://www.5o5k.com"), "蘑菇影视 MoguHtmlSource")
    # 2) 玉兔源 (yutuzy10.com)
    await test_source(YutuHtmlSource("https://yutuzy10.com"), "玉兔源 YutuHtmlSource")


if __name__ == "__main__":
    asyncio.run(main())