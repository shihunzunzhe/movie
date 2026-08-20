# EarthVideo (大地视频) 开发笔记

> 最后更新: 2026-08-18
> 用于记录开发上下文、架构决策和后续开发参考

---

## 一、项目概览

### 技术栈
- **Android 端**: Kotlin + Jetpack Compose + ExoPlayer
- **后端**: Python FastAPI (uvicorn)
- **构建**: Gradle (Kotlin DSL)
- **数据源**: 内置 Mock 42 部 + 远程 apiyutu.com 10 部 = 52 部
- **网络**: Retrofit + OkHttp + Coil (图片)

### 项目路径
- 根目录: `/Users/neil/soft/code/Android-app/movie`
- Android: `EarthVideoAndroid/`
- 后端: `EarthVideoServer/`
- 设计文档: `docs/` (PRD.md, API_Spec.md, UI_Design_System.md, Agent_Prompt.md)
- 运行说明: `RUN.md`
- 本文件: `DEVELOPMENT_NOTES.md`

### 网络配置
- 后端地址: `192.168.1.36:8808` (局域网真机调试)
- Android 端: RetrofitClient.kt 中 `HOST_IP = "192.168.1.36"`, `PORT = 8808`
- 模拟器: 自动识别, 使用 `10.0.2.2` 映射
- 远程数据源代理: `HTTP_PROXY=http://127.0.0.1:7897`
- Manifest: `usesCleartextTraffic="true"` (明文 HTTP 已配置)

---

## 二、后端架构

### 启动方式 (persistent)
```bash
# 通过 launchctl 持久化运行 (KeepAlive, 崩溃自动重启)
launchctl load ~/Library/LaunchAgents/com.earthvideo.server.plist

# 手动启动
cd EarthVideoServer
source .venv/bin/activate
python main.py
```

### plist 配置位置
`~/Library/LaunchAgents/com.earthvideo.server.plist`

### 日志
`/tmp/earthvideo-server.log`

### 后端文件结构
```
EarthVideoServer/
├── main.py              # FastAPI 主应用, 18+ 端点
├── start.sh             # 启动脚本 (可自启 watchdog)
├── requirements.txt     # fastapi, uvicorn, pydantic, httpx
├── .venv/               # Python 虚拟环境
└── src/
    ├── __init__.py
    ├── models.py        # Pydantic 数据模型
    └── data_sources.py  # DataSourceManager + RemoteDataSource + MOCK_MOVIES
```

### 核心架构: 多数据源
`DataSourceManager` 管理多个数据源, 当前只有 `yutu` 源 (apiyutu.com):
- 远程源通过 `HTTP_PROXY=http://127.0.0.1:7897` 代理访问
- 内置 42 部 Mock 影片 (MOCK_MOVIES)
- 远程获取 10 部
- 背景每 10 分钟自动刷新 (asyncio)
- 提供 `add_source()` 方法便于后续添加更多 API 源

### API 端点列表 (18 个, 全部测试通过)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/health | 健康检查 |
| GET | /api/home/recommend | 首页推荐 |
| GET | /api/search/history | 搜索历史 |
| POST | /api/search/history/clear | 清除搜索历史 |
| GET | /api/search/hot | 热门搜索 |
| GET | /api/search/suggest | 搜索建议 |
| GET | /api/search | 搜索结果 |
| GET | /api/category/list | 分类筛选 |
| GET | /api/rank/list | 排行榜 |
| GET | /api/movie/detail | 影片详情 |
| GET | /api/movie/episodes | 剧集列表 |
| GET | /api/movie/playUrl | 播放地址 |
| GET | /api/user/profile | 用户资料 |
| GET | /api/user/history | 获取历史列表 |
| POST | /api/user/history/add | 添加观看历史 |
| POST | /api/user/history/clear | 清空观看历史 |
| GET | /api/user/favorites | 获取收藏列表 |
| POST | /api/user/favorites/toggle | 切换收藏状态 |
| GET | /api/user/favorites/status | 查询收藏状态 |

### 播放视频源 (10 个公共测试视频)
- `https://media.w3.org/2010/05/sintel/trailer.mp4`
- `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4`
- `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4`
- `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4`
- `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4`
- `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4`
- `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4`
- `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4`
- `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4`
- `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4`

视频分配: 基于 movieId 的稳定哈希值, 不同影片播放不同视频, 同一影片固定同一视频源。

---

