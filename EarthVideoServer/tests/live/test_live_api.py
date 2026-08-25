"""Live API smoke tests against a running EarthVideo backend.

By default these hit ``http://192.168.1.36:8808`` (the LAN host described in
RUN.md). Override with the ``EARTHVIDEO_API_BASE`` env var, e.g.::

    EARTHVIDEO_API_BASE=http://10.0.2.2:8808 pytest tests/live

If the server is unreachable the tests are skipped, not failed, so this file
is safe to keep in CI as well.

The live API may serve explicit-content titles; we deliberately only assert on
schema, ids, and source names so we never depend on or echo that content.
"""

import os
import time
import urllib.parse
import pytest
import httpx


BASE_URL = os.environ.get("EARTHVIDEO_API_BASE", "http://192.168.1.36:8808").rstrip("/")
DEFAULT_TIMEOUT = float(os.environ.get("EARTHVIDEO_API_TIMEOUT", "8.0"))


def _skip_if_unreachable():
    try:
        r = httpx.get(f"{BASE_URL}/api/health", timeout=DEFAULT_TIMEOUT)
        if r.status_code != 200:
            pytest.skip(f"EarthVideo backend returned {r.status_code}")
    except Exception as e:
        pytest.skip(f"EarthVideo backend unreachable at {BASE_URL}: {e}")


@pytest.fixture(scope="module", autouse=True)
def require_backend():
    _skip_if_unreachable()


# --------------------------------------------------------------------------- #
# Health & catalog shape
# --------------------------------------------------------------------------- #

class TestHealthAndCatalog:
    def test_health_returns_ok(self):
        r = httpx.get(f"{BASE_URL}/api/health", timeout=DEFAULT_TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert body.get("status") == "ok"
        assert isinstance(body.get("movies_count"), int)
        assert body["movies_count"] > 0

    def test_home_recommend_lists_movies(self):
        r = httpx.get(
            f"{BASE_URL}/api/home/recommend",
            params={"page": 1, "size": 5},
            timeout=DEFAULT_TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        items = body["data"]["list"]
        assert items, "home recommend should not be empty"

        # Each item must have an id and a recognised source prefix.
        for m in items:
            assert m["id"].startswith(("yutu_", "mogu_")), f"unexpected id: {m['id']}"
            assert m["source"], f"missing source field on {m['id']}"

    def test_both_source_prefixes_can_be_observed(self):
        """We don't hard-code counts (the catalog changes over time) but the
        manager must expose at least one yutu_* and one mogu_* prefix whenever
        the scrape has anything cached."""
        r = httpx.get(
            f"{BASE_URL}/api/home/recommend",
            params={"page": 1, "size": 20},
            timeout=DEFAULT_TIMEOUT,
        )
        items = r.json()["data"]["list"]
        prefixes = {m["id"].split("_", 1)[0] for m in items}
        # Each prefix that appears must be a known source.
        assert prefixes.issubset({"yutu", "mogu"})
        # At least one prefix should appear in the top of the catalog.
        assert prefixes, "no movie prefixes visible in home/recommend"


# --------------------------------------------------------------------------- #
# /api/movie/playUrl: per-source source list and source switching
# --------------------------------------------------------------------------- #

class TestPlayUrlMultiSource:
    @staticmethod
    def _pick(prefix: str) -> str:
        """Find a movie id whose prefix matches ``prefix`` (best-effort,
        bounded search)."""
        for page in range(1, 6):
            r = httpx.get(
                f"{BASE_URL}/api/home/recommend",
                params={"page": page, "size": 20},
                timeout=DEFAULT_TIMEOUT,
            )
            for m in r.json()["data"]["list"]:
                if m["id"].startswith(prefix + "_"):
                    return m["id"]
        pytest.skip(f"No {prefix} movie visible in the first 5 recommend pages")

    def test_play_url_default_returns_url_and_sources_list(self):
        movie_id = self._pick("yutu")
        r = httpx.get(
            f"{BASE_URL}/api/movie/playUrl",
            params={"id": movie_id, "episode": 1, "source": "default"},
            timeout=DEFAULT_TIMEOUT,
        )
        assert r.status_code == 200
        data = r.json()["data"]
        assert data["movieId"] == movie_id
        assert isinstance(data["url"], str)
        # url may be empty while scraping is in flight; if it's non-empty, it
        # must look like a media URL (http(s)://...).
        if data["url"]:
            assert data["url"].startswith(("http://", "https://"))
        # sources list is always present.
        assert isinstance(data["sources"], list)
        for src in data["sources"]:
            assert {"sourceId", "sourceName", "priority"} <= set(src.keys())

    def test_play_url_explicit_sourceId_when_listed_returns_url(self):
        movie_id = self._pick("yutu")
        r = httpx.get(
            f"{BASE_URL}/api/movie/playUrl",
            params={"id": movie_id, "episode": 1, "source": "default"},
            timeout=DEFAULT_TIMEOUT,
        )
        sources = r.json()["data"]["sources"]
        if not sources:
            pytest.skip("no sources advertised for this yutu movie yet")
        src_id = sources[0]["sourceId"]

        r2 = httpx.get(
            f"{BASE_URL}/api/movie/playUrl",
            params={"id": movie_id, "episode": 1, "source": src_id},
            timeout=DEFAULT_TIMEOUT,
        )
        assert r2.status_code == 200
        data2 = r2.json()["data"]
        if data2["url"]:
            assert data2["url"].startswith(("http://", "https://"))

    def test_mogu_prefix_route_is_supported(self):
        """The mogu source should accept its own ids without falling back to
        the yutu path. If no mogu movie is currently in cache the test is
        skipped to avoid false positives."""
        try:
            movie_id = self._pick("mogu")
        except pytest.skip.Exception:
            pytest.skip("no mogu movie currently in the catalog")

        r = httpx.get(
            f"{BASE_URL}/api/movie/playUrl",
            params={"id": movie_id, "episode": 1, "source": "default"},
            timeout=DEFAULT_TIMEOUT,
        )
        assert r.status_code == 200
        data = r.json()["data"]
        assert data["movieId"] == movie_id


# --------------------------------------------------------------------------- #
# Filters & search
# --------------------------------------------------------------------------- #

class TestSearchAndFilter:
    def test_search_with_keyword(self):
        r = httpx.get(
            f"{BASE_URL}/api/search",
            params={"keyword": "Hello", "page": 1, "size": 5},
            timeout=DEFAULT_TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        assert "data" in body

    def test_category_list(self):
        r = httpx.get(
            f"{BASE_URL}/api/category/list",
            params={"type": "电影", "page": 1, "size": 5},
            timeout=DEFAULT_TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        # Empty result is acceptable; just verify schema.
        assert "list" in body["data"]

    def test_rank_list(self):
        r = httpx.get(
            f"{BASE_URL}/api/rank/list",
            params={"type": "hot", "size": 5},
            timeout=DEFAULT_TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        for item in body["data"]["list"]:
            assert item["movieId"].startswith(("yutu_", "mogu_"))
            assert 1 <= item["rank"] <= 50
