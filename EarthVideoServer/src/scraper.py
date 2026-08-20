"""Standalone scraper that runs independently from the API server."""
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

from src.data_sources import YutuHtmlSource
from src.mysql_store import MySQLStore

LAST_SCRAPE_FILE = "/tmp/earthvideo_last_scrape.txt"

async def run_scrape():
    """Full scrape cycle: catalog + detail enrichment + MySQL sync."""
    logger.info("=" * 50)
    logger.info("Starting scrape cycle...")

    src = YutuHtmlSource("https://yutuzy10.com")
    await src.fetch_movies(force=True)
    movie_count = len(src._movies)
    logger.info("Catalog fetched: %s movies", movie_count)

    # Batch fetch details
    logger.info("Fetching details for up to 200 movies...")
    await src.batch_fetch_details(limit=200, concurrency=5)

    # Connect to MySQL and sync
    store = MySQLStore()
    await store.connect()

    all_movies = list(src._movies.values())
    await store.batch_upsert(all_movies)
    logger.info("Synced %s movies to MySQL", len(all_movies))

    # Also fetch details for movies in MySQL that don't have poster URLs
    db_ids = set(await store.get_all_movie_ids())
    missing_details = []
    for mid, m in src._movies.items():
        if not m.posterUrl and mid in db_ids:
            missing_details.append(mid)

    if missing_details:
        logger.info("Fetching details for %s movies with missing posters...", len(missing_details))
        await src.batch_fetch_details(limit=min(len(missing_details), 200), concurrency=5)
        all_movies = list(src._movies.values())
        await store.batch_upsert(all_movies)
        logger.info("Re-synced after detail enrichment")

    await store.close()

    # Record last scrape time
    with open(LAST_SCRAPE_FILE, "w") as f:
        f.write(str(time.time()))

    logger.info("Scrape cycle complete!")
    logger.info("=" * 50)

async def main():
    while True:
        try:
            await run_scrape()
        except Exception as e:
            logger.error("Scrape cycle failed: %s", e)
        logger.info("Sleeping 2 hours until next scrape...")
        await asyncio.sleep(7200)

if __name__ == "__main__":
    asyncio.run(main())
