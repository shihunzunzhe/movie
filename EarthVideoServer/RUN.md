# 大地视频 (EarthVideo) 后端 Mock 服务

## 技术栈
- Python 3.9+
- FastAPI + Uvicorn
- 多数据源架构 (Mock + MACCMS 远程源)

## 快速启动

```bash
cd EarthVideoServer
bash start.sh     # 一键后台启动
```

或手动启动：
```bash
cd EarthVideoServer
source .venv/bin/activate
pip install -r requirements.txt
python main.py
```

服务将运行在 `http://0.0.0.0:8080`

## 数据源

后端支持多源架构，默认使用内置的 28 部 Mock 影片（无需网络），
可按需启用远程源（如 apiyutu.com）。

启用远程源：在 `main.py` 中取消注释：
```python
data_manager.add_source("yutu", "https://apiyutu.com", enabled=True)
```

## 接口验证

```bash
curl http://localhost:8080/api/home/recommend
curl "http://localhost:8080/api/search?keyword=重器"
curl http://localhost:8080/api/rank/list
curl "http://localhost:8080/api/category/list?type=连续剧"
curl "http://localhost:8080/api/movie/detail?id=m001"
curl "http://localhost:8080/api/movie/playUrl?id=m001&episode=1"
```

## 所有接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/home/recommend | 首页推荐 |
| GET | /api/search/history | 搜索历史 |
| GET | /api/search/hot | 热门搜索 |
| GET | /api/search/suggest | 搜索建议 |
| GET | /api/search | 搜索结果 |
| GET | /api/category/list | 分类筛选 |
| GET | /api/rank/list | 排行榜 |
| GET | /api/movie/detail | 影片详情 |
| GET | /api/movie/episodes | 剧集列表 |
| GET | /api/movie/playUrl | 播放地址 |
| GET | /api/user/profile | 用户资料 |

## CORS
已开启 CORS，允许所有来源访问。绑定 0.0.0.0 支持局域网调试。