## 三、Android 架构

### 文件结构 (26 个 Kotlin 文件)
```
EarthVideoAndroid/app/src/main/java/com/earthvideo/app/
├── MainActivity.kt               # 入口 + Scaffold + BottomBar
├── data/
│   ├── api/
│   │   ├── ApiService.kt         # Retrofit 接口定义 (18 个方法)
│   │   └── RetrofitClient.kt     # OkHttp + Retrofit 配置
│   ├── model/
│   │   └── Models.kt             # 所有数据模型 (Movie, Episode, RankItem 等)
│   └── repository/
│       └── MovieRepository.kt    # 数据仓库封装
└── ui/
    ├── components/
    │   ├── BottomNavBar.kt       # 底部导航 (首页/排行/找片/我的)
    │   ├── MovieCard.kt          # 首页双列卡片
    │   ├── CategoryGridItem.kt   # 找片三列网格项
    │   ├── RankListItem.kt       # 排行列表项 (带名次角标)
    │   └── SearchResultItem.kt   # 搜索结果列表项 (左图右信息+立即播放)
    ├── home/
    │   ├── HomeScreen.kt         # 首页 (Header + Tab + 双列网格)
    │   └── HomeViewModel.kt      # 首页 ViewModel
    ├── search/
    │   └── SearchScreen.kt       # 搜索页 (输入+历史+热门)
    ├── searchresult/
    │   └── SearchResultScreen.kt # 搜索结果页 (Tab筛选+列表)
    ├── discover/
    │   └── DiscoverScreen.kt     # 找片页 (5维筛选+三列网格)
    ├── rank/
    │   └── RankScreen.kt         # 排行页 (Banner+6榜Tab+列表)
    ├── player/
    │   └── PlayerScreen.kt       # 播放页 (ExoPlayer+选集+收藏)
    ├── profile/
    │   └── ProfileScreen.kt      # 我的页 (模糊背景+菜单列表)
    ├── user/
    │   ├── HistoryScreen.kt      # 观看历史列表
    │   ├── FavoritesScreen.kt    # 收藏列表 (双列网格)
    │   └── DownloadsScreen.kt    # 下载页面 (模拟数据+Tab)
    ├── settings/
    │   └── SettingsScreen.kt     # 设置页
    ├── navigation/
    │   └── NavGraph.kt           # 导航图 (11 个路由)
    └── theme/
        ├── Color.kt              # 颜色常量
        ├── Theme.kt              # Material3 主题
        └── Type.kt              # 字体排版
```

### 导航路由 (11 个)
```
home          -> 首页
search        -> 搜索页
search_result/{keyword} -> 搜索结果页
profile       -> 我的页
discover      -> 找片页
rank          -> 排行页
player/{movieId}/{episode} -> 播放页
history       -> 观看历史
favorites     -> 我的收藏
downloads     -> 我的下载
settings      -> 设置
```

### 依赖配置 (build.gradle.kts)
- Compose BOM 2023.10.01 + Material3
- Navigation Compose 2.7.5
- Retrofit 2.9.0 + OkHttp 4.12.0
- Coil Compose 2.5.0
- ExoPlayer (Media3) 1.2.0

---

## 四、UI 设计系统

### 色彩
| 名称 | 色值 | 用途 |
|------|------|------|
| Primary | #3B6EE5 | 品牌主色, Header, Tab选中, 按钮 |
| PrimaryDark | #2A5ACF | 渐变辅助 |
| PrimaryLight | #5A8CF7 | 高亮 |
| HotRed | #F44336 | 热播角标 |
| Gold | #FFC107 | 排行第1名 |
| Orange | #FF9800 | 排行第2-3名 |
| TextPrimary | #212121 | 主文字 |
| TextSecondary | #666666 | 次要文字 |
| TextHint | #999999 | 辅助文字/占位符 |
| TabBg | #F5F5F5 | 标签背景 |
| Divider | #EEEEEE | 分割线 |
| PlayerBg | #000000 | 播放器背景 |
| SemiBlack | #80000000 | 半透明遮罩 |

### 字号
- H1: 22sp Bold (登陆/注册)
- H2: 18sp Bold (页面标题)
- H3: 16sp Medium (卡片标题)
- Body: 14sp Regular
- Caption: 12sp Regular
- Small: 10sp Regular

---

## 五、已实现功能

