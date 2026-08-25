"""Offline unit tests for the multi-source architecture.

Goals:
  * DataSourceManager routes movie ids to the correct source by prefix
  * YutuHtmlSource parses a category page (maccms/v10 layout)
  * MoguHtmlSource parses a category page and a detail page
  * MoguHtmlSource.get_play_url_for_episode handles cached URLs and source selection
  * MoguHtmlSource._parse_detail_page must populate episodeTag (regression for pydantic kwarg typo)
  * DataSourceManager.search_movies merges across all sources and supports filters
  * DataSourceManager.get_play_url_for_episode only calls the source that owns the movie id
"""

import asyncio
import pytest

from src.data_sources import (
    DataSourceManager,
    MoguHtmlSource,
    YutuHtmlSource,
    _merge_movie_fields,
)
from src.models import Movie


# --------------------------------------------------------------------------- #
# Fixture HTML samples (synthetic, do not contain any real content)
# --------------------------------------------------------------------------- #

SAMPLE_TITLE = "Sample Movie Title"


def _yutu_category_html() -> str:
    return f"""<!doctype html>
<html><head><title>电影数据列表-第1页-玉兔资源网</title></head>
<body>
  <div class="stui-pannel">
    <div class="stui-pannel-bd">
      <ul>
        <li>
          <a class="videoName" href="/index.php/vod/detail/id/1001.html">{SAMPLE_TITLE}</a>
          <span class="note">1集</span>
          <img src="https://cdn.example.com/posters/1001.jpg">
        </li>
        <li>
          <a class="videoName" href="/index.php/vod/detail/id/1002.html">Other Movie</a>
          <span class="note">5集</span>
          <img src="https://cdn.example.com/posters/1002.jpg">
        </li>
      </ul>
    </div>
  </div>
</body></html>"""


def _mogu_category_html() -> str:
    return """<!doctype html>
<html><body>
  <a href="/voddetail/2001.html" title="Mogu One" class="module-poster-item module-item">
    <img data-original="https://cdn.example.com/mogu/2001.jpg">
    <div class="module-item-note">1集</div>
  </a>
  <a href="/voddetail/2002.html" title="Mogu Two" class="module-poster-item module-item">
    <img data-original="https://cdn.example.com/mogu/2002.jpg">
    <div class="module-item-note">更新至3集</div>
  </a>
</body></html>"""


def _mogu_detail_html() -> str:
    ld = {
        "@context": "https://schema.org",
        "@graph": [
            {
                "@type": "TVSeries",
                "name": "Mogu One",
                "image": "https://cdn.example.com/mogu/2001-poster.jpg",
                "description": "A sample mogu show.",
                "actor": [{"name": "Actor A"}, {"name": "Actor B"}],
                "director": [{"name": "Director X"}],
                "datePublished": "2024-05-01",
                "countryOfOrigin": {"name": "中国大陆"},
                "genre": "剧情",
                "numberOfEpisodes": 3,
                "aggregateRating": {"ratingValue": 8.1},
            }
        ],
    }
    import json as _json
    return f"""<!doctype html>
<html><head><meta name="description" content="Mogu One - synopsis here."></head>
<body>
  <h1>Mogu One</h1>
  <script type="application/ld+json">{_json.dumps(ld, ensure_ascii=False)}</script>
  <div class="module-info-poster">
    <img data-original="https://cdn.example.com/mogu/2001-fallback.jpg">
  </div>
  <div class="module-tab-item tab-item" data-dropdown-value="线路A">
    <small>3</small>
  </div>
  <div class="module-tab-item tab-item" data-dropdown-value="线路B">
    <small>3</small>
  </div>
  <a class="module-play-list-link" href="/vodplay/2001-1-1.html"><span>第01集</span></a>
  <a class="module-play-list-link" href="/vodplay/2001-1-2.html"><span>第02集</span></a>
  <a class="module-play-list-link" href="/vodplay/2001-1-3.html"><span>第03集</span></a>
  <a class="module-play-list-link" href="/vodplay/2001-2-1.html"><span>第01集</span></a>
</body></html>"""


# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #

def _run(coro):
    return asyncio.get_event_loop().run_until_complete(coro)


def _make_movie(mid: str, **kwargs) -> Movie:
    return Movie(
        id=mid,
        title=kwargs.pop("title", "Sample"),
        posterUrl=kwargs.pop("posterUrl", "https://x/p.jpg"),
        type=kwargs.pop("type", "电影"),
        **kwargs,
    )


