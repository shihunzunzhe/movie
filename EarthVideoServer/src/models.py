from pydantic import BaseModel
from typing import List, Optional, Any, Dict

class ApiResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: Any

class PageData(BaseModel):
    list: List[Any]
    page: int
    size: int
    total: int
    hasMore: bool

class Movie(BaseModel):
    id: str
    title: str
    description: str = ""
    posterUrl: str
    type: str
    region: str = ""
    year: int = 0
    genre: List[str] = []
    director: str = ""
    actors: List[str] = []
    episodeTotal: int = 0
    episodeUpdated: int = 0
    episodeTag: str = ""
    hotTag: bool = False
    rating: float = 0.0
    tags: str = ""
    source: str = ""
    sourceAvatar: str = ""
    highlightTitle: Optional[str] = None
    introduction: Optional[str] = ""
    publishDate: Optional[str] = None
    playUrls: Dict[str, Dict[int, str]] = {}

class Episode(BaseModel):
    episodeNumber: int
    title: str
    duration: int
    current: bool = False

class PlaySource(BaseModel):
    sourceId: str
    sourceName: str
    priority: int

class SearchHistory(BaseModel):
    keywords: List[str]

class HotSearchItem(BaseModel):
    keyword: str
    tag: str
    description: str

class RankItem(BaseModel):
    rank: int
    movieId: str
    movie: Movie

class UserProfile(BaseModel):
    isLogin: bool = False
    nickname: str = ""
    avatar: str = ""
    historyCount: int = 0
    favoriteCount: int = 0
    downloadCount: int = 0

class DataSource(BaseModel):
    name: str
    enabled: bool = True
    base_url: str
    list_endpoint: str = "/api.php/providedao/vod/"
    detail_endpoint: str = "/api.php/providedao/vod/"
    format: str = "maccms"  # maccms, custom, etc.
