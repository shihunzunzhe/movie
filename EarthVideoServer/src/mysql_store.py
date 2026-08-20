"""MySQL-backed storage for EarthVideo.
Auto-creates database and tables if they don't exist.
Configuration loaded from .env file or environment variables."""

import json
import os
import logging
from typing import List, Optional
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
    play_urls JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
"""

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
        logger.info("MySQL store ready")

    async def close(self):
        if self._pool:
            self._pool.close()
            await self._pool.wait_closed()

    def _movie_to_row(self, m: Movie) -> tuple:
        """Convert Movie to DB row tuple."""
        return (
            m.id,
            m.title,
            m.description or "",
            m.posterUrl or "",
            m.type or "",
            m.region or "",
            m.year or 0,
            m.director or "",
            json.dumps(m.actors, ensure_ascii=False) if m.actors else "[]",
            m.episodeTotal or 0,
            m.episodeUpdated or 0,
            m.episodeTag or "",
            1 if m.hotTag else 0,
            m.rating or 0.0,
            m.tags or "",
            m.source or "",
            m.introduction or "",
            json.dumps(m.playUrls, ensure_ascii=False) if m.playUrls else "{}",
        )

    def _row_to_movie(self, row: tuple) -> Movie:
        """Convert DB row to Movie."""
        play_urls = {}
        if row[17]:
            try:
                play_urls = json.loads(row[17]) if isinstance(row[17], str) else row[17]
            except (json.JSONDecodeError, TypeError):
                play_urls = {}

        actors = []
        if row[8]:
            try:
                actors = json.loads(row[8]) if isinstance(row[8], str) else row[8]
            except (json.JSONDecodeError, TypeError):
                actors = []

        return Movie(
            id=row[0],
            title=row[1],
            description=row[2] or "",
            posterUrl=row[3] or "",
            type=row[4] or "",
            region=row[5] or "",
            year=row[6] or 0,
            director=row[7] or "",
            actors=actors,
            episodeTotal=row[9] or 0,
            episodeUpdated=row[10] or 0,
            episodeTag=row[11] or "",
            hotTag=bool(row[12]),
            rating=row[13] or 0.0,
            tags=row[14] or "",
            source=row[15] or "",
            introduction=row[16] or "",
            playUrls=play_urls,
        )

    async def upsert_movie(self, m: Movie):
        """Insert or update a movie."""
        if not self._pool:
            return
        sql = """
            INSERT INTO movies (id, title, description, poster_url, type, region, year,
                director, actors, episode_total, episode_updated, episode_tag,
                hot_tag, rating, tags, source, introduction, play_urls)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON DUPLICATE KEY UPDATE
                title=VALUES(title), description=VALUES(description),
                poster_url=VALUES(poster_url), type=VALUES(type), region=VALUES(region),
                year=VALUES(year), director=VALUES(director), actors=VALUES(actors),
                episode_total=VALUES(episode_total), episode_updated=VALUES(episode_updated),
                episode_tag=VALUES(episode_tag), hot_tag=VALUES(hot_tag),
                rating=VALUES(rating), tags=VALUES(tags), source=VALUES(source),
                introduction=VALUES(introduction), play_urls=VALUES(play_urls)
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
                hot_tag, rating, tags, source, introduction, play_urls)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON DUPLICATE KEY UPDATE
                title=VALUES(title), description=VALUES(description),
                poster_url=VALUES(poster_url), type=VALUES(type), region=VALUES(region),
                year=VALUES(year), director=VALUES(director), actors=VALUES(actors),
                episode_total=VALUES(episode_total), episode_updated=VALUES(episode_updated),
                episode_tag=VALUES(episode_tag), hot_tag=VALUES(hot_tag),
                rating=VALUES(rating), tags=VALUES(tags), source=VALUES(source),
                introduction=VALUES(introduction), play_urls=VALUES(play_urls)
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
            "最近更新": "ORDER BY episode_updated DESC",
            "最新上线": "ORDER BY year DESC",
        }
        order = sort_map.get(sort, "ORDER BY hot_tag DESC, rating DESC")

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

    async def get_play_url(self, movie_id: str, episode: int = 1) -> Optional[str]:
        """Get play URL for a specific episode."""
        m = await self.get_movie(movie_id)
        if m and m.playUrls and episode in m.playUrls:
            return m.playUrls[episode]
        return None
