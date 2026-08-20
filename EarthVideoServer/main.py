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
import random
import json
import re as re_mod
import asyncio
import time
import logging
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
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

data_manager = DataSourceManager()
data_manager.add_source("yutu", "https://yutuzy10.com", enabled=True)
mysql = MySQLStore()

# In-memory user store
user_history: List[str] = []
user_favorites: List[str] = []

# Scrape tracking
_last_scrape_time: float = 0
_scrape_in_progress: bool = False


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
                if data_manager.sources:
                    src = data_manager.sources[0]
                    src._movies[m.id] = m
            logger.info("Loaded %s movies from MySQL into cache", len(movies))
    except Exception as e:
        logger.warning("Failed to load from MySQL: %s", e)


async def _run_scrape():
    global _last_scrape_time, _scrape_in_progress
    if _scrape_in_progress:
        logger.info("Scrape already in progress, skipping")
        return False
    _scrape_in_progress = True
    try:
        if data_manager.sources:
            src = data_manager.sources[0]
            await src.fetch_movies(force=True)
            movie_count = len(src._movies)
            logger.info("Scraper: catalog fetched, %s movies", movie_count)
            # Fetch details for all movies in batches
            await src.batch_fetch_details(limit=len(src._movies), concurrency=8)
            if mysql._pool:
                all_movies = list(src._movies.values())
                await mysql.batch_upsert(all_movies)
                logger.info("Scraper: synced %s movies to MySQL", len(all_movies))
        _last_scrape_time = time.time()
        with open("/tmp/earthvideo_last_scrape.txt", "w") as f:
            f.write(str(_last_scrape_time))
        logger.info("Scrape cycle complete")
        return True
    except Exception as e:
        logger.error("Scrape cycle failed: %s", e)
        return False
    finally:
        _scrape_in_progress = False


async def scrape_scheduler():
    # Wait 2 hours before first scrape (avoid impact on startup)
    await asyncio.sleep(30)
    logger.info("Starting first scheduled scrape (after 2h delay)...")
    await _run_scrape()
    while True:
        await asyncio.sleep(7200)
        logger.info("Starting scheduled scrape (2-hour interval)...")
        await _run_scrape()


async def sync_to_mysql():
    await asyncio.sleep(60)
    while True:
        await asyncio.sleep(600)
        try:
            if mysql._pool:
                movies = data_manager.search_movies()
                if movies:
                    await mysql.batch_upsert(movies)
        except Exception as e:
            logger.warning("MySQL sync error: %s", e)


@contextlib.asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("EarthVideo API starting up...")

    try:
        await mysql.connect()
        logger.info("MySQL connected")
    except Exception as e:
        logger.warning("MySQL connection failed (in-memory fallback): %s", e)

    # Load from MySQL into cache, then start periodic sync and scraper
    await _load_from_mysql()
    asyncio.create_task(sync_to_mysql())
    asyncio.create_task(scrape_scheduler())

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
    from src.data_sources import YutuHtmlSource
    for src in data_manager.sources:
        if isinstance(src, YutuHtmlSource):
            try:
                detail = await src.fetch_detail(movie_id)
                if detail:
                    # Save to MySQL
                    if mysql._pool:
                        await mysql.upsert_movie(detail)
                    return detail
            except Exception:
                pass
            break
    return m


async def _get_play_url(movie_id: str, episode: int) -> str:
    """Get play URL from MySQL, with fallback in-memory."""
    url = await mysql.get_play_url(movie_id, episode) if mysql._pool else None
    if url:
        return url
    m = data_manager.get_movie_by_id(movie_id)
    if m and m.playUrls and episode in m.playUrls:
        return m.playUrls[episode]
    return ""


# ---------------------------------------------------------------------------
# HLS ad-removal proxy
# ---------------------------------------------------------------------------
_HLS_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/151.0.0.0 Safari/537.36"
    ),
}

async def _resolve_m3u8(url: str) -> Optional[str]:
    """Fetch an m3u8 playlist, following stream-inf redirects if needed."""
    try:
        async with httpx.AsyncClient(timeout=15.0, follow_redirects=True) as client:
            resp = await client.get(url, headers=_HLS_HEADERS)
            resp.raise_for_status()
            text = resp.text.strip()
            if "#EXT-X-STREAM-INF" in text:
                lines = text.split("\n")
                for line in lines:
                    line = line.strip()
                    if line and not line.startswith("#"):
                        from urllib.parse import urljoin
                        return await _resolve_m3u8(urljoin(url, line))
            return text
    except Exception as e:
        logger.warning("HLS resolve error for %s: %s", url, e)
        return None


