import asyncio
import re
import time
from typing import List, Optional, Dict
from .models import Movie

import httpx
import logging

logger = logging.getLogger("earthvideo")

CATEGORY_IDS = [20, 22, 23, 24, 25, 26, 27, 28, 29, 31, 32, 33, 35, 36, 44, 56, 57, 60, 61, 62, 63, 64, 65, 66]

CATEGORY_NAME_MAP = {
    20: "\u7cbe\u54c1\u63a8\u8350", 22: "\u4e3b\u64ad\u79c0\u8272", 23: "\u65e5\u672c\u6709\u7801", 24: "\u65e5\u672c\u65e0\u7801",
    25: "\u4e2d\u6587\u5b57\u5e55", 26: "\u7ae5\u989c\u5de8\u4e73", 27: "\u6027\u611f\u4eba\u59bb", 28: "\u5f3a\u5978\u4e71\u4f26",
    29: "\u6b27\u7f8e\u60c5\u8272", 31: "\u4e09\u7ea7\u4f26\u7406", 32: "\u5361\u901a\u52a8\u6f2b", 33: "\u4e1d\u889cOL",
    35: "\u81ea\u62cd\u5077\u62cd", 36: "\u65e5\u672c\u7247\u5546", 44: "\u5267\u60c5\u4ecb\u7ecd", 56: "\u7f51\u66dd\u7cfb\u5217",
    57: "\u9ebb\u8c46\u4f20\u5a92", 60: "\u660e\u661f\u6362\u8138", 61: "\u56fd\u4ea7\u4e71\u4f26", 62: "\u56fd\u4ea7\u4e1d\u889c",
    63: "\u56fd\u4ea7SM", 64: "\u56fd\u4ea7\u4eba\u59bb", 65: "\u63a2\u82b1\u5a36\u5a3f", 66: "\u540c\u6027\u604b",
}

MAX_PAGES = 1


def _merge_movie_fields(existing: Movie, fresh: Movie) -> Movie:
    for field in ['posterUrl', 'director', 'actors', 'region', 'year',
                  'introduction', 'episodeTotal', 'episodeUpdated',
                  'episodeTag', 'playUrls']:
        existing_val = getattr(existing, field, None)
        if existing_val and (isinstance(existing_val, (str, int, float, list, dict)) and existing_val):
            setattr(fresh, field, existing_val)
    return fresh


