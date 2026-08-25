"""
EarthVideo API Server
Scrapes yutuzy10.com for real movie data via standalone scraper.
FastAPI server loads from MySQL on startup, serves data from in-memory cache.
Standalone scraper (src/scraper.py) handles 2-hour crawling cycle independently.
No mock data. Missing fields left empty.
Binds to 0.0.0.0:8808 for LAN access.
"""

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import contextlib
import json
import re as re_mod
import asyncio
import time
import logging
import subprocess
import sys
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from fastapi import Request
from pydantic import BaseModel

import httpx
import uvicorn

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger("earthvideo")

from src.models import (
    ApiResponse,
    Movie,
    Episode,
    SearchHistory,
    HotSearchItem,
    RankItem,
    UserProfile,
    PageData,
)
from src.data_sources import DataSourceManager
from src.mysql_store import MySQLStore
from src.config import config
from src.hls_proxy import cdn_headers, rewrite_hls_playlist

data_manager = DataSourceManager(proxy_url=config.proxy_url)
data_manager.add_source("yutu", "https://yutuzy10.com", enabled=True)
data_manager.add_source("mogu", "https://www.5o5k.com", enabled=True)
mysql = MySQLStore()

# In-memory user store
user_history: List[str] = []
user_favorites: List[str] = []

# Scrape tracking
_last_scrape_time: float = 0


class ToggleFavoriteRequest(BaseModel):
    movie_id: str


class AddHistoryRequest(BaseModel):
    movie_id: str


# ---------------------------------------------------------------------------
# Background tasks & lifespan
# ---------------------------------------------------------------------------
async def _load_from_mysql():
    """Load movies from MySQL into in-memory cache on startup."""
    try:
        movies, total = await mysql.search_movies(size=100000)
        if movies:
            for m in movies:
                src = data_manager.find_source_for_movie(m.id)
                if src is not None:
                    src._movies[m.id] = m
            logger.info("Loaded %s movies from MySQL into cache", len(movies))
    except Exception as e:
        logger.warning("Failed to load from MySQL: %s", e)


async def scrape_scheduler():
    """Periodically run the scraper in an isolated subprocess.

    Each scrape cycle spawns a fresh Python process that scrapes both sources
    and writes results to MySQL, then exits.  The main event loop, its thread
    pool, and the main MySQL pool are never touched — zero risk of blocking.
    """
    server_dir = os.path.dirname(os.path.abspath(__file__))
    scraper_script = os.path.join(server_dir, "src", "subprocess_scraper.py")

    # Initial delay: let the server finish startup
    await asyncio.sleep(30)
    if not config.collection_enabled:
        return

    def _spawn():
        logger.info("Starting scrape subprocess…")
        return subprocess.Popen(
            [sys.executable, scraper_script],
            cwd=server_dir,
            stdout=None,
            stderr=None,
        )

    # First scrape
    proc = _spawn()
    while True:
        await asyncio.sleep(config.scrape_interval)
        if not config.collection_enabled:
            continue
        # Check if previous scrape is still running; if not, spawn a new one.
        if proc.poll() is not None:
            logger.info("Scheduled scrape (interval=%ss)…", config.scrape_interval)
            proc = _spawn()
        else:
            logger.info("Previous scrape still running, skipping this cycle")


@contextlib.asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("EarthVideo API starting up...")

    try:
        await mysql.connect()
        logger.info("MySQL connected")
    except Exception as e:
        logger.warning("MySQL connection failed (in-memory fallback): %s", e)

    # Load from MySQL into cache, then start the scraper only.
    await _load_from_mysql()

    if config.collection_enabled:
        # Fire-and-forget: the scraper runs in a dedicated thread (not the main
        # event loop) so it never blocks API responses. The thread creates its
        # own MySQL connection pool and manages its own event loop.
        asyncio.create_task(scrape_scheduler())
        logger.info(
            "Collection enabled: scrape_interval=%ss",
            config.scrape_interval,
        )
    else:
        logger.info("Collection disabled: serving existing data only, no background tasks")

    yield
    await mysql.close()


# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------
app = FastAPI(title="EarthVideo API", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def page_resp(items: list, page: int, size: int) -> PageData:
    total = len(items)
    start = (page - 1) * size
    end = start + size
    return PageData(
        list=items[start:end],
        page=page,
        size=size,
        total=total,
        hasMore=end < total,
    )

def paginated_resp(items: list, page: int, size: int, total: int) -> PageData:
    return PageData(
        list=items,
        page=page,
        size=size,
        total=total,
        hasMore=(page * size) < total,
    )


def category_aliases(cat: str) -> list:
    m = {
        "recommend": [],
        "new": ["电视剧", "综艺", "动漫"],
        "oversea": ["电影", "电视剧"],
        "tv": ["电视剧"],
        "movie": ["电影"],
        "variety": ["综艺"],
        "anime": ["动漫"],
        "drama": ["短剧"],
    }
    return m.get(cat, [])


def to_dict(obj):
    return (
        obj.model_dump()
        if hasattr(obj, "model_dump")
        else obj.dict() if hasattr(obj, "dict") else obj
    )


async def _get_detail(movie_id: str):
    """Get movie detail from MySQL, with fallback to in-memory or on-demand fetch."""
    m = await mysql.get_movie(movie_id) if mysql._pool else None
    if m and m.posterUrl and not m.posterUrl.endswith('huo.gif'):
        return m
    # Fallback to in-memory
    m = data_manager.get_movie_by_id(movie_id)
    if m and m.posterUrl and not m.posterUrl.endswith('huo.gif'):
        return m
    # On-demand fetch detail page to get real poster/playUrls
    src = data_manager.find_source_for_movie(movie_id)
    if src is not None:
        try:
            detail = await src.fetch_detail(movie_id)
            if detail:
                if mysql._pool:
                    await mysql.upsert_movie(detail)
                return detail
        except Exception:
            pass
    return m


async def _collect_sources_for_movie(movie_id: str) -> list:
    """Collect all available source names for a movie.
    Returns list of source name strings, with higher-priority sources first."""
    seen = set()
    sources = []
    src = data_manager.find_source_for_movie(movie_id)
    m = data_manager.get_movie_by_id(movie_id)
    # Sources from playUrls (sources that have been scraped successfully)
    if m and m.playUrls:
        for sname in m.playUrls:
            if sname not in seen and sname != "default":
                sources.append(sname)
                seen.add(sname)
    # Sources from _movie_sources metadata (all sources on the website)
    if src and hasattr(src, "_movie_sources"):
        for _sid, sname, _count in (src._movie_sources.get(movie_id) or []):
            if sname not in seen:
                sources.append(sname)
                seen.add(sname)
    if not sources:
        sources = ["default"]
    return sources


async def _get_play_url(
    movie_id: str, episode: int, source: str = "default",
    sources: list = None,
) -> tuple:
    """Get play URL with automatic source fallback.

    Returns (url, actual_source) tuple — actual_source tells the caller
    which source produced the URL, so it can be passed back to the client.

    When the requested source fails, other available sources are tried
    automatically.  Pass ``sources`` to control the fallback order, or
    leave it as None to discover sources from the in-memory cache.
    """
    # 1. Try MySQL cache for the exact (movie_id, episode, source).
    if mysql._pool:
        url = await mysql.get_play_url(movie_id, episode, source)
        if url:
            return url, source

    src = data_manager.find_source_for_movie(movie_id)

    # 2. Check in-memory playUrls.
    m = data_manager.get_movie_by_id(movie_id) if src else None
    if m and m.playUrls:
        play_urls = m.playUrls or {}
        if source == "default":
            for sname, eps in play_urls.items():
                if episode in eps:
                    return eps[episode], sname
        elif source in play_urls and episode in play_urls[source]:
            return play_urls[source][episode], source

    if src is None:
        return "", source

    # 3. Build the list of sources to try.
    if sources is None:
        sources = await _collect_sources_for_movie(movie_id)

    to_try = [source]
    for s in sources:
        if s not in to_try:
            to_try.append(s)

    # 4. Try each source in turn; stop on the first that yields a URL.
    for s in to_try:
        url = await src.get_play_url_for_episode(movie_id, episode, s)
        if url:
            return url, s

    return "", source


# ---------------------------------------------------------------------------
# HLS proxy — rewrite URIs against THIS playlist's URL, then wrap them.
# Nested short-drama streams (master → 2000k/hls/index.m3u8 → relative .ts)
# must NOT be flattened: flattening resolves .ts against the master directory
# and the CDN 404s. ExoPlayer follows the rewritten master itself.
# ---------------------------------------------------------------------------
_HLS_UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Safari/537.36"
)
_HLS_PROXY_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Cache-Control": "no-cache",
}


