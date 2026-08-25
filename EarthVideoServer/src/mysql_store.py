"""MySQL-backed storage for EarthVideo.
Auto-creates database and tables if they don't exist.
Configuration loaded from .env file or environment variables."""

import html as html_mod
import json
import os
import logging
from typing import List, Optional, Dict, Any
from dotenv import load_dotenv
from .models import Movie

import aiomysql

logger = logging.getLogger("earthvideo.mysql")

load_dotenv()

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "127.0.0.1"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USER", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "db": os.getenv("DB_NAME", "earthvideo"),
    "autocommit": True,
}

CREATE_DB_SQL = "CREATE DATABASE IF NOT EXISTS earthvideo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS movies (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(500) NOT NULL DEFAULT '',
    description TEXT,
    poster_url VARCHAR(1000) DEFAULT '',
    type VARCHAR(100) DEFAULT '',
    region VARCHAR(100) DEFAULT '',
    year INT DEFAULT 0,
    director VARCHAR(500) DEFAULT '',
    actors TEXT,
    episode_total INT DEFAULT 0,
    episode_updated INT DEFAULT 0,
    episode_tag VARCHAR(100) DEFAULT '',
    hot_tag TINYINT(1) DEFAULT 0,
    rating FLOAT DEFAULT 0.0,
    tags VARCHAR(500) DEFAULT '',
    source VARCHAR(100) DEFAULT '',
    introduction TEXT,
    publish_date VARCHAR(10) DEFAULT '',
    play_urls JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