class YutuHtmlSource:
    def __init__(self, base_url: str = "https://yutuzy10.com"):
        self.base_url = base_url.rstrip("/")
        self._movies: Dict[str, Movie] = {}
        self._last_fetch: float = 0
        self._fetch_interval: float = 300
        self._headers = {
            "User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/151.0.0.0 Safari/537.36"
            ),
            "accept-language": "zh-CN,zh;q=0.9",
            "Cookie": (
                "server_name_session=7b4b3d1f843f21b24e99ed57cb393d6a; "
                "PHPSESSID=67rnphq1tlnd2gakvc05b6jtru"
            ),
        }

    async def fetch_movies(self, force: bool = False) -> List[Movie]:
        now = time.time()
        if not force and self._movies and (now - self._last_fetch) < self._fetch_interval:
            return list(self._movies.values())

        all_movies: Dict[str, Movie] = {}
        async with httpx.AsyncClient(
            timeout=30.0,
            limits=httpx.Limits(max_keepalive_connections=2),
        ) as client:
            for cid in CATEGORY_IDS:
                for page in range(1, MAX_PAGES + 1):
                    retries = 2
                    for attempt in range(retries):
                        try:
                            if page == 1:
                                url = f"{self.base_url}/index.php/vod/type/id/{cid}.html"
                            else:
                                url = f"{self.base_url}/index.php/vod/type/id/{cid}/page/{page}.html"
                            resp = await client.get(url, headers=self._headers)
                            resp.raise_for_status()
                            items = self._parse_category_page(resp.text, cid)
                            if not items:
                                break
                            for m in items:
                                existing = self._movies.get(m.id)
                                if existing:
                                    _merge_movie_fields(existing, m)
                                all_movies[m.id] = m
                            cat_name = CATEGORY_NAME_MAP.get(cid, "?")
                            logger.info("[yutu] Category %s (%s) page %s: %s items", cid, cat_name, page, len(items))
                            break
                        except Exception as e:
                            logger.warning("[yutu] Category %s page %s attempt %s failed: %s", cid, page, attempt+1, e)
                            if attempt < retries - 1:
                                await asyncio.sleep(2)
                            else:
                                break

        if all_movies:
            self._movies = all_movies
            self._last_fetch = now
            logger.info("[yutu] Total movies fetched: %s", len(all_movies))
        return list(self._movies.values())

    def _parse_category_page(self, html: str, cid: int) -> List[Movie]:
        """Parse a category list page and return Movie objects with basic info."""
        ul_content = None

        # Find the content after stui-pannel-bd
        stui_match = re.search(
            r'<div class="stui-pannel[^>]*>.*?<div class="stui-pannel-bd[^>]*>(.*?)</div>\s*</div>\s*</div>',
            html, re.DOTALL
        )
        if stui_match:
            uls = re.findall(r'<ul>(.*?)</ul>', stui_match.group(1), re.DOTALL)
            for ul in uls:
                if re.search(r'vod/detail/id/', ul):
                    ul_content = ul
                    break

        if not ul_content:
            all_uls = re.findall(r'<ul>(.*?)</ul>', html, re.DOTALL)
            for ul in all_uls:
                if re.search(r'class="videoName"', ul):
                    ul_content = ul
                    break

        if not ul_content:
            return []

        # Match movie items - robust regex
        item_matches = re.findall(
            r'<a[^>]*class="[^"]*videoName[^"]*"[^>]*'
            r'href="/index\.php/vod/detail/id/(\d+)\.html"[^>]*>'
            r'(?:<[^>]+>)?([^<]+)</a>',
            ul_content, re.DOTALL
        )

        if not item_matches:
            # Last resort: any detail link with visible text not "点击进入"
            all_links = re.findall(
                r'<a[^>]*href="/index\.php/vod/detail/id/(\d+)\.html"[^>]*>([^<]+)</a>',
                ul_content
            )
            item_matches = [
                (vid, title.strip()) for vid, title in all_links
                if title.strip() not in ('\u70b9\u51fb\u8fdb\u5165', '')
            ]
            if not item_matches:
                return []

        # Extract poster images in order
        all_imgs = re.findall(r'<img[^>]*src="([^"]+)"', ul_content)

        notes = re.findall(r'<span class="note">([^<]*)</span>', ul_content)
        cat_matches = re.findall(r'<span class="category">([^<]+)</span>', ul_content)

        # Try to get category name from page title first
        title_match = re.search(r'<title>([^<]+)</title>', html)
        guessed_cat = CATEGORY_NAME_MAP.get(cid, f"\u5206\u7c7b{cid}")
        if title_match:
            title_text = title_match.group(1)
            # Title format: "{catname}\u6570\u636e\u5217\u8868-\u7b2cX\u9875-\u7389\u5154\u8d44\u6e90\u7f51"
            page_title_cat = title_text.split('\u6570\u636e\u5217\u8868')[0].split('\u7b2c')[0].strip()
            if page_title_cat:
                guessed_cat = page_title_cat
        category_name = guessed_cat

        result = []
        for i, (vid, title) in enumerate(item_matches):
            title = title.strip()
            if not title:
                continue
            note = notes[i].strip() if i < len(notes) else "" if notes else ""

            poster = ""
            if i < len(all_imgs):
                p = all_imgs[i].strip()
                if p and not p.endswith('huo.gif'):
                    if p.startswith("//"):
                        p = "https:" + p
                    elif not p.startswith("http"):
                        p = self.base_url + "/" + p.lstrip("/")
                    poster = p

            mid = f"yutu_{vid}"
            result.append(Movie(
                id=mid,
                title=title,
                description="",
                posterUrl=poster,
                type=category_name,
                region="",
                year=2026,
                genre=[],
                director="",
                actors=[],
                episodeTotal=0,
                episodeUpdated=0,
                episodeTag="",
                hotTag=False,
                rating=0.0,
                tags="",
                source="\u7389\u5154\u8d44\u6e90",
                sourceAvatar="",
                introduction="",
            ))
        return result

    async def fetch_detail(self, movie_id: str) -> Optional[Movie]:
        if not movie_id.startswith("yutu_"):
            return None
        vid = movie_id.replace("yutu_", "")
        url = f"{self.base_url}/index.php/vod/detail/id/{vid}.html"

        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                resp = await client.get(
                    url,
                    headers={**self._headers, "referer": f"{self.base_url}/"},
                )
                resp.raise_for_status()
                html = resp.text

                # Poster from left section
                poster_url = ""
                pm = re.search(r'<div class="left">\s*<img[^>]*src="([^"]+)"', html, re.DOTALL)
                if pm:
                    poster_url = pm.group(1)

                if poster_url:
                    if poster_url.startswith("//"):
                        poster_url = "https:" + poster_url
                    elif not poster_url.startswith("http"):
                        poster_url = self.base_url + "/" + poster_url.lstrip("/")

                # Detail from right panel
                detail_match = re.search(
                    r'<div class="right">(.*?)</div>', html, re.DOTALL)
                director = ""
                actors: List[str] = []
                region = ""
                year = 0

                if detail_match:
                    dh = detail_match.group(1)
                    pairs = re.findall(r'<p>([^：<:]*)[：:]\s*([^<]*)</p>', dh)
                    for key, val in pairs:
                        val = val.strip()
                        if '\u5bfc\u6f14' in key:
                            director = val
                        elif '\u6f14\u5458' in key:
                            actors = [x.strip() for x in val.split("/") if x.strip()]
                            if not actors:
                                actors = [x.strip() for x in re.split(r'[\s,\u3000]+', val) if x.strip()]
                        elif '\u5730\u533a' in key:
                            region = val
                        elif '\u4e0a\u6620' in key or '\u5e74\u4efd' in key:
                            try:
                                year = int(val)
                            except ValueError:
                                pass

                # Introduction from meta description
                introduction = ""
                desc_match = re.search(r'<meta[^>]*name="description"[^>]*content="([^"]*)"', html)
                if desc_match:
                    introduction = desc_match.group(1).strip()[:200]

                # Play URLs from detail page
                play_matches = re.findall(
                    r'<font[^>]*color="?red"?[^>]*>([^$]+)\$([^<]+)</font>',
                    html
                )

                episode_total = len(play_matches)
                episode_play_urls: Dict[int, str] = {}
                for ep_idx, (ep_title, ep_url) in enumerate(play_matches):
                    ep_url = ep_url.strip()
                    if ep_url:
                        ep_num_match = re.search(r'(\d+)', ep_title)
                        ep_num = int(ep_num_match.group(1)) if ep_num_match else (ep_idx + 1)
                        episode_play_urls[ep_num] = ep_url

                episode_tag = f"{episode_total}\u96c6" if episode_total > 1 else "1\u96c6" if episode_total == 1 else ""

                existing = self._movies.get(movie_id)
                if existing:
                    existing.posterUrl = poster_url or existing.posterUrl
                    existing.director = director or existing.director
                    existing.actors = actors or existing.actors
                    existing.region = region or existing.region
                    existing.year = year or existing.year
                    existing.introduction = introduction or existing.introduction
                    existing.episodeTotal = episode_total
                    existing.episodeUpdated = episode_total
                    existing.episodeTag = episode_tag
                    existing.playUrls = episode_play_urls
                    return existing

                title = existing.title if existing else ""
                return Movie(
                    id=movie_id,
                    title=title,
                    posterUrl=poster_url,
                    type="",
                    director=director,
                    actors=actors,
                    region=region,
                    year=year,
                    introduction=introduction,
                    episodeTotal=episode_total,
                    episodeUpdated=episode_total,
                    episodeTag=episode_tag,
                    playUrls=episode_play_urls,
                )

        except Exception as e:
            logger.warning("[yutu] Detail fetch failed for %s: %s", movie_id, e)
            return None

    async def batch_fetch_details(self, limit: int = 500, concurrency: int = 8) -> int:
        movie_ids = list(self._movies.keys())
        movie_ids = movie_ids[:min(limit, 9999)]
        if not movie_ids:
            return 0
        
        # Incremental: only fetch details for movies without poster or play URLs
        need_detail = []
        for mid in movie_ids:
            m = self._movies.get(mid)
            if m and m.playUrls:  # Still fetch detail for movies without real poster
                continue  # Already has good data
            need_detail.append(mid)
        
        if not need_detail:
            logger.info("[yutu] All %s movies already have details, skipping detail fetch", len(movie_ids))
            return 0

        logger.info("[yutu] Fetching details for %s/%s movies (skipping %s with existing data)", 
                     len(need_detail), len(movie_ids), len(movie_ids) - len(need_detail))

        sem = asyncio.Semaphore(concurrency)

        async def fetch_one(mid: str):
            async with sem:
                try:
                    await self.fetch_detail(mid)
                    return True
                except Exception:
                    return False

        tasks = [fetch_one(mid) for mid in need_detail]
        results = await asyncio.gather(*tasks)
        success = sum(1 for r in results if r)
        logger.info("[yutu] Batch detail fetch: %s/%s success", success, len(need_detail))
        return success

    async def get_play_url_for_episode(self, movie_id: str, episode: int = 1) -> Optional[str]:
        m = await self.fetch_detail(movie_id)
        if m and m.playUrls and episode in m.playUrls:
            return m.playUrls[episode]
        return None

    def get_movie_by_id(self, movie_id: str) -> Optional[Movie]:
        return self._movies.get(movie_id)