@app.get("/api/proxy/hls")
async def proxy_hls(request: Request, url: str = ""):
    if not url:
        return Response(content="#EXTM3U\n", media_type="application/vnd.apple.mpegurl")

    try:
        async with httpx.AsyncClient(timeout=15.0, follow_redirects=True) as client:
            resp = await client.get(url, headers=cdn_headers(url, _HLS_UA))
            resp.raise_for_status()
            playlist = resp.text
            final_url = str(resp.url)
    except Exception as e:
        logger.warning("HLS proxy: fetch failed for %s: %s", url[:80], e)
        return Response(
            content="#EXTM3U\n#EXT-X-ERROR:SOURCE_UNAVAILABLE\n",
            media_type="application/vnd.apple.mpegurl",
            headers=_HLS_PROXY_HEADERS,
        )

    if not playlist or not playlist.lstrip().startswith("#EXTM3U"):
        logger.warning("HLS proxy: source returned no playlist for %s", url[:80])
        return Response(
            content="#EXTM3U\n#EXT-X-ERROR:SOURCE_UNAVAILABLE\n",
            media_type="application/vnd.apple.mpegurl",
            headers=_HLS_PROXY_HEADERS,
        )

    origin = str(request.base_url).rstrip("/")
    rewritten = rewrite_hls_playlist(
        playlist,
        final_url,
        proxy_hls=origin + "/api/proxy/hls",
        proxy_segment=origin + "/api/proxy/segment",
    )
    return Response(
        content=rewritten,
        media_type="application/vnd.apple.mpegurl",
        headers=_HLS_PROXY_HEADERS,
    )


@app.get("/api/proxy/segment")
async def proxy_segment(url: str = ""):
    """Proxy a video segment (TS / fMP4 / AES key) through the server."""
    if not url:
        return Response(status_code=400)
    try:
        async with httpx.AsyncClient(timeout=30.0, follow_redirects=True) as client:
            resp = await client.get(url, headers=cdn_headers(url, _HLS_UA))
            resp.raise_for_status()
            return Response(
                content=resp.content,
                media_type=resp.headers.get("content-type", "application/octet-stream"),
                headers={
                    "Access-Control-Allow-Origin": "*",
                    "Cache-Control": "public, max-age=3600",
                },
            )
    except Exception as e:
        logger.warning("Segment proxy failed for %s: %s", url[:120], e)
        return Response(status_code=502)


# ---------------------------------------------------------------------------
# Helper: search movies from MySQL with fallback to in-memory
# ---------------------------------------------------------------------------
async def _search_movies(
    keyword: str = "",
    type_filter: str = "all",
    region: str = "all",
    year: str = "all",
    sort: str = "最热",
    page: int = 1,
    size: int = 20,
):
    try:
        if mysql._pool:
            movies, total = await mysql.search_movies(keyword, type_filter, region, year, sort, page, size)
            if movies:
                return movies, total
    except Exception:
        pass
    all_items = data_manager.search_movies(keyword, type_filter, region, year, sort)
    total = len(all_items)
    start = (page - 1) * size
    end = start + size
    return all_items[start:end], total


async def _get_all_movies():
    try:
        if mysql._pool:
            movies, total = await mysql.search_movies(size=100000)
            if movies:
                return movies
    except Exception:
        pass
    return data_manager.search_movies()


# ---------------------------------------------------------------------------
# API endpoints
# ---------------------------------------------------------------------------
@app.get("/api/home/recommend")
async def home_recommend(category: str = "recommend", page: int = 1, size: int = 20):
    all_items = await _get_all_movies()
    # Newest first across every category — driven by the media publish date
    # extracted from the source URL (e.g. /20260821/...) or DB updated time.
    all_items = sorted(
        all_items,
        key=lambda m: (m.publishDate or "", m.episodeUpdated),
        reverse=True,
    )
    if category != "recommend":
        allowed = category_aliases(category)
        all_items = (
            [m for m in all_items if m.type in allowed]
            if allowed
            else all_items
        )
    return ApiResponse(data=page_resp([to_dict(m) for m in all_items], page, size))


@app.get("/api/search/history")
def search_history():
    return ApiResponse(
        data=SearchHistory(
            keywords=[
                "南部档案", "老九门2", "老九门", "九门", "余罪",
                "凡人修仙传全集", "理想之城", "寒战1994", "寒战", "剑来",
            ]
        )
    )


