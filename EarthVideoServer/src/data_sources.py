import asyncio
import html
import re
import time
import json
import urllib.parse
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


def _extract_date_from_url(url: str) -> Optional[str]:
    """Extract a publish date from a media URL like .../20260821/xxx.m3u8.

    Returns ISO 'YYYY-MM-DD' when a date is found, else None.
    """
    if not url:
        return None
    m = re.search(r'/(\d{4})(\d{2})(\d{2})/', url)
    if m:
        return f"{m.group(1)}-{m.group(2)}-{m.group(3)}"
    m = re.search(r'/(\d{4})-(\d{2})-(\d{2})/', url)
    if m:
        return f"{m.group(1)}-{m.group(2)}-{m.group(3)}"
    return None


def _extract_publish_date(play_urls: Dict[str, Dict[int, str]]) -> Optional[str]:
    """Look through every stored play URL for the newest date."""
    best: Optional[str] = None
    for _sname, eps in (play_urls or {}).items():
        for _ep, url in (eps or {}).items():
            d = _extract_date_from_url(str(url))
            if d and (best is None or d > best):
                best = d
    return best


def _merge_movie_fields(existing: Movie, fresh: Movie) -> Movie:
    """Union existing and fresh: truthy fields from either side win."""
    for field in [
        'posterUrl', 'description', 'director', 'actors', 'region',
        'year', 'introduction', 'episodeTotal', 'episodeUpdated',
        'episodeTag', 'type', 'genre', 'tags', 'rating', 'hotTag',
        'highlightTitle', 'publishDate',
    ]:
        existing_val = getattr(existing, field, None)
        fresh_val = getattr(fresh, field, None)
        if existing_val and _is_truthy_field(existing_val):
            setattr(fresh, field, existing_val)
        elif fresh_val and _is_truthy_field(fresh_val):
            setattr(existing, field, fresh_val)

    existing_play = getattr(existing, 'playUrls', None) or {}
    fresh_play = getattr(fresh, 'playUrls', None) or {}
    if existing_play or fresh_play:
        merged: Dict[str, Dict[int, str]] = dict(existing_play)
        for sname, eps in fresh_play.items():
            bucket = dict(merged.get(sname) or {})
            bucket.update(eps or {})
            merged[sname] = bucket
        existing.playUrls = merged
        fresh.playUrls = merged
    return fresh


def _is_truthy_field(v) -> bool:
    """Treat 0 and empty containers as missing for merge purposes."""
    if v is None:
        return False
    if isinstance(v, (list, dict, str)) and len(v) == 0:
        return False
    if isinstance(v, int) and v == 0:
        return False
    return True