# --------------------------------------------------------------------------- #
# DataSourceManager registration & routing
# --------------------------------------------------------------------------- #

class TestDataSourceManagerRegistration:
    def setup_method(self):
        self.mgr = DataSourceManager()
        self.mgr.add_source("yutu", "https://yutuzy10.com")
        self.mgr.add_source("mogu", "https://www.5o5k.com")

    def test_both_sources_registered(self):
        assert len(self.mgr.sources) == 2

    def test_yutu_is_default_source(self):
        # Yutu has no SOURCE_PREFIX, so it becomes the manager's default.
        assert self.mgr._default_source is self.mgr.sources[0]
        assert isinstance(self.mgr._default_source, YutuHtmlSource)

    def test_mogu_routed_by_prefix(self):
        assert isinstance(self.mgr._by_prefix["mogu_"], MoguHtmlSource)

    def test_find_source_for_movie_mogu(self):
        src = self.mgr.find_source_for_movie("mogu_2001")
        assert isinstance(src, MoguHtmlSource)

    def test_find_source_for_movie_yutu_falls_back(self):
        src = self.mgr.find_source_for_movie("yutu_1001")
        assert isinstance(src, YutuHtmlSource)
        # It must NOT be the mogu source.
        assert src is self.mgr._default_source

    def test_find_source_unknown_prefix_falls_back_to_default(self):
        # A movie id whose prefix nobody claims should still resolve to default.
        src = self.mgr.find_source_for_movie("foo_1")
        assert src is self.mgr._default_source


# --------------------------------------------------------------------------- #
# DataSourceManager.search_movies merges across sources
# --------------------------------------------------------------------------- #

class TestDataSourceManagerSearch:
    def setup_method(self):
        self.mgr = DataSourceManager()
        self.mgr.add_source("yutu", "https://yutuzy10.com")
        self.mgr.add_source("mogu", "https://www.5o5k.com")

        self.yutu_src = self.mgr.sources[0]
        self.mogu_src = self.mgr.sources[1]

        # Seed two yutu movies + two mogu movies (no network).
        self.yutu_src._movies = {
            "yutu_1": _make_movie("yutu_1", title="Hello Yutu", type="电影", rating=7.5),
            "yutu_2": _make_movie("yutu_2", title="World Yutu", type="电视剧", rating=6.0),
        }
        self.mogu_src._movies = {
            "mogu_1": _make_movie("mogu_1", title="Hello Mogu", type="电影", rating=8.0),
            "mogu_2": _make_movie("mogu_2", title="Other Mogu", type="综艺", rating=5.5),
        }

    def test_search_returns_all_movies_across_sources(self):
        results = self.mgr.search_movies()
        ids = {m.id for m in results}
        assert ids == {"yutu_1", "yutu_2", "mogu_1", "mogu_2"}

    def test_keyword_search_spans_sources(self):
        results = self.mgr.search_movies(keyword="hello")
        ids = {m.id for m in results}
        # Lower-cased match crosses both sources
        assert ids == {"yutu_1", "mogu_1"}

    def test_type_filter_applies(self):
        results = self.mgr.search_movies(type_filter="电视剧")
        ids = {m.id for m in results}
        assert ids == {"yutu_2"}

    def test_sort_by_rating_descending(self):
        results = self.mgr.search_movies(sort="评分")
        ratings = [m.rating for m in results]
        assert ratings == sorted(ratings, reverse=True)


# --------------------------------------------------------------------------- #
# DataSourceManager.get_play_url_for_episode routes to the owning source
# --------------------------------------------------------------------------- #

class TestDataSourceManagerPlayUrlRouting:
    def test_get_play_url_only_calls_owning_source(self):
        mgr = DataSourceManager()
        mgr.add_source("yutu", "https://yutuzy10.com")
        mgr.add_source("mogu", "https://www.5o5k.com")

        yutu = mgr.sources[0]
        mogu = mgr.sources[1]

        yutu_calls = []
        mogu_calls = []

        async def yutu_play(movie_id, episode=1, source="default"):
            yutu_calls.append((movie_id, episode, source))
            return "https://cdn.example.com/yutu.m3u8"

        async def mogu_play(movie_id, episode=1, source="default"):
            mogu_calls.append((movie_id, episode, source))
            return "https://cdn.example.com/mogu.m3u8"

        yutu.get_play_url_for_episode = yutu_play  # type: ignore
        mogu.get_play_url_for_episode = mogu_play  # type: ignore

        url_yutu = _run(mgr.get_play_url_for_episode("yutu_1", 1, "default"))
        url_mogu = _run(mgr.get_play_url_for_episode("mogu_1", 2, "线路A"))

        assert url_yutu == "https://cdn.example.com/yutu.m3u8"
        assert url_mogu == "https://cdn.example.com/mogu.m3u8"
        assert yutu_calls == [("yutu_1", 1, "default")]
        assert mogu_calls == [("mogu_1", 2, "线路A")]