@app.post("/api/search/history/clear")
def search_history_clear():
    return ApiResponse(data=SearchHistory(keywords=[]))


@app.get("/api/search/hot")
def search_hot():
    items = [
        HotSearchItem(keyword="仙逆", tag="热", description="我辈修士,何惜一战"),
        HotSearchItem(keyword="欢迎来龙餐馆", tag="荐", description="徐峥沈腾联手"),
        HotSearchItem(keyword="庆余年第三季", tag="热", description="范闲归来"),
        HotSearchItem(keyword="重器", tag="荐", description="年代法治大剧"),
        HotSearchItem(keyword="天才女友", tag="热", description="田曦薇胡一天"),
        HotSearchItem(keyword="狂飙", tag="热", description="评分9.6"),
        HotSearchItem(keyword="花开锦绣", tag="热", description="赵露思新剧"),
        HotSearchItem(keyword="异人之下2", tag="荐", description="彭昱畅回归"),
        HotSearchItem(keyword="凡人修仙传", tag="热", description="国漫之光"),
        HotSearchItem(keyword="鱿鱼游戏2", tag="热", description="等你挑战"),
    ]
    return ApiResponse(data={"list": [i.model_dump() for i in items]})


@app.get("/api/search/suggest")
async def search_suggest(keyword: str = ""):
    results, _ = await _search_movies(keyword=keyword, size=9999)
    suggestions = list(set(m.title for m in results))[:5]
    return ApiResponse(data={"suggestions": suggestions})


@app.get("/api/search")
async def search(keyword: str = "", type: str = "all", page: int = 1, size: int = 20):
    # Results are always newest-first by publish date.
    results, total = await _search_movies(keyword=keyword, type_filter=type, sort="最新", size=9999)
    return ApiResponse(data=page_resp([to_dict(m) for m in results], page, size))


@app.get("/api/category/list")
async def category_list(
    type: str = "all",
    genre: str = "all",
    region: str = "all",
    year: str = "all",
    sort: str = "最热",
    page: int = 1,
    size: int = 20,
):
    results, total = await _search_movies(
        type_filter=type, region=region, year=year, sort=sort, size=9999
    )
    items = [
        {
            "id": m.id,
            "title": m.title,
            "posterUrl": m.posterUrl,
            "episodeTag": m.episodeTag,
            "hotTag": m.hotTag,
        }
        for m in results
    ]
    return ApiResponse(data=page_resp(items, page, size))


@app.get("/api/rank/list")
async def rank_list(type: str = "hot", page: int = 1, size: int = 20):
    all_items = await _get_all_movies()
    # All rank tabs show newest media first (publish date), filtered by type.
    ranked = sorted(
        all_items,
        key=lambda m: (m.publishDate or "", m.episodeUpdated),
        reverse=True,
    )
    if type == "tv":
        ranked = [m for m in ranked if "电视剧" in m.type or "剧" in m.type]
    elif type == "movie":
        ranked = [m for m in ranked if "电影" in m.type]
    items = [RankItem(rank=i + 1, movieId=m.id, movie=m) for i, m in enumerate(ranked)]
    return ApiResponse(data=page_resp([to_dict(r) for r in items], page, size))


@app.get("/api/movie/list")
async def movie_list(page: int = 1, pageSize: int = 20):
    try:
        if mysql._pool:
            movies, total = await mysql.search_movies(page=page, size=pageSize)
            if movies:
                return ApiResponse(data=paginated_resp([to_dict(m) for m in movies], page, pageSize, total))
    except Exception:
        pass
    items = data_manager.search_movies()
    total = len(items)
    start = (page - 1) * pageSize
    end = start + pageSize
    return ApiResponse(data=paginated_resp([to_dict(m) for m in items[start:end]], page, pageSize, total))


@app.get("/api/movie/detail")
async def movie_detail(id: str = ""):
    m = await mysql.get_movie(id) if mysql._pool else None
    if not m:
        m = data_manager.get_movie_by_id(id)
    if not m:
        return ApiResponse(code=404, message="not found", data={})
    enriched = await _get_detail(id)
    movie = enriched if enriched else m
    d = to_dict(movie)
    d["introduction"] = movie.introduction or ""
    d["playUrls"] = movie.playUrls
    return ApiResponse(data=d)


