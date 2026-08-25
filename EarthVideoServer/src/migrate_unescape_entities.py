"""One-off migration: unescape HTML entities in the movies table.

Run this after deploying the mysql_store.py fix to clean existing rows.
The fix in _row_to_movie sanitizes on read (so the API already returns clean text),
but this migration makes the DB itself clean for future use.

Usage: python src/migrate_unescape_entities.py
"""

import asyncio
import html as html_mod
import logging
import json
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(levelname)s: %(message)s")
logger = logging.getLogger("migrate")

from src.mysql_store import MySQLStore, _clean, _clean_play_urls


async def migrate():
    store = MySQLStore()
    await store.connect()
    if not store._pool:
        logger.error("No MySQL pool — aborting")
        return

    async with store._pool.acquire() as conn:
        async with conn.cursor() as cur:
            # Get all rows
            await cur.execute("SELECT * FROM movies")
            rows = await cur.fetchall()
            logger.info("Loaded %s movie rows", len(rows))

            updated = 0
            for row in rows:
                mid = row[0]
                dirty = False

                # Fields to clean (by index in SELECT *)
                text_fields = [
                    (1, "title"), (2, "description"), (4, "type"), (5, "region"),
                    (7, "director"), (8, "actors"), (11, "episode_tag"),
                    (14, "tags"), (15, "source"), (16, "introduction"),
                ]
                updates = {}
                for idx, col in text_fields:
                    val = row[idx] if idx < len(row) else ""
                    if isinstance(val, str) and '&' in val:
                        cleaned = _clean(val)
                        if cleaned != val:
                            updates[col] = cleaned
                            dirty = True

                # actors JSON
                if "actors" in updates:
                    try:
                        raw = json.loads(updates["actors"]) if isinstance(updates["actors"], str) else updates["actors"]
                        updates["actors"] = json.dumps([_clean(a) for a in raw], ensure_ascii=False)
                    except Exception:
                        pass

                # play_urls keys
                play_raw = row[18] if len(row) > 18 else None
                if play_raw and isinstance(play_raw, str) and '&' in play_raw:
                    cleaned_pu = _clean_play_urls(play_raw)
                    updates["play_urls"] = json.dumps(cleaned_pu, ensure_ascii=False)
                    dirty = True

                # publish_date: fix empty string → None
                pub = row[17] if len(row) > 17 else None
                if pub is not None and pub == "":
                    updates["publish_date"] = None
                    dirty = True

                if dirty and updates:
                    set_parts = ", ".join(f"{col} = %s" for col in updates)
                    vals = list(updates.values())
                    sql = f"UPDATE movies SET {set_parts} WHERE id = %s"
                    await cur.execute(sql, vals + [mid])
                    updated += 1
                    if updated % 500 == 0:
                        logger.info("Migrated %s rows...", updated)

            logger.info("Migration complete: %s/%s rows updated", updated, len(rows))

    await store.close()


if __name__ == "__main__":
    asyncio.run(migrate())