# --------------------------------------------------------------------------- #
# _merge_movie_fields: union semantics
# --------------------------------------------------------------------------- #

class TestMergeMovieFields:
    def test_fresh_fills_existing_blanks(self):
        existing = _make_movie("mogu_1", title="T", type="", director="")
        fresh = _make_movie("mogu_1", title="T", type="电影", director="D")
        merged = _merge_movie_fields(existing, fresh)
        assert merged.director == "D"
        assert merged.type == "电影"

    def test_existing_wins_when_existing_truthy(self):
        existing = _make_movie("mogu_1", title="T", type="电影", director="Existing")
        fresh = _make_movie("mogu_1", title="T", type="电视剧", director="Fresh")
        merged = _merge_movie_fields(existing, fresh)
        assert merged.director == "Existing"
        assert merged.type == "电影"

    def test_play_urls_union(self):
        existing = _make_movie("mogu_1", title="T", playUrls={"A": {1: "u1"}})
        fresh = _make_movie("mogu_1", title="T", playUrls={"B": {1: "u2", 2: "u3"}})
        merged = _merge_movie_fields(existing, fresh)
        assert merged.playUrls == {"A": {1: "u1"}, "B": {1: "u2", 2: "u3"}}

    def test_play_urls_overlap_fresh_wins(self):
        existing = _make_movie("mogu_1", title="T", playUrls={"A": {1: "u1"}})
        fresh = _make_movie("mogu_1", title="T", playUrls={"A": {1: "u2"}})
        merged = _merge_movie_fields(existing, fresh)
        assert merged.playUrls["A"][1] == "u2"


# --------------------------------------------------------------------------- #
# YutuHtmlSource._parse_category_page
# --------------------------------------------------------------------------- #

class TestYutuCategoryParse:
    def setup_method(self):
        self.src = YutuHtmlSource("https://yutuzy10.com")

    def test_parse_extracts_movie_ids_with_yutu_prefix(self):
        movies = self.src._parse_category_page(_yutu_category_html(), 20)
        ids = [m.id for m in movies]
        assert ids == ["yutu_1001", "yutu_1002"]

    def test_parse_sets_source_name(self):
        movies = self.src._parse_category_page(_yutu_category_html(), 20)
        assert all(m.source == "玉兔源" for m in movies)

    def test_parse_infers_category_from_page_title(self):
        movies = self.src._parse_category_page(_yutu_category_html(), 20)
        # Title is "电影数据列表-第1页-..." -> "电影"
        assert movies[0].type == "电影"

    def test_parse_extracts_posters_in_order(self):
        movies = self.src._parse_category_page(_yutu_category_html(), 20)
        assert movies[0].posterUrl == "https://cdn.example.com/posters/1001.jpg"
        assert movies[1].posterUrl == "https://cdn.example.com/posters/1002.jpg"


# --------------------------------------------------------------------------- #
# MoguHtmlSource parsing
# --------------------------------------------------------------------------- #

class TestMoguCategoryParse:
    def setup_method(self):
        self.src = MoguHtmlSource("https://www.5o5k.com")

    def test_parse_extracts_movie_ids_with_mogu_prefix(self):
        movies = self.src._parse_category_page(_mogu_category_html(), 20)
        ids = [m.id for m in movies]
        assert ids == ["mogu_2001", "mogu_2002"]

    def test_parse_maps_category(self):
        movies = self.src._parse_category_page(_mogu_category_html(), 20)
        assert all(m.type == "电影" for m in movies)
        # Category 35 should map to 电视剧
        movies2 = self.src._parse_category_page(_mogu_category_html(), 35)
        assert all(m.type == "电视剧" for m in movies2)

    def test_parse_sets_source_name(self):
        movies = self.src._parse_category_page(_mogu_category_html(), 20)
        assert all(m.source == "蘑菇影视" for m in movies)


