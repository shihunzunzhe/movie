"""One-time/idempotent migration for nested playUrls.

Old shape:  {"1": "https://..."}
New shape:  {"\u7389\u5154\u6e90": {"1": "https://..."}}
"""

import asyncio
import json
import os
import sys

import aiomysql
from dotenv import load_dotenv


load_dotenv()

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "127.0.0.1"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USER", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "db": os.getenv("DB_NAME", "earthvideo"),
    "autocommit": True,
}

YUTU_SOURCE = "\u7389\u5154\u6e90"


async def run():
    conn = await aiomysql.connect(**DB_CONFIG)
    async with conn.cursor() as cur:
        await cur.execute("SELECT id, source, play_urls FROM movies")
        rows = await cur.fetchall()

    migrated = 0
    already_nested = 0
    empty = 0
    for mid, src_name, raw in rows:
        if not raw or raw == "{}":
            empty += 1
            continue
        try:
            data = json.loads(raw) if isinstance(raw, str) else raw
        except (json.JSONDecodeError, TypeError):
            empty += 1
            continue
        if not isinstance(data, dict) or not data:
            empty += 1
            continue

        first_key = next(iter(data))
        first_val = data[first_key]
        if isinstance(first_val, dict):
            if src_name != YUTU_SOURCE:
                async with conn.cursor() as cur:
                    await cur.execute(
                        "UPDATE movies SET source = %s WHERE id = %s",
                        (YUTU_SOURCE, mid),
                    )
            already_nested += 1
            continue

        nested = {YUTU_SOURCE: data}
        async with conn.cursor() as cur:
            await cur.execute(
                "UPDATE movies SET play_urls = %s, source = %s WHERE id = %s",
                (json.dumps(nested, ensure_ascii=False), YUTU_SOURCE, mid),
            )
        migrated += 1

    async with conn.cursor() as cur:
        await cur.execute(
            "UPDATE movies SET source = %s WHERE source = %s",
            (YUTU_SOURCE, "\u7389\u5154\u8d44\u6e90"),
        )

    print(f"migrated: {migrated}, already-nested: {already_nested}, empty: {empty}")
    conn.close()


if __name__ == "__main__":
    if os.environ.get("PYTHONPATH"):
        sys.path.insert(0, os.environ["PYTHONPATH"])
    asyncio.run(run())