class YutuHtmlSource:
    SOURCE_NAME = "\u7389\u5154\u6e90"

    def __init__(self, base_url: str = "https://yutuzy10.com", proxy_url: str = ""):
        self.base_url = base_url.rstrip("/")
        self.proxy_url = proxy_url.strip()
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

    @property
    def _proxy_kwargs(self) -> dict:
        """Return httpx proxy kwargs when proxy_url is configured."""
        if self.proxy_url:
            return {"proxies": self.proxy_url}
        return {}

    async def fetch_movies(self, force: bool = False) -> List[Movie]:
        now = time.time()
        if not force and self._movies and (now - self._last_fetch) < self._fetch_interval:
            return list(self._movies.values())

        all_movies: Dict[str, Movie] = {}
        async with httpx.AsyncClient(
            timeout=30.0,
            limits=httpx.Limits(max_keepalive_connections=2),
            **self._proxy_kwargs,
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
                source=self.SOURCE_NAME,
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
            async with httpx.AsyncClient(timeout=30.0, **self._proxy_kwargs) as client:
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
                play_urls = {
                    self.SOURCE_NAME: episode_play_urls,
                } if episode_play_urls else {}

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
                    existing.playUrls = play_urls
                    existing.publishDate = existing.publishDate or _extract_publish_date(play_urls)
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
                    playUrls=play_urls,
                    publishDate=_extract_publish_date(play_urls),
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

    async def get_play_url_for_episode(
        self, movie_id: str, episode: int = 1, source: str = "default"
    ) -> Optional[str]:
        m = await self.fetch_detail(movie_id)
        if m and m.playUrls:
            if source == "default":
                for sname, eps in m.playUrls.items():
                    if episode in eps:
                        return eps[episode]
            elif source in m.playUrls and episode in m.playUrls[source]:
                return m.playUrls[source][episode]
        return None

    def get_movie_by_id(self, movie_id: str) -> Optional[Movie]:
        return self._movies.get(movie_id)


class MoguHtmlSource:
    """Data source for 5o5k.com (蘑菇影视) - maccms-based movie portal.

    URL conventions:
      - List page:    /vodshow/{type_id}--------{page}---.html
      - Detail page:  /voddetail/{id}.html  (carries JSON-LD metadata + source/episode list)
      - Play page:    /vodplay/{id}-{sid}-{nid}.html  (carries URL-encoded m3u8 link)
    """

    SOURCE_PREFIX = "mogu_"
    SOURCE_NAME = "\u8611\u83c7\u5f71\u89c6"

    CATEGORY_IDS = [20, 35, 43, 48, 55]
    CATEGORY_MAP = {
        20: "\u7535\u5f71",
        35: "\u7535\u89c6\u5267",
        43: "\u7efc\u827a",
        48: "\u52a8\u6f2b",
        55: "\u77ed\u5267",
    }

    MAX_PAGES = 2
    EPISODES_TO_PRELOAD = 0

    def __init__(self, base_url: str = "https://www.5o5k.com", proxy_url: str = ""):
        self.base_url = base_url.rstrip("/")
        self.proxy_url = proxy_url.strip()
        self.name = self.SOURCE_NAME
        self._movies: Dict[str, Movie] = {}
        self._last_fetch: float = 0
        self._fetch_interval: float = 300
        self._movie_sources: Dict[str, List[Tuple[int, str, int]]] = {}
        self._headers = {
            "User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/151.0.0.0 Safari/537.36"
            ),
            "accept": (
                "text/html,application/xhtml+xml,application/xml;q=0.9,"
                "image/avif,image/webp,image/apng,*/*;q=0.8"
            ),
            "accept-language": "zh-CN,zh;q=0.9",
            "referer": self.base_url + "/",
        }

    @property
    def _proxy_kwargs(self) -> dict:
        """Return httpx proxy kwargs when proxy_url is configured."""
        if self.proxy_url:
            return {"proxies": self.proxy_url}
        return {}

    @staticmethod
    def source_prefix() -> str:
        return MoguHtmlSource.SOURCE_PREFIX

    def make_movie_id(self, vid: str) -> str:
        return f"{self.SOURCE_PREFIX}{vid}"

    def _parse_vid(self, movie_id: str) -> Optional[str]:
        if not movie_id.startswith(self.SOURCE_PREFIX):
            return None
        return movie_id[len(self.SOURCE_PREFIX):]

    async def fetch_movies(self, force: bool = False) -> List[Movie]:
        now = time.time()
        if not force and self._movies and (now - self._last_fetch) < self._fetch_interval:
            return list(self._movies.values())

        all_movies: Dict[str, Movie] = {}
        async with httpx.AsyncClient(
            timeout=30.0,
            limits=httpx.Limits(max_keepalive_connections=2),
            **self._proxy_kwargs,
        ) as client:
            for cid in self.CATEGORY_IDS:
                for page in range(1, self.MAX_PAGES + 1):
                    retries = 2
                    success = False
                    for attempt in range(retries):
                        try:
                            url = f"{self.base_url}/vodshow/{cid}--------{page}---.html"
                            resp = await client.get(url, headers=self._headers)
                            resp.raise_for_status()
                            items = self._parse_category_page(resp.text, cid)
                            if not items:
                                success = True
                                break
                            for m in items:
                                existing = self._movies.get(m.id)
                                if existing:
                                    _merge_movie_fields(existing, m)
                                all_movies[m.id] = m
                            cat_name = self.CATEGORY_MAP.get(cid, "?")
                            logger.info(
                                "[mogu] Category %s (%s) page %s: %s items",
                                cid, cat_name, page, len(items),
                            )
                            success = True
                            break
                        except Exception as e:
                            logger.warning(
                                "[mogu] Category %s page %s attempt %s failed: %s",
                                cid, page, attempt + 1, e,
                            )
                            if attempt < retries - 1:
                                await asyncio.sleep(2)
                    if not success:
                        break

        if all_movies:
            self._movies = all_movies
            self._last_fetch = now
            logger.info("[mogu] Total movies fetched: %s", len(all_movies))
        return list(self._movies.values())

    def _parse_category_page(self, html: str, cid: int) -> List[Movie]:
        """Parse a vodshow list page and return Movie objects with basic info."""
        from html import unescape as _unescape
        pattern = (
            r'<a[^>]*href="/voddetail/(\d+)\.html"'
            r'[^>]*title="([^"]*)"'
            r'[^>]*class="module-poster-item module-item"'
            r'[^>]*>(.*?)</a>'
        )
        blocks = re.findall(pattern, html, re.DOTALL)
        if not blocks:
            return []

        category_name = self.CATEGORY_MAP.get(cid, f"\u5206\u7c7b{cid}")
        result: List[Movie] = []
        for vid, raw_title, block in blocks:
            title = _unescape(raw_title).strip()
            if not title:
                continue
            poster_match = re.search(r'data-original="([^"]*)"', block)
            poster = poster_match.group(1).strip() if poster_match else ""
            note_match = re.search(r'<div class="module-item-note">([^<]*)</div>', block)
            note = _unescape(note_match.group(1).strip()) if note_match else ""

            mid = self.make_movie_id(vid)
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
                episodeTag=note,
                hotTag=False,
                rating=0.0,
                tags=note,
                source=self.SOURCE_NAME,
                sourceAvatar="",
                introduction="",
            ))
        return result

    async def fetch_detail(self, movie_id: str) -> Optional[Movie]:
        vid = self._parse_vid(movie_id)
        if not vid:
            return None
        url = f"{self.base_url}/voddetail/{vid}.html"

        try:
            async with httpx.AsyncClient(
                timeout=30.0,
                limits=httpx.Limits(max_keepalive_connections=2),
                **self._proxy_kwargs,
            ) as client:
                resp = await client.get(
                    url,
                    headers={**self._headers, "referer": f"{self.base_url}/"},
                )
                resp.raise_for_status()
                html = resp.text
                movie = self._parse_detail_page(html, vid)
                if not movie:
                    return None

                # Merge into existing (in cache) so all known fields accumulate.
                existing = self._movies.get(movie_id)
                if existing:
                    _merge_movie_fields(existing, movie)
                    movie = existing
                movie.playUrls = movie.playUrls or {}

                # Derive publish date from the media URL path (e.g. /20260821/...)
                if not movie.publishDate:
                    movie.publishDate = _extract_publish_date(movie.playUrls)

                # Preload the first few episode URLs into the canonical movie.
                sources = self._movie_sources.get(movie_id) or []
                ep_total = movie.episodeTotal or 1
                if sources and ep_total >= 1:
                    for sid, sname, _ in sources:
                        movie.playUrls.setdefault(sname, {})
                    best_sid, best_name, _ = max(sources, key=lambda s: s[2])
                    for ep in range(1, min(self.EPISODES_TO_PRELOAD, ep_total) + 1):
                        url_ep = await self._fetch_play_url(client, vid, best_sid, ep)
                        if url_ep:
                            movie.playUrls.setdefault(best_name, {})[ep] = url_ep

                self._movies[movie_id] = movie
                return movie
        except Exception as e:
            logger.warning("[mogu] Detail fetch failed for %s: %s", movie_id, e)
            return None

    def _parse_detail_page(self, html: str, vid: str) -> Optional[Movie]:
        """Extract metadata + source/episode list from a detail page."""
        mid = self.make_movie_id(vid)
        existing = self._movies.get(mid)
        title = existing.title if existing else ""

        name = ""
        poster = ""
        year_val = 0
        region = ""
        genre = ""
        director = ""
        actors: List[str] = []
        introduction = ""
        episode_total = 0
        rating = 0.0

        ld_match = re.search(
            r'<script type="application/ld\+json">(.*?)</script>', html, re.DOTALL
        )
        if ld_match:
            try:
                ld_data = json.loads(ld_match.group(1))
                graph = ld_data.get("@graph", []) if isinstance(ld_data, dict) else []
                for item in graph:
                    if item.get("@type") in ("TVSeries", "Movie"):
                        name = item.get("name") or name
                        img = item.get("image")
                        if isinstance(img, str):
                            poster = img
                        elif isinstance(img, dict):
                            poster = img.get("url") or poster
                        desc = item.get("description") or ""
                        if desc and not introduction:
                            introduction = desc.strip()[:600]
                        actors = [
                            a.get("name") for a in item.get("actor", [])
                            if isinstance(a, dict) and a.get("name")
                        ]
                        directors = [
                            d.get("name") for d in item.get("director", [])
                            if isinstance(d, dict) and d.get("name")
                        ]
                        director = " ".join(directors)
                        yr = item.get("datePublished") or ""
                        m_year = re.match(r"(\d{4})", str(yr))
                        if m_year:
                            year_val = int(m_year.group(1))
                        origin = item.get("countryOfOrigin") or {}
                        if isinstance(origin, dict):
                            region = origin.get("name") or region
                        elif isinstance(origin, str):
                            region = origin
                        genre = item.get("genre") or genre
                        ne = item.get("numberOfEpisodes") or 0
                        if ne:
                            episode_total = int(ne)
                        agg = item.get("aggregateRating") or {}
                        if isinstance(agg, dict) and agg.get("ratingValue"):
                            try:
                                rating = float(agg.get("ratingValue"))
                            except (TypeError, ValueError):
                                pass
                        break
            except (json.JSONDecodeError, ValueError):
                pass

        if not introduction:
            meta_desc = re.search(
                r'<meta[^>]*name="description"[^>]*content="([^"]*)"', html
            )
            if meta_desc:
                introduction = meta_desc.group(1).strip()[:600]

        if not poster:
            pm = re.search(
                r'<div class="module-info-poster".*?<img[^>]*data-original="([^"]+)"',
                html, re.DOTALL,
            )
            if pm:
                poster = pm.group(1)

        if not name:
            h1 = re.search(r'<h1[^>]*>([^<]+)</h1>', html)
            if h1:
                name = h1.group(1).strip()
        title = (name or title).strip()

        tab_blocks = re.findall(
            r'<div[^>]*class="module-tab-item tab-item[^"]*"[^>]*>'
            r'.*?data-dropdown-value="([^"]+)".*?<small>(\d+)</small>',
            html, re.DOTALL,
        )
        tab_counts = {name: int(c) for name, c in tab_blocks}

        src_pattern = (
            r'<div[^>]*class="module-tab-item tab-item[^"]*"'
            r'[^>]*data-dropdown-value="([^"]+)"'
        )
        src_names = re.findall(src_pattern, html)

        sources: List[Tuple[int, str, int]] = []
        for idx, sname in enumerate(src_names, start=1):
            count = tab_counts.get(sname, 0)
            sources.append((idx, sname, count))

        ep_pattern = (
            r'<a[^>]*class="module-play-list-link[^"]*"'
            r'[^>]*href="/vodplay/(\d+)-(\d+)-(\d+)\.html"'
            r'[^>]*>\s*<span>([^<]+)</span>'
        )
        ep_matches = re.findall(ep_pattern, html)

        max_nid = 0
        for _, _, nid_str, _ in ep_matches:
            try:
                max_nid = max(max_nid, int(nid_str))
            except ValueError:
                pass
        if max_nid > episode_total:
            episode_total = max_nid
        if not episode_total and sources:
            episode_total = max((s[2] for s in sources), default=0)

        if not episode_total:
            episode_total = 1

        self._movie_sources[mid] = sources

        from html import unescape as _unescape

        title = _unescape(title)
        introduction = _unescape(introduction)
        director = _unescape(director)
        region = _unescape(region)
        genre = _unescape(genre)
        actors = [_unescape(a) for a in actors]

        episode_tag = (
            f"{episode_total}\u96c6"
            if episode_total > 1
            else "1\u96c6" if episode_total == 1 else ""
        )

        return Movie(
            id=mid,
            title=title,
            description="",
            posterUrl=poster,
            type=existing.type if existing else "",
            region=region,
            year=year_val,
            genre=[genre] if genre else [],
            director=director,
            actors=actors,
            episodeTotal=episode_total,
            episodeUpdated=episode_total,
            episodeTag=episode_tag,
            hotTag=False,
            rating=rating,
            tags="",
            source=self.SOURCE_NAME,
            sourceAvatar="",
            introduction=introduction,
        )

    async def _fetch_play_url(
        self,
        client: httpx.AsyncClient,
        vid: str,
        sid: int,
        nid: int,
    ) -> Optional[str]:
        """Fetch a play page and extract the actual m3u8 URL from player_aaaa.

        The play page contains a JavaScript snippet like:
          var player_aaaa = { "url": "...", "encrypt": 1, ... }

        Handles optional whitespace around '=' and nested braces in the JSON.
        """
        url = f"{self.base_url}/vodplay/{vid}-{sid}-{nid}.html"
        try:
            resp = await client.get(
                url,
                headers={**self._headers, "referer": f"{self.base_url}/voddetail/{vid}.html"},
            )
            resp.raise_for_status()
            html_text = resp.text

            # Find player_aaaa= or player_aaaa = (with optional whitespace)
            idx = html_text.find("player_aaaa=")
            if idx == -1:
                # Try with space: "player_aaaa ="
                idx = html_text.find("player_aaaa =")
            if idx == -1:
                return None

            # Skip past "player_aaaa" and any whitespace and '=' to find '{'
            brace_start = html_text.find("{", idx)
            if brace_start == -1:
                return None

            # Extract the JSON object accounting for nested braces.
            depth = 0
            end = brace_start
            for i, ch in enumerate(html_text[brace_start:]):
                if ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                    if depth == 0:
                        end = brace_start + i + 1
                        break
            if depth != 0:
                return None

            raw_json = html_text[brace_start:end]
            data = json.loads(raw_json)
            raw = data.get("url") or ""
            if not raw:
                return None

            encrypt = data.get("encrypt")
            if encrypt == 1:
                return urllib.parse.unquote(raw)
            if encrypt == 2:
                import base64
                return urllib.parse.unquote(base64.b64decode(raw).decode("utf-8"))
            return raw
        except Exception as e:
            logger.warning(
                "[mogu] play fetch failed vid=%s sid=%s nid=%s: %s",
                vid, sid, nid, e,
            )
            return None

    async def batch_fetch_details(self, limit: int = 500, concurrency: int = 6) -> int:
        movie_ids = list(self._movies.keys())[: min(limit, 9999)]
        if not movie_ids:
            return 0

        need_detail = []
        for mid in movie_ids:
            m = self._movies.get(mid)
            if m and m.posterUrl and m.introduction and m.actors:
                continue
            need_detail.append(mid)

        if not need_detail:
            logger.info(
                "[mogu] All %s movies already have details, skipping",
                len(movie_ids),
            )
            return 0

        logger.info(
            "[mogu] Fetching details for %s/%s movies",
            len(need_detail), len(movie_ids),
        )

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
        logger.info("[mogu] Batch detail fetch: %s/%s success", success, len(need_detail))
        return success

    def _best_source_for_episode(
        self, movie_id: str, episode: int
    ) -> Optional[int]:
        """Pick the source (sid) that has the requested episode."""
        sources = self._movie_sources.get(movie_id) or []
        candidates = [s for s in sources if s[2] >= episode]
        if not candidates:
            return None
        return max(candidates, key=lambda s: s[2])[0]

    async def _ensure_sources(self, movie_id: str) -> None:
        """Make sure _movie_sources is populated for a Mogu movie.

        The mapping is per-process; after a restart it has to be rebuilt by
        re-parsing the detail page.
        """
        if self._movie_sources.get(movie_id):
            return
        vid = self._parse_vid(movie_id)
        if not vid:
            return
        url = f"{self.base_url}/voddetail/{vid}.html"
        try:
            async with httpx.AsyncClient(
                timeout=30.0,
                limits=httpx.Limits(max_keepalive_connections=2),
                **self._proxy_kwargs,
            ) as client:
                resp = await client.get(
                    url,
                    headers={**self._headers, "referer": f"{self.base_url}/"},
                )
                resp.raise_for_status()
                self._parse_detail_page(resp.text, vid)
        except Exception as e:
            logger.warning("[mogu] lazy source load failed for %s: %s", movie_id, e)

    async def get_play_url_for_episode(
        self, movie_id: str, episode: int = 1, source: str = "default"
    ) -> Optional[str]:
        m = self._movies.get(movie_id)
        if m and m.playUrls:
            if source == "default":
                for sname, eps in m.playUrls.items():
                    if episode in eps:
                        return eps[episode]
            elif source in m.playUrls and episode in m.playUrls[source]:
                return m.playUrls[source][episode]

        await self._ensure_sources(movie_id)

        vid = self._parse_vid(movie_id)
        if not vid:
            return None

        sources = self._movie_sources.get(movie_id) or []
        if source != "default":
            candidates = [s for s in sources if s[1] == source]
            if not candidates:
                return None
            sid = max(candidates, key=lambda s: s[2])[0]
            fallback_name = source
        else:
            sid = self._best_source_for_episode(movie_id, episode)
            if not sid:
                return None
            fallback_name = next(
                (sname for s_id, sname, _ in sources if s_id == sid),
                self.SOURCE_NAME,
            )

        async with httpx.AsyncClient(
            timeout=30.0,
            limits=httpx.Limits(max_keepalive_connections=2),
            **self._proxy_kwargs,
        ) as client:
            url = await self._fetch_play_url(client, vid, sid, episode)

        if url and m is not None:
            m.playUrls = m.playUrls or {}
            m.playUrls.setdefault(fallback_name, {})[episode] = url
            if not m.publishDate:
                m.publishDate = _extract_publish_date(m.playUrls)
        return url

    def get_movie_by_id(self, movie_id: str) -> Optional[Movie]:
        return self._movies.get(movie_id)