class DataSourceManager:
    def __init__(self):
        self.sources: List[YutuHtmlSource] = []

    def add_source(self, name: str, base_url: str, enabled: bool = True):
        self.sources.append(YutuHtmlSource(base_url))

    async def refresh_all(self):
        for src in self.sources:
            try:
                await src.fetch_movies(force=True)
                await src.batch_fetch_details(limit=500, concurrency=8)
            except Exception as e:
                logger.warning("Error refreshing source: %s", e)

    def search_movies(self, keyword: str = "", type_filter: str = "all",
                      genre: str = "all", region: str = "all",
                      year: str = "all", sort: str = "\u6700\u70ed") -> List[Movie]:
        results: List[Movie] = []
        for src in self.sources:
            results.extend(src._movies.values())

        if keyword:
            kw = keyword.lower()
            results = [m for m in results if kw in m.title.lower()]

        type_map = {
            "all": None,
            "\u7535\u89c6\u5267": "\u7535\u89c6\u5267",
            "\u7535\u5f71": "\u7535\u5f71",
            "\u7efc\u827a": "\u7efc\u827a",
            "\u52a8\u6f2b": "\u52a8\u6f2b",
            "\u77ed\u5267": "\u77ed\u5267",
        }
        if type_filter in type_map and type_map[type_filter]:
            results = [m for m in results if type_map[type_filter] in m.type]

        if region != "all":
            results = [m for m in results if region in m.region]

        if year != "all":
            try:
                y = int(year)
                results = [m for m in results if m.year == y]
            except ValueError:
                pass

        results = list({m.id: m for m in results}.values())

        sort_funcs = {
            "\u6700\u70ed": lambda m: (1 if m.hotTag else 0, m.rating),
            "\u8bc4\u5206": lambda m: m.rating,
            "\u6700\u8fd1\u66f4\u65b0": lambda m: m.episodeUpdated,
            "\u6700\u65b0\u4e0a\u7ebf": lambda m: m.year,
        }
        sf = sort_funcs.get(sort)
        if sf:
            results.sort(key=sf, reverse=True)

        return results

    def get_movie_by_id(self, movie_id: str) -> Optional[Movie]:
        for src in self.sources:
            m = src.get_movie_by_id(movie_id)
            if m:
                return m
        return None

    async def get_play_url_for_episode(self, movie_id: str, episode: int = 1) -> Optional[str]:
        for src in self.sources:
            url = await src.get_play_url_for_episode(movie_id, episode)
            if url:
                return url
        return None
