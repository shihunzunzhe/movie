"""Standalone subprocess scraper — runs in a completely isolated process.

Called by the main API server via subprocess.Popen so the scrape never
touches the main event loop, its thread pool, or the main MySQL pool.
"""
import asyncio
import sys
import os
import time
import logging

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger("earthvideo.scraper")

from src.data_sources import DataSourceManager
from src.mysql_store import MySQLStore


async def run_scrape():
    logger.info("=" * 50)
    logger.info("Starting scrape cycle (subprocess)…")

    mgr = DataSourceManager()
    mgr.add_source("yutu", "https://yutuzy10.com", enabled=True)
    mgr.add_source("mogu", "https://www.5o5k.com", enabled=True)

    for src in mgr.sources:
        src_name = getattr(src, "name", None) or getattr(src, "SOURCE_NAME", "?")
        try:
            await src.fetch_movies(force=True)
            logger.info("%s: catalog fetched, %s movies", src_name, len(src._movies))
            await src.batch_fetch_details(limit=500, concurrency=6)
            logger.info("%s: details fetched", src_name)
        except Exception as e:
            logger.warning("%s: scrape failed: %s", src_name, e)

    # Sync all sources to MySQL
    store = MySQLStore()
    await store.connect()
    try:
        for src in mgr.sources:
            if src._movies:
                src_movies = list(src._movies.values())
                sname = getattr(src, "name", None) or getattr(src, "SOURCE_NAME", "?")
                await store.batch_upsert(src_movies)
                logger.info("Synced %s %s movies to MySQL", len(src_movies), sname)
    finally:
        await store.close()

    # Record last scrape time
    with open("/tmp/earthvideo_last_scrape.txt", "w") as f:
        f.write(str(time.time()))

    logger.info("Scrape cycle complete!")
    logger.info("=" * 50)


if __name__ == "__main__":
    asyncio.run(run_scrape())