# 大地视频 (EarthVideo) 运行说明

## 项目结构

```
movie/
├── EarthVideoAndroid/     # Android 客户端 (Kotlin + Jetpack Compose)
├── EarthVideoServer/      # 后端 API 服务 (Python + FastAPI)
├── docs/                  # 设计文档 (PRD, API_Spec, UI_Design_System, Agent_Prompt)
└── RUN.md                 # 本文件
```

## 后端启动（已配置后台持久运行）

后端使用 Python FastAPI，绑定 `0.0.0.0:8808`，已通过 launchctl 配置为开机自启、崩溃自动重启。

### 查看后端状态

```bash
# 检查是否运行
launchctl list com.earthvideo.server

# 健康检查
curl http://192.168.1.36:8808/api/health

# 查看日志
tail -f /tmp/earthvideo-server.log
```

### 手动重启

```bash
launchctl unload ~/Library/LaunchAgents/com.earthvideo.server.plist 2>/dev/null
launchctl load ~/Library/LaunchAgents/com.earthvideo.server.plist
sleep 4
curl http://192.168.1.36:8808/api/health
```

### 手动启动（无需 launchctl）

```bash
cd EarthVideoServer
bash start.sh
```

### 停止服务

```bash
launchctl unload ~/Library/LaunchAgents/com.earthvideo.server.plist
# 或
pkill -f "python main.py"
```

### 服务配置说明

- 绑定地址：`0.0.0.0:8808`（局域网所有设备可访问）
- 远程数据源代理：`HTTP_PROXY=http://127.0.0.1:7897`
- 开机自启 + 崩溃自动重启（launchctl KeepAlive）
- 日志文件：`/tmp/earthvideo-server.log`
- 数据源：内置 42 部 Mock 影片 + 远程 apiyutu.com 10 部 = 52 部

## 后端 API 测试

启动后访问：

```bash
# 健康检查
curl http://192.168.1.36:8808/api/health
# 返回: {"status":"ok","movies_count":52}

# 首页推荐
curl "http://192.168.1.36:8808/api/home/recommend?page=1&size=5"

# 搜索
curl "http://192.168.1.36:8808/api/search?keyword=%E9%87%8D%E5%99%A8"

# 分类筛选
curl "http://192.168.1.36:8808/api/category/list?type=%E7%94%B5%E5%BD%B1"

# 排行榜
curl "http://192.168.1.36:8808/api/rank/list?type=hot"
```

## Android 端编译运行

### 编译 APK

```bash
cd EarthVideoAndroid
./gradlew assembleDebug
```

APK 路径：`EarthVideoAndroid/app/build/outputs/apk/debug/app-debug.apk`（约 18MB）

### 安装到真机

确保手机和电脑在同一局域网（192.168.1.x），然后：

```bash
# 连接真机
adb devices

# 安装 APK
adb install EarthVideoAndroid/app/build/outputs/apk/debug/app-debug.apk

# 或直接拖拽 APK 文件到手机安装
```

### 网络配置

App 自动连接 `192.168.1.36:8808`（后端地址）。如需修改：

编辑 `app/src/main/java/com/earthvideo/app/data/api/RetrofitClient.kt`
中的 `HOST_IP` 常量和 `PORT` 常量。

模拟器会自动使用 `10.0.2.2:8808` 映射到宿主机。

## 核心功能清单

### 7 个核心页面（全部实现）

1. **首页** - 顶部搜索栏、分类 Tab（推荐/新剧/国外热映/电视剧/电影/综艺）、双列瀑布流、热播角标、集数标签
2. **搜索页** - 搜索输入框、搜索历史标签云（可清除）、热门搜索列表
3. **搜索结果页** - 类型筛选 Tab（全部/电视剧/电影/综艺/动漫/短剧）、左图右信息列表、"立即播放"按钮
4. **我的页** - 模糊背景头像、登陆/注册提示、菜单列表（观看历史/我的收藏/我的下载/上传视频/意见反馈/设置）、统计信息
5. **找片页** - 多维筛选（类型/风格/地区/年份/排序）、三列海报网格
6. **排行页** - 蓝色渐变 Banner、6 个榜单 Tab（热播榜/飙升榜/热搜榜/新片榜/电视剧/电影）、带名次角标的列表
7. **播放页** - ExoPlayer 视频播放器、选集切换、收藏/取消收藏、评分展示、简介展开/收起、播放源选择、投屏/弹幕占位

### 用户功能

| 功能 | 状态 |
|------|------|
| 观看历史 | 自动记录，可查看列表、点击播放、清空全部 |
| 我的收藏 | 播放页可收藏/取消收藏，收藏列表可查看和播放 |
| 搜索历史 | 内置示例关键词，可清除 |
| 视频播放 | ExoPlayer 播放器，支持选集切换、播放源选择 |
| 分类筛选 | 类型/风格/地区/年份/排序 5 维筛选 |

### 后端 API 接口

共 18 个接口，全部实现并测试通过：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/health | 健康检查 |
| GET | /api/home/recommend | 首页推荐（按分类） |
| GET | /api/search/history | 搜索历史 |
| POST | /api/search/history/clear | 清除搜索历史 |
| GET | /api/search/hot | 热门搜索 |
| GET | /api/search/suggest | 搜索建议 |
| GET | /api/search | 搜索结果（支持按类型筛选） |
| GET | /api/category/list | 分类筛选（5 维筛选） |
| GET | /api/rank/list | 排行榜（6 种排行） |
| GET | /api/movie/detail | 影片详情 |
| GET | /api/movie/episodes | 剧集列表 |
| GET | /api/movie/playUrl | 播放地址（10 个测试视频） |
| GET | /api/user/profile | 用户资料 |
| GET | /api/user/history | 获取历史列表 |
| POST | /api/user/history/add | 添加观看历史 |
| POST | /api/user/history/clear | 清空观看历史 |
| GET | /api/user/favorites | 获取收藏列表 |
| POST | /api/user/favorites/toggle | 切换收藏状态 |
| GET | /api/user/favorites/status | 查询收藏状态 |

## 设计文档

- `docs/PRD.md` - 产品需求文档
- `docs/API_Spec.md` - API 接口规范
- `docs/UI_Design_System.md` - UI 设计规范
- `docs/Agent_Prompt.md` - 开发 Agent 提示词

## 常见问题

### 后端启动失败

```bash
# 查看日志排查
tail -20 /tmp/earthvideo-server.log

# 检查端口占用
lsof -i :8808

# 手动启动测试
cd /Users/neil/soft/code/Android-app/movie/EarthVideoServer
source .venv/bin/activate
python main.py
```

### 真机连接不上后端

1. 确保手机和电脑在同一 WiFi 网络（192.168.1.x）
2. 检查电脑防火墙是否允许 8808 端口
3. 在手机上用浏览器访问 `http://192.168.1.36:8808/api/health` 测试
4. 如需修改 IP，编辑 `RetrofitClient.kt` 中的 `HOST_IP`

### 播放器没有画面

1. 使用的是公共测试视频链接，需要网络畅通
2. 检查 ExoPlayer 日志输出
3. 尝试点击"重新加载"按钮

### 编译报错

```bash
cd EarthVideoAndroid
./gradlew clean
./gradlew assembleDebug
```