class TestMoguDetailParse:
    def setup_method(self):
        self.src = MoguHtmlSource("https://www.5o5k.com")

    def test_parse_populates_metadata_from_jsonld(self):
        m = self.src._parse_detail_page(_mogu_detail_html(), "2001")
        assert m is not None
        assert m.title == "Mogu One"
        assert m.director == "Director X"
        assert m.actors == ["Actor A", "Actor B"]
        assert m.region == "中国大陆"
        assert m.year == 2024
        assert m.introduction == "A sample mogu show."

    def test_parse_populates_episode_total(self):
        m = self.src._parse_detail_page(_mogu_detail_html(), "2001")
        assert m.episodeTotal == 3

    def test_parse_builds_movie_sources_table(self):
        # We need to call _parse_detail_page through the public flow that also
        # pre-populates self._movie_sources.
        self.src._parse_detail_page(_mogu_detail_html(), "2001")
        # The detail page declares 2 source buckets (线路A, 线路B).
        sources = self.src._movie_sources.get("mogu_2001") or []
        names = [s[1] for s in sources]
        assert "线路A" in names
        assert "线路B" in names

    def test_episode_tag_is_set_not_due_to_pydantic_silent_drop(self):
        """Regression: MoguHtmlSource._parse_detail_page previously used the
        kwarg name ``episode_tag`` while Movie expects ``episodeTag``. Pydantic
        silently drops the unknown kwarg, so ``episodeTag`` ended up empty.
        """
        m = self.src._parse_detail_page(_mogu_detail_html(), "2001")
        assert m.episodeTag == "3集"


class TestMoguBestSourceForEpisode:
    def setup_method(self):
        self.src = MoguHtmlSource("https://www.5o5k.com")
        # Simulate a movie whose two source buckets have different coverage.
        self.src._movie_sources["mogu_1"] = [
            (1, "线路A", 5),
            (2, "线路B", 10),
        ]

    def test_picks_source_that_covers_episode(self):
        sid = self.src._best_source_for_episode("mogu_1", 7)
        assert sid == 2  # only 线路B has >= 7 episodes

    def test_picks_largest_among_candidates(self):
        sid = self.src._best_source_for_episode("mogu_1", 3)
        assert sid == 2  # both cover it; tie-break by larger count

    def test_returns_none_when_no_source_covers(self):
        assert self.src._best_source_for_episode("mogu_1", 999) is None
        assert self.src._best_source_for_episode("unknown", 1) is None


class TestMoguMakeMovieId:
    def test_round_trip(self):
        src = MoguHtmlSource("https://www.5o5k.com")
        for vid in ["1", "2001", "12345"]:
            mid = src.make_movie_id(vid)
            assert mid == f"mogu_{vid}"
            assert src._parse_vid(mid) == vid

    def test_parse_vid_rejects_non_mogu_prefix(self):
        src = MoguHtmlSource("https://www.5o5k.com")
        assert src._parse_vid("yutu_1") is None
        assert src._parse_vid("foo") is None


# --------------------------------------------------------------------------- #
# MoguHtmlSource.get_play_url_for_episode: cached URL fast path
# --------------------------------------------------------------------------- #

class TestMoguPlayUrlCached:
    def test_default_returns_first_matching_episode(self):
        src = MoguHtmlSource("https://www.5o5k.com")
        src._movies["mogu_1"] = _make_movie(
            "mogu_1",
            title="T",
            playUrls={"线路A": {1: "u1", 2: "u2"}, "线路B": {1: "v1"}},
        )
        url = _run(src.get_play_url_for_episode("mogu_1", 2, "default"))
        assert url == "u2"

    def test_explicit_source_is_respected(self):
        src = MoguHtmlSource("https://www.5o5k.com")
        src._movies["mogu_1"] = _make_movie(
            "mogu_1",
            title="T",
            playUrls={"线路A": {1: "u1"}, "线路B": {1: "v1"}},
        )
        url = _run(src.get_play_url_for_episode("mogu_1", 1, "线路B"))
        assert url == "v1"

    def test_returns_none_when_cached_miss(self):
        src = MoguHtmlSource("https://www.5o5k.com")
        src._movies["mogu_1"] = _make_movie(
            "mogu_1", title="T", playUrls={"线路A": {1: "u1"}}
        )

        # Never touch the network in this test: stub out the lazy reload.
        async def _noop_ensure(_movie_id):
            return None
        src._ensure_sources = _noop_ensure  # type: ignore

        # Asking for an episode that isn't cached and with no detail page hit
        # should return None rather than crash.
        url = _run(src.get_play_url_for_episode("mogu_1", 9, "线路A"))
        assert url is None
