"""Application configuration for EarthVideo Server.

Loads settings from .env file or environment variables.
Provides a singleton AppConfig with runtime reload support.
"""

import os
import logging
from dotenv import load_dotenv

logger = logging.getLogger("earthvideo.config")

# Load .env once at import time
load_dotenv()


class AppConfig:
    """Application configuration loaded from environment variables.

    All attributes are read from os.environ on init / reload().
    Use the module-level `config` singleton.

    Naming convention: UPPER_CASE for env vars, snake_case for Python attrs.
    """

    # ── Collection (scraping) ──────────────────────────────────────────
    collection_enabled: bool = True
    """Master switch: when False, no scheduled scraping or MySQL sync runs."""

    scrape_interval: int = 7200
    """Seconds between full scrape cycles (default 2 hours)."""

    sync_interval: int = 600
    """Seconds between in-memory → MySQL syncs (default 10 minutes)."""

    detail_fetch_limit: int = 500
    """Max movies to fetch detail pages for per scrape cycle."""

    detail_concurrency: int = 8
    """Number of concurrent HTTP requests for detail fetching."""

    # ── Server ────────────────────────────────────────────────────────
    host: str = "0.0.0.0"
    port: int = 8808
    log_level: str = "info"

    # ── MySQL ─────────────────────────────────────────────────────────
    db_host: str = "127.0.0.1"
    db_port: int = 3306
    db_user: str = "root"
    db_password: str = ""
    db_name: str = "earthvideo"

    def __init__(self) -> None:
        self.reload()

    def reload(self) -> None:
        """(Re)load all settings from environment variables."""
        # ── Collection ──
        self.collection_enabled = self._env_bool("COLLECTION_ENABLED", True)
        self.scrape_interval = int(os.getenv("SCRAPE_INTERVAL", "7200"))
        self.sync_interval = int(os.getenv("SYNC_INTERVAL", "600"))
        self.detail_fetch_limit = int(os.getenv("DETAIL_FETCH_LIMIT", "500"))
        self.detail_concurrency = int(os.getenv("DETAIL_CONCURRENCY", "8"))

        # ── Server ──
        self.host = os.getenv("HOST", "0.0.0.0")
        self.port = int(os.getenv("PORT", "8808"))
        self.log_level = os.getenv("LOG_LEVEL", "info")

        # ── MySQL ──
        self.db_host = os.getenv("DB_HOST", "127.0.0.1")
        self.db_port = int(os.getenv("DB_PORT", "3306"))
        self.db_user = os.getenv("DB_USER", "root")
        self.db_password = os.getenv("DB_PASSWORD", "")
        self.db_name = os.getenv("DB_NAME", "earthvideo")

        logger.info(
            "Config loaded: collection_enabled=%s, scrape_interval=%s, sync_interval=%s",
            self.collection_enabled,
            self.scrape_interval,
            self.sync_interval,
        )

    @staticmethod
    def _env_bool(key: str, default: bool) -> bool:
        val = os.getenv(key)
        if val is None:
            return default
        return val.strip().lower() in ("1", "true", "yes", "on")

    def to_dict(self) -> dict:
        """Return all settings as a plain dict (for API serialisation)."""
        return {
            "collection_enabled": self.collection_enabled,
            "scrape_interval": self.scrape_interval,
            "sync_interval": self.sync_interval,
            "detail_fetch_limit": self.detail_fetch_limit,
            "detail_concurrency": self.detail_concurrency,
            "host": self.host,
            "port": self.port,
            "log_level": self.log_level,
            "db_host": self.db_host,
            "db_port": self.db_port,
            "db_name": self.db_name,
        }


# Module-level singleton – importers use `from src.config import config`
config = AppConfig()