"""

def _clean(text: str) -> str:
    """Unescape HTML entities in a string; no-op if no entities or on non-string."""
    if not text or not isinstance(text, str) or '&' not in text:
        return text or ""
    try:
        return html_mod.unescape(text).strip()
    except Exception:
        return text


def _clean_play_urls(raw: Any) -> Dict[str, Dict[str, str]]:
    """Unescape entity keys in a play_urls dict."""
    if not raw:
        return {}
    if isinstance(raw, str):
        try:
            raw = json.loads(raw)
        except (json.JSONDecodeError, TypeError):
            return {}
    if not isinstance(raw, dict):
        return {}
    cleaned: Dict[str, Dict[str, str]] = {}
    for sk, sv in raw.items():
        sk_clean = _clean(str(sk))
        if isinstance(sv, dict):
            cleaned[sk_clean] = {str(ek): str(ev) for ek, ev in sv.items()}
        else:
            cleaned[sk_clean] = {}
    return cleaned


class MySQLStore:
    """Async MySQL store for movie data."""

    def __init__(self):
        self._pool = None

    async def connect(self):
        """Create connection pool, auto-creating DB if needed."""
        # First connect without db to create it
        try:
            tmp_conn = await aiomysql.connect(
                host=DB_CONFIG["host"],
                port=DB_CONFIG["port"],
                user=DB_CONFIG["user"],
                password=DB_CONFIG["password"],
                autocommit=True,
            )
            async with tmp_conn.cursor() as cur:
                await cur.execute(CREATE_DB_SQL)
            tmp_conn.close()
        except Exception as e:
            logger.warning("DB setup warning: %s", e)

        self._pool = await aiomysql.create_pool(**DB_CONFIG, minsize=1, maxsize=10)
        async with self._pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(CREATE_TABLE_SQL)
                # Upgrade path: add publish_date if the column is missing.
                try:
                    await cur.execute(
                        "ALTER TABLE movies ADD COLUMN publish_date VARCHAR(10) DEFAULT ''"
                    )
                    logger.info("Added publish_date column to movies table")
                except Exception:
                    pass  # column already exists
        logger.info("MySQL store ready")

    async def close(self):
        if self._pool:
            self._pool.close()
            await self._pool.wait_closed()

    def _movie_to_row(self, m: Movie) -> tuple:
        """Convert Movie to DB row tuple, sanitizing HTML entities on write."""
        return (
            m.id,
            _clean(m.title) if m.title else "",
            _clean(m.description or ""),
            m.posterUrl or "",
            _clean(m.type or ""),
            _clean(m.region or ""),
            m.year or 0,
            _clean(m.director or ""),
            json.dumps([_clean(a) for a in (m.actors or [])], ensure_ascii=False) if m.actors else "[]",
            m.episodeTotal or 0,
            m.episodeUpdated or 0,
            _clean(m.episodeTag or ""),
            1 if m.hotTag else 0,
            m.rating or 0.0,
            _clean(m.tags or ""),
            _clean(m.source or ""),
            _clean(m.introduction or ""),
            (m.publishDate or "")[:10],
            json.dumps({_clean(str(k)): v for k, v in (m.playUrls or {}).items()}, ensure_ascii=False) if m.playUrls else "{}",
        )

    def _row_to_movie(self, row: tuple) -> Movie:
        """Convert DB row to Movie, sanitizing HTML entities on read.
        
        Column order (SELECT *): id(0), title(1), description(2), poster_url(3),
        type(4), region(5), year(6), director(7), actors(8), episode_total(9),
        episode_updated(10), episode_tag(11), hot_tag(12), rating(13), tags(14),
        source(15), introduction(16), play_urls(17), created_at(18), updated_at(19),
        publish_date(20) — publish_date is at the end because it was added via
        ALTER TABLE ADD COLUMN.
        """
        play_urls = _clean_play_urls(row[17] if len(row) > 17 else None)

        actors = []
        if row[8]:
            try:
                raw = json.loads(row[8]) if isinstance(row[8], str) else row[8]
                actors = [_clean(a) for a in raw if isinstance(a, str)]
            except (json.JSONDecodeError, TypeError):
                actors = []

        return Movie(
            id=row[0],
            title=_clean(row[1]) if len(row) > 1 else "",
            description=_clean(row[2] or "") if len(row) > 2 else "",
            posterUrl=(row[3] or "") if len(row) > 3 else "",
            type=_clean(row[4] or "") if len(row) > 4 else "",
            region=_clean(row[5] or "") if len(row) > 5 else "",
            year=row[6] or 0,
            director=_clean(row[7] or "") if len(row) > 7 else "",
            actors=actors,
            episodeTotal=row[9] or 0,
            episodeUpdated=row[10] or 0,
            episodeTag=_clean(row[11] or "") if len(row) > 11 else "",
            hotTag=bool(row[12]) if len(row) > 12 else False,
            rating=float(row[13] or 0) if len(row) > 13 else 0.0,
            tags=_clean(row[14] or "") if len(row) > 14 else "",
            source=_clean(row[15] or "") if len(row) > 15 else "",
            introduction=_clean(row[16] or "") if len(row) > 16 else "",
            publishDate=(row[20] or None) if len(row) > 20 and row[20] else None,
            playUrls=play_urls,
        )

    async def upsert_movie(self, m: Movie):
        """Insert or update a movie."""
        if not self._pool:
            return
        sql = """
            INSERT INTO movies (id, title, description, poster_url, type, region, year,
                director, actors, episode_total, episode_updated, episode_tag,
                hot_tag, rating, tags, source, introduction, publish_date, play_urls)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON DUPLICATE KEY UPDATE
                title=VALUES(title), description=VALUES(description),
                poster_url=VALUES(poster_url), type=VALUES(type), region=VALUES(region),
                year=VALUES(year), director=VALUES(director), actors=VALUES(actors),
                episode_total=VALUES(episode_total), episode_updated=VALUES(episode_updated),
                episode_tag=VALUES(episode_tag), hot_tag=VALUES(hot_tag),
                rating=VALUES(rating), tags=VALUES(tags), source=VALUES(source),
                introduction=VALUES(introduction), publish_date=VALUES(publish_date),
                play_urls=VALUES(play_urls)
        """
        async with self._pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(sql, self._movie_to_row(m))

    async def batch_upsert(self, movies: List[Movie]):
        """Batch upsert movies."""
        if not movies or not self._pool:
            return
        sql = """
            INSERT INTO movies (id, title, description, poster_url, type, region, year,
                director, actors, episode_total, episode_updated, episode_tag,
                hot_tag, rating, tags, source, introduction, publish_date, play_urls)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON DUPLICATE KEY UPDATE
                title=VALUES(title), description=VALUES(description),
                poster_url=VALUES(poster_url), type=VALUES(type), region=VALUES(region),
                year=VALUES(year), director=VALUES(director), actors=VALUES(actors),
                episode_total=VALUES(episode_total), episode_updated=VALUES(episode_updated),
                episode_tag=VALUES(episode_tag), hot_tag=VALUES(hot_tag),
                rating=VALUES(rating), tags=VALUES(tags), source=VALUES(source),
                introduction=VALUES(introduction), publish_date=VALUES(publish_date),
                play_urls=VALUES(play_urls)
        """
        rows = [self._movie_to_row(m) for m in movies]
        async with self._pool.acquire() as conn:
            async with conn.cursor() as cur:
                for row in rows:
                    await cur.execute(sql, row)

    async def get_movie(self, movie_id: str) -> Optional[Movie]:
        """Get a single movie by ID."""
        if not self._pool:
            return None
        async with self._pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute("SELECT * FROM movies WHERE id = %s", (movie_id,))
                row = await cur.fetchone()
                return self._row_to_movie(row) if row else None

    async def search_movies(
        self,
        keyword: str = "",
        type_filter: str = "all",
        region: str = "all",
        year: str = "all",
        sort: str = "最热",
        page: int = 1,
        size: int = 20,
    ) -> tuple:
        """Search movies with filters. Returns (movies, total_count)."""
        if not self._pool:
            return [], 0

        conditions = []
        params = []

        if keyword:
            conditions.append("title LIKE %s")
            params.append(f"%{keyword}%")

        type_map = {
            "电视剧": "电视剧", "电影": "电影",
            "综艺": "综艺", "动漫": "动漫", "短剧": "短剧",
        }
        if type_filter in type_map:
            conditions.append("type LIKE %s")
            params.append(f"%{type_map[type_filter]}%")

        if region != "all":
            conditions.append("region LIKE %s")
            params.append(f"%{region}%")

        if year != "all":
            try:
                conditions.append("year = %s")
                params.append(int(year))
            except ValueError:
                pass

        where = " WHERE " + " AND ".join(conditions) if conditions else ""

        sort_map = {
            "最热": "ORDER BY hot_tag DESC, rating DESC",
            "评分": "ORDER BY rating DESC",
            "最近更新": "ORDER BY publish_date DESC, episode_updated DESC",
            "最新上线": "ORDER BY publish_date DESC, episode_updated DESC, created_at DESC",
            "最新": "ORDER BY publish_date DESC, episode_updated DESC, created_at DESC",
        }
        # Default: newest first (by media publish date, then recency).
        order = sort_map.get(sort, "ORDER BY publish_date DESC, episode_updated DESC, created_at DESC")

        # Count
        async with self._pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(f"SELECT COUNT(*) FROM movies{where}", params)
                total = (await cur.fetchone())[0]

            # Data
            offset = (page - 1) * size
            async with conn.cursor() as cur:
                sql = f"SELECT * FROM movies{where} {order} LIMIT %s OFFSET %s"
                await cur.execute(sql, params + [size, offset])
                rows = await cur.fetchall()

        movies = [self._row_to_movie(r) for r in rows]
        # Deduplicate by id
        seen = set()
        unique = []
        for m in movies:
            if m.id not in seen:
                seen.add(m.id)
                unique.append(m)
        return unique, total

    async def get_all_movie_ids(self) -> List[str]:
        """Get all movie IDs."""
        if not self._pool:
            return []
        async with self._pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute("SELECT id FROM movies")
                rows = await cur.fetchall()
                return [r[0] for r in rows]

    async def get_play_url(
        self, movie_id: str, episode: int = 1, source: str = "default"
    ) -> Optional[str]:
        """Get play URL for a specific episode, optionally on a specific source."""
        m = await self.get_movie(movie_id)
        if not m or not m.playUrls:
            return None
        play_urls = m.playUrls or {}
        episode_key = str(episode)
        if source == "default":
            for _sname, eps in play_urls.items():
                if eps and episode_key in eps:
                    return eps[episode_key]
            for _sname, eps in play_urls.items():
                if eps and episode in eps:
                    return eps[episode]
            return None
        bucket = play_urls.get(source)
        if not bucket:
            return None
        if episode_key in bucket:
            return bucket[episode_key]
        if episode in bucket:
            return bucket[episode]
        return None

    async def delete_play_url(
        self, movie_id: str, episode: int, source: str = "default"
    ) -> None:
        """Remove a cached play URL so the next request fetches fresh from the source."""
        m = await self.get_movie(movie_id)
        if not m or not m.playUrls:
            return
        play_urls = m.playUrls or {}
        if source == "default":
            for sname in list(play_urls.keys()):
                eps = play_urls[sname]
                if episode in eps:
                    del eps[episode]
                    if not eps:
                        del play_urls[sname]
        elif source in play_urls:
            eps = play_urls[source]
            if episode in eps:
                del eps[episode]
                if not eps:
                    del play_urls[source]
        await self.upsert_movie(m)