@app.get("/api/movie/episodes")
async def movie_episodes(id: str = ""):
    m = await mysql.get_movie(id) if mysql._pool else None
    if not m:
        m = data_manager.get_movie_by_id(id)
    if not m:
        return ApiResponse(code=404, message="not found", data={})

    total = m.episodeTotal if m.episodeTotal > 0 else 1

    # Always generate episodes from 1..total, regardless of playUrls content.
    # The scrape may only preload a few play URLs, but the user should see
    # the full episode list (on-demand play URL resolution fills the gaps).
    episodes = []
    for i in range(1, min(total + 1, 150)):
        episodes.append(
            Episode(
                episodeNumber=i,
                title=f"第{i}集",
                duration=0,
                current=(i == 1),
            )
        )

    return ApiResponse(
        data={
            "movieId": m.id,
            "total": total,
            "updated": total,
            "episodes": [to_dict(e) for e in episodes],
        }
    )


@app.get("/api/movie/playUrl")
async def movie_play_url(id: str = "", episode: int = 1, source: str = "default", force: int = 0):
    m = await mysql.get_movie(id) if mysql._pool else None
    if not m:
        m = data_manager.get_movie_by_id(id)
    if not m:
        return ApiResponse(code=404, message="not found", data={})

    # When force=1, evict the cached play URL so the next fetch goes
    # straight to the source website (5o5k.com) for a fresh link.
    if force == 1 and m and m.playUrls:
        for sname in list(m.playUrls.keys()):
            if episode in m.playUrls[sname]:
                if source == "default" or sname == source:
                    del m.playUrls[sname][episode]
                    if not m.playUrls[sname]:
                        del m.playUrls[sname]
                    break
        # Also evict from MySQL so the stale URL isn't returned.
        if mysql._pool:
            await mysql.delete_play_url(id, episode, source)
        logger.info("Forced refresh for %s ep=%s source=%s", id, episode, source)

    # Collect all available sources before resolving, so _get_play_url
    # can fall back to them when the requested source fails.
    all_sources = await _collect_sources_for_movie(id)

    video_url, actual_source = await _get_play_url(id, episode, source, all_sources)

    if video_url and m and mysql._pool:
        # Persist the resolved URL to MySQL for future calls.
        if not m.playUrls:
            m.playUrls = {}
        bucket = m.playUrls.setdefault(actual_source, {})
        bucket[episode] = video_url
        await mysql.upsert_movie(m)

    if not video_url:
        # Last resort: test videos
        test_videos = [
            "https://media.w3.org/2010/05/sintel/trailer.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        ]
        h = 0
        for c in id:
            h = (h * 31 + ord(c)) & 0xFFFFFFFF
        video_url = test_videos[h % len(test_videos)]
        actual_source = "fallback"

    sources = [
        {"sourceId": s, "sourceName": s, "priority": i + 1}
        for i, s in enumerate(all_sources)
    ]
    if not sources:
        sources = [
            {"sourceId": "default", "sourceName": "默认播放", "priority": 1},
        ]

    return ApiResponse(
        data={
            "movieId": m.id,
            "episode": episode,
            "url": video_url,
            "sources": sources,
            "actualSource": actual_source,
        }
    )


@app.get("/api/user/profile")
def user_profile():
    return ApiResponse(
        data=UserProfile(
            historyCount=len(user_history),
            favoriteCount=len(user_favorites),
            downloadCount=0,
        )
    )


@app.get("/api/user/history")
def get_history(page: int = 1, size: int = 20):
    movies = []
    for mid in reversed(user_history):
        m = data_manager.get_movie_by_id(mid)
        if m:
            movies.append(m)
    return ApiResponse(data=page_resp([to_dict(m) for m in movies], page, size))


@app.post("/api/user/history/add")
def add_history(req: AddHistoryRequest):
    if req.movie_id in user_history:
        user_history.remove(req.movie_id)
    user_history.append(req.movie_id)
    if len(user_history) > 200:
        user_history.pop(0)
    return ApiResponse(data={"count": len(user_history)})


@app.post("/api/user/history/clear")
def clear_history():
    user_history.clear()
    return ApiResponse(data={"count": 0})


@app.get("/api/user/favorites")
def get_favorites(page: int = 1, size: int = 20):
    movies = []
    for mid in reversed(user_favorites):
        m = data_manager.get_movie_by_id(mid)
        if m:
            movies.append(m)
    return ApiResponse(data=page_resp([to_dict(m) for m in movies], page, size))


@app.post("/api/user/favorites/toggle")
def toggle_favorite(req: ToggleFavoriteRequest):
    if req.movie_id in user_favorites:
        user_favorites.remove(req.movie_id)
        is_favorite = False
    else:
        user_favorites.append(req.movie_id)
        is_favorite = True
    return ApiResponse(data={"isFavorite": is_favorite, "count": len(user_favorites)})


@app.get("/api/user/favorites/status")
def favorites_status(movie_id: str = ""):
    return ApiResponse(data={"isFavorite": movie_id in user_favorites})


# ---------------------------------------------------------------------------
# Config endpoints
# ---------------------------------------------------------------------------
class UpdateConfigRequest(BaseModel):
    collection_enabled: Optional[bool] = None
    scrape_interval: Optional[int] = None
    sync_interval: Optional[int] = None
    detail_fetch_limit: Optional[int] = None
    detail_concurrency: Optional[int] = None


@app.get("/api/config")
async def get_config():
    """Return current configuration (excluding sensitive fields like passwords)."""
    cfg = config.to_dict()
    # Remove sensitive fields
    cfg.pop("db_password", None)
    cfg.pop("db_host", None)
    cfg.pop("db_port", None)
    cfg.pop("db_user", None)
    cfg.pop("db_name", None)
    # Add runtime state
    all_list = data_manager.search_movies()
    cfg["movies_count"] = len(all_list) if all_list else 0
    last_time = _last_scrape_time
    cfg["last_scrape"] = (
        datetime.fromtimestamp(last_time, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
        if last_time else "never"
    )
    cfg["scrape_in_progress"] = False
    return ApiResponse(data=cfg)


@app.post("/api/config")
async def update_config(req: UpdateConfigRequest):
    """Dynamically update configuration at runtime.

    - Set collection_enabled=false to stop scheduled scraping immediately.
    - Set collection_enabled=true to re-enable it.
    - Other fields are updated in the config singleton and take effect on next cycle.
    """
    changed = []
    if req.collection_enabled is not None:
        old = config.collection_enabled
        os.environ["COLLECTION_ENABLED"] = "true" if req.collection_enabled else "false"
        config.reload()
        if old != config.collection_enabled:
            changed.append(f"collection_enabled: {old} → {config.collection_enabled}")
            if config.collection_enabled:
                logger.info("Collection re-enabled at runtime")
            else:
                logger.info("Collection disabled at runtime — in-progress scrape will finish on its own")

    if req.scrape_interval is not None and req.scrape_interval >= 60:
        os.environ["SCRAPE_INTERVAL"] = str(req.scrape_interval)
        changed.append(f"scrape_interval: {config.scrape_interval} → {req.scrape_interval}")

    if req.sync_interval is not None and req.sync_interval >= 60:
        os.environ["SYNC_INTERVAL"] = str(req.sync_interval)
        changed.append(f"sync_interval: {config.sync_interval} → {req.sync_interval}")

    if req.detail_fetch_limit is not None and req.detail_fetch_limit >= 1:
        os.environ["DETAIL_FETCH_LIMIT"] = str(req.detail_fetch_limit)
        changed.append(f"detail_fetch_limit: {config.detail_fetch_limit} → {req.detail_fetch_limit}")

    if req.detail_concurrency is not None and 1 <= req.detail_concurrency <= 20:
        os.environ["DETAIL_CONCURRENCY"] = str(req.detail_concurrency)
        changed.append(f"detail_concurrency: {config.detail_concurrency} → {req.detail_concurrency}")

    if changed:
        config.reload()
        logger.info("Config updated: %s", "; ".join(changed))

    return ApiResponse(data={"updated": changed, "config": config.to_dict()})


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------


@app.get("/api/health")
async def health_check():
    all_list = data_manager.search_movies()
    count = len(all_list) if all_list else 0
    last_time = _last_scrape_time
    last_str = datetime.fromtimestamp(last_time, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S") if last_time else "never"
    return {
        "status": "ok",
        "movies_count": count,
        "last_scrape": last_str,
        "collection_enabled": config.collection_enabled,
        "scrape_in_progress": False,
    }


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=config.host,
        port=config.port,
        log_level=config.log_level,
    )