### 7 个核心页面
1. **首页** - Header(搜索+历史+下载图标), 6个分类Tab, 双列瀑布流, 热播角标, 集数标签
2. **搜索页** - 搜索输入(IME Search), 历史标签云(可清除), 热门搜索列表
3. **搜索结果页** - 6个类型Tab, 左图右信息列表, "立即播放"按钮
4. **我的页** - 模糊海报背景, 头像, 统计行(历史/收藏/下载), 6个菜单项
5. **找片页** - 分类/专题切换, 5维筛选(类型/风格/地区/年份/排序), 三列网格
6. **排行页** - 蓝色渐变Banner, 6个榜单Tab(热播/飙升/热搜/新片/电视剧/电影), 带名次角标
7. **播放页** - ExoPlayer, 顶部覆盖(返回/投屏/弹幕), 选集(滚动+网格), 收藏, 简介展开, 播放源选择, 操作栏(评论/收藏/下载/反馈/分享)

### 用户功能
- 观看历史: 自动记录, 列表查看, 清空
- 我的收藏: 播放页切换收藏, 列表查看
- 搜索历史: 示例数据, 可清除
- 设置页: WiFi下播放, 常亮, 清除缓存(弹窗)

---

## 六、已知问题与待改进

### 当前问题
1. **PlayerScreen** - 之前有结构错误(信息区嵌套在播放器Box内), 已修复
2. **SearchResultItem.kt** - HTML `<em>` 高亮标签未完全解析 (buildAnnotatedString 未应用高亮色)
3. **ProfileScreen.kt** - `LocalContext` 从两个不同包导入, 代码可清理
4. **搜索建议** - 远程数据源返回的中文建议较长, 可能影响UI
5. **DownloadsScreen** - 使用模拟数据, 无真实下载功能

### 待实现功能
1. **弹幕系统** - 播放页弹幕开关已占位, 需 WebSocket 或简单弹幕层
2. **投屏功能** - 投屏图标已占位
3. **评论功能** - 播放页评论按钮已占位
4. **分享功能** - 分享按钮已占位
5. **上传视频** - 我的页菜单项已占位
6. **真实登录/注册** - 目前为模拟未登录状态
7. **本地下载** - 下载功能需要 Android DownloadManager 或 ExoPlayer 离线下载
8. **专题页面** - 找片页"专题"Tab 未实现

### 性能优化方向
1. 首页加载骨架屏
2. 图片缓存策略优化 (Coil)
3. 分页加载更多 (当前只加载第一页)
4. 列表 Item 缓存 (LazyColumn 已自动处理)

---

## 七、开发命令速查

### 后端
```bash
# 启动
cd /Users/neil/soft/code/Android-app/movie/EarthVideoServer
source .venv/bin/activate
python main.py

# 持久化
launchctl load ~/Library/LaunchAgents/com.earthvideo.server.plist

# 重启
launchctl unload ~/Library/LaunchAgents/com.earthvideo.server.plist 2>/dev/null
launchctl load ~/Library/LaunchAgents/com.earthvideo.server.plist

# 查看日志
tail -f /tmp/earthvideo-server.log

# 测试API
curl http://192.168.1.36:8808/api/health
curl "http://192.168.1.36:8808/api/search?keyword=重器"

# 停服
pkill -f "python main.py"
```

### Android
```bash
# 编译
cd /Users/neil/soft/code/Android-app/movie/EarthVideoAndroid
./gradlew assembleDebug

# 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 清理重编
./gradlew clean assembleDebug
```

### APK 路径
`EarthVideoAndroid/app/build/outputs/apk/debug/app-debug.apk` (~18MB)

---

## 八、设计文档索引

- `docs/PRD.md` — 产品需求文档 (7个页面详细说明)
- `docs/API_Spec.md` — API 接口规范 (请求/响应格式)
- `docs/UI_Design_System.md` — UI 设计规范 (颜色/字体/组件)
- `docs/Agent_Prompt.md` — 开发 Agent 提示词

---

## 九、后续数据源接入指南

`main.py` 中数据源配置:
```python
# 添加新数据源
data_manager.add_source("yutu", "https://apiyutu.com", enabled=True, proxy_url=proxy_url)
```

每个数据源需要:
1. 提供 base_url
2. 实现 MACCMS 标准 JSON 格式 (或修改 `_parse_maccms` 方法)
3. 可配置代理

---

*本文件由开发过程中自动生成, 用于保存对话上下文和项目状态。*