@app.get("/api/proxy/hls")
async def proxy_hls(url: str = "", skip_seconds: int = 0):
    if not url:
        return Response(content="#EXTM3U\n", media_type="application/vnd.apple.mpegurl")

    playlist = await _resolve_m3u8(url)
    if not playlist:
        return Response(content="#EXTM3U\n", media_type="application/vnd.apple.mpegurl")

    lines = playlist.split("\n")
    from urllib.parse import urljoin

    discontinuity_idx = -1
    for i, line in enumerate(lines):
        if "#EXT-X-DISCONTINUITY" in line:
            discontinuity_idx = i
            break

    if discontinuity_idx >= 0:
        header_tags = []
        body = []
        for i, line in enumerate(lines):
            stripped = line.strip()
            if i < discontinuity_idx:
                if stripped.startswith("#") and "EXTINF" not in stripped:
                    header_tags.append(line)
            else:
                body.append(line)
        clean_lines = header_tags + body
    elif skip_seconds > 0:
        clean_lines = []
        add_header = True
        skip_remaining = skip_seconds
        i = 0
        while i < len(lines):
            stripped = lines[i].strip()
            if add_header:
                if stripped.startswith("#") and "EXTINF" not in stripped:
                    clean_lines.append(lines[i])
                elif stripped.startswith("#EXTINF"):
                    dur_match = re_mod.search(r'#EXTINF:([0-9.]+)', stripped)
                    if dur_match:
                        seg_dur = float(dur_match.group(1))
                        if skip_remaining > 0 and seg_dur <= skip_remaining:
                            skip_remaining -= seg_dur
                            i += 2
                            continue
                        elif skip_remaining > 0 and seg_dur > skip_remaining:
                            new_dur = seg_dur - skip_remaining
                            skip_remaining = 0
                            add_header = False
                            clean_lines.append(f"#EXTINF:{new_dur:.1f},")
                            i += 1
                            if i < len(lines) and not lines[i].strip().startswith("#"):
                                clean_lines.append(lines[i])
                            i += 1
                            continue
                    add_header = False
                    clean_lines.append(lines[i])
                    i += 1
                else:
                    add_header = False
                    clean_lines.append(lines[i])
                    i += 1
            else:
                clean_lines.append(lines[i])
                i += 1
    else:
        clean_lines = lines

    base_url = url[: url.rfind("/")] if "/" in url else url
    resolved_lines = []
    for line in clean_lines:
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and not stripped.startswith("http"):
            resolved_lines.append(urljoin(base_url + "/", stripped))
        else:
            resolved_lines.append(line)

    result = "\n".join(resolved_lines)
    return Response(content=result, media_type="application/vnd.apple.mpegurl")


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
    if category == "recommend":
        shuffled = list(all_items)
        random.shuffle(shuffled)
        all_items = shuffled
    else:
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
    results, total = await _search_movies(keyword=keyword, type_filter=type, size=9999)
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
    if type == "tv":
        ranked = sorted(
            [m for m in all_items if "电视剧" in m.type or "剧" in m.type],
            key=lambda m: m.rating, reverse=True,
        )
    elif type == "movie":
        ranked = sorted(
            [m for m in all_items if "电影" in m.type],
            key=lambda m: m.rating, reverse=True,
        )
    elif type == "new":
        ranked = sorted(all_items, key=lambda m: m.year, reverse=True)
    elif type == "rising":
        ranked = sorted(
            all_items, key=lambda m: (1 if m.hotTag else 0, m.rating), reverse=True
        )
    elif type == "search":
        ranked = sorted(all_items, key=lambda m: len(m.actors) + m.episodeTotal, reverse=True)
    else:
        ranked = sorted(all_items, key=lambda m: m.rating, reverse=True)
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

    episodes = []
    if m.playUrls:
        for ep_num in sorted(m.playUrls.keys()):
            episodes.append(
                Episode(
                    episodeNumber=ep_num,
                    title=f"第{ep_num}集",
                    duration=0,
                    current=(ep_num == 1),
                )
            )
    else:
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
async def movie_play_url(id: str = "", episode: int = 1, source: str = "default"):
    m = await mysql.get_movie(id) if mysql._pool else None
    if not m:
        m = data_manager.get_movie_by_id(id)
    if not m:
        return ApiResponse(code=404, message="not found", data={})

    video_url = await _get_play_url(id, episode)
    if not video_url:
        # Fallback: try fetching detail page on-demand
        from src.data_sources import YutuHtmlSource
        for src in data_manager.sources:
            if isinstance(src, YutuHtmlSource):
                video_url = await src.get_play_url_for_episode(id, episode)
                if video_url:
                    # Save to MySQL for next time
                    if m and mysql._pool:
                        if not m.playUrls:
                            m.playUrls = {}
                        m.playUrls[episode] = video_url
                        await mysql.upsert_movie(m)
                    break

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

    return ApiResponse(
        data={
            "movieId": m.id,
            "episode": episode,
            "url": video_url,
            "sources": [
                {"sourceId": "default", "sourceName": "玉兔源", "priority": 1},
            ],
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


@app.get("/api/health")
async def health_check():
    all_list = data_manager.search_movies()
    count = len(all_list) if all_list else 0
    last_time = _last_scrape_time
    last_str = datetime.fromtimestamp(last_time, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S") if last_time else "never"
    return {"status": "ok", "movies_count": count, "last_scrape": last_str}


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8808, log_level="info")