class DataSourceManager:
    def __init__(self, proxy_url: str = ""):
        self.proxy_url = proxy_url.strip()
        self.sources: List = []
        self._by_prefix: Dict[str, object] = {}
        self._by_name: Dict[str, object] = {}
        self._default_source = None

    def add_source(self, name: str, base_url: str, enabled: bool = True):
        kind = (name or "").lower()
        if kind in ("mogu", "5o5k"):
            src = MoguHtmlSource(base_url, proxy_url=self.proxy_url)
        else:
            src = YutuHtmlSource(base_url, proxy_url=self.proxy_url)
        self.sources.append(src)
        prefix = getattr(src, "SOURCE_PREFIX", None)
        if prefix:
            self._by_prefix[prefix] = src
        else:
            # First non-prefixed source (e.g. Yutu) becomes the default.
            if self._default_source is None:
                self._default_source = src
        sname = getattr(src, "name", None) or getattr(src, "SOURCE_NAME", None)
        if sname:
            self._by_name[sname] = src

    def find_source_for_movie(self, movie_id: str):
        """Return the source that owns this movie_id (based on prefix)."""
        # Check prefix-matched sources first.
        for prefix, src in self._by_prefix.items():
            if movie_id.startswith(prefix):
                return src
        # Fallback to the default (non-prefixed) source.
        if self._default_source:
            return self._default_source
        return None

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
            "\u6700\u8fd1\u66f4\u65b0": lambda m: (m.publishDate or "", m.episodeUpdated),
            "\u6700\u65b0\u4e0a\u7ebf": lambda m: (m.publishDate or "", m.episodeUpdated),
            "\u6700\u65b0": lambda m: (m.publishDate or "", m.episodeUpdated),
        }
        sf = sort_funcs.get(sort)
        if sf:
            results.sort(key=sf, reverse=True)
        else:
            # Default: newest first based on publish date
            results.sort(key=lambda m: (m.publishDate or ""), reverse=True)

        return results

    def get_movie_by_id(self, movie_id: str) -> Optional[Movie]:
        for src in self.sources:
            m = src.get_movie_by_id(movie_id)
            if m:
                return m
        return None

    async def get_play_url_for_episode(
        self, movie_id: str, episode: int = 1, source: str = "default"
    ) -> Optional[str]:
        # Route straight to the source that owns this movie id (prefix match),
        # so e.g. "mogu_*" never hits the yutu source first.
        owner = self.find_source_for_movie(movie_id)
        if owner is not None:
            url = await owner.get_play_url_for_episode(movie_id, episode, source)
            if url:
                return url
        # Fallback: try every source.
        for src in self.sources:
            url = await src.get_play_url_for_episode(movie_id, episode, source)
            if url:
                return url
        return None
