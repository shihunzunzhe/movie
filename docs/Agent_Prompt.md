# Agent 开发 Prompt：大地视频 Android App

> 本 Prompt 用于交给另一位开发 Agent，指导其独立完成 Android 端 + 后端 Mock 服务的开发。

---

## 1. 你的角色

你是一名资深 Android 开发工程师 + 后端开发工程师。你的任务是按照本 Prompt 和配套文档，独立完成一款影视类 Android App 及其后端 Mock 服务的开发，最终交付可运行的完整项目源码。

---

## 2. 项目目标

开发一款名为「大地视频」的 Android 影视 App，完整还原用户提供的 7 张截图中的 UI 样式与交互流程。后端使用样例数据（Mock Server），App 可独立运行演示。

---

## 3. 必须阅读的参考文档

在执行任何代码开发前，你必须先完整阅读以下文件：

1. **PRD**：[file:/workspace/output/PRD.md](file:/workspace/output/PRD.md)
   - 包含产品概述、7 个页面详细说明、数据模型、交互流程、验收标准。

2. **UI 设计规范**：[file:/workspace/output/UI_Design_System.md](file:/workspace/output/UI_Design_System.md)
   - 包含颜色、字体、间距、组件、页面布局、图标、图片规范。

3. **API 接口规范**：[file:/workspace/output/API_Spec.md](file:/workspace/output/API_Spec.md)
   - 包含所有后端接口定义、请求/响应格式、数据模型、样例数据建议。

4. **截图原图**（按页面）：
   - 首页：[file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144633_com.ground.dddymovie.jpg](file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144633_com.ground.dddymovie.jpg)
   - 搜索页：[file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144637_com.ground.dddymovie.jpg](file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144637_com.ground.dddymovie.jpg)
   - 搜索结果页：[file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144642_com.ground.dddymovie.jpg](file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144642_com.ground.dddymovie.jpg)
   - 我的页：[file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144653_com.ground.dddymovie.jpg](file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144653_com.ground.dddymovie.jpg)
   - 找片页：[file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144657_com.ground.dddymovie.jpg](file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144657_com.ground.dddymovie.jpg)
   - 排行页：[file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144701_com.ground.dddymovie.jpg](file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144701_com.ground.dddymovie.jpg)
   - 播放页：[file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144720_com.ground.dddymovie.jpg](file:/workspace/UserGlobalData/UserProjectData/47106/Screenshot_20260817_144720_com.ground.dddymovie.jpg)

---

## 4. 技术栈（必须遵守）

### Android 端
- **语言**：Kotlin
- **UI 框架**：Jetpack Compose（优先，UI 还原度更高）
- **最低 SDK**：API 26（Android 8.0）
- **目标 SDK**：API 34 或 35
- **架构**：MVVM + Repository 模式
- **依赖库**：
  - 网络：Retrofit 2 + OkHttp + Gson
  - 图片加载：Coil
  - 视频播放：ExoPlayer
  - 导航：Jetpack Navigation Compose
  - 状态管理：ViewModel + StateFlow/Compose State
  - 协程：Kotlin Coroutines
- **构建工具**：Gradle（Kotlin DSL 或 Groovy DSL 均可）

### 后端 Mock
- **框架**：Spring Boot 3.x（Java 17+）
- **构建工具**：Maven 或 Gradle
- **数据**：内存中静态 List / Map 模拟数据库
- **端口**：8080
- **跨域**：必须开启 CORS，允许所有来源访问

---

## 5. 你需要完成的事

### 5.1 后端 Mock 服务
1. 创建 Spring Boot 项目
2. 实现 API_Spec.md 中定义的所有接口
3. 准备充足的样例数据（至少覆盖 PRD 中提到的影片信息）
4. 使用占位图 URL（via.placeholder.com）作为海报
5. 配置 CORS
6. 确保后端可独立运行：`./mvnw spring-boot:run` 或 `./gradlew bootRun`

### 5.2 Android App
1. 创建 Android Studio 项目
2. 配置 Gradle 依赖
3. 实现网络层（Retrofit + ApiResponse 封装）
4. 实现数据模型（Movie、Episode、RankItem 等）
5. 实现 Repository 和 ViewModel
6. 实现 7 个核心页面：
   - 首页
   - 搜索页
   - 搜索结果页
   - 我的页
   - 找片页
   - 排行页
   - 播放页
7. 实现底部 Tab 导航
8. 实现页面内横向 Tab 切换
9. 实现图片加载、占位图、错误图
10. 实现分页加载、加载状态、错误状态
11. 实现视频播放（ExoPlayer），接入测试视频源

---

## 6. 页面实现要求

### 6.1 首页
- 顶部蓝色 Header，包含搜索框、历史图标、下载图标
- 横向 Tab：推荐 / 新剧 / 国外热映 / 电视剧 / 电影 / 综艺
- 双列瀑布流海报卡片
- 每张卡片显示海报、标题、描述、集数标签、热播角标
- 底部 TabBar，首页选中

### 6.2 搜索页
- 搜索框占位符"片名 / 演员 / 导演"，右侧"取消"
- 搜索历史标签云，可清除
- 热门搜索双列列表，带"热"/"荐"标签
- 输入关键词后可跳转搜索结果页

### 6.3 搜索结果页
- 顶部保留搜索关键词
- Tab：全部 / 电视剧 / 电影 / 综艺 / 动漫 / 短剧
- 列表项：左图右信息，含高亮标题、类型标签、地区年份、导演、主演
- 蓝色"立即播放"按钮

### 6.4 我的页
- 顶部模糊海报背景
- 默认头像 + "登陆/注册" + "开启大地视频之旅"
- 菜单列表：观看历史、我的收藏、我的下载、上传视频、意见反馈、设置
- 底部 TabBar，我的选中

### 6.5 找片页
- 顶部"分类/专题"切换，分类默认选中
- 多维筛选：类型、风格、地区、年份、排序
- 三列海报网格
- 筛选切换后刷新内容

### 6.6 排行页
- 顶部蓝色横幅"排行榜 PAI HANG BANG"
- Tab：热播榜 / 飙升榜 / 热搜榜 / 新片榜 / 电视剧 / 电影
- 排行榜列表，每项带名次角标

### 6.7 播放页
- 横屏/全屏视频播放器
- 顶部：返回、标题、TV、弹幕
- 播放控制：播放/暂停、进度条、当前/总时长、全屏
- 影片信息：标题、评分、地区、年份、标签、简介
- 来源信息
- 操作栏：评论、收藏、下载、帮助、分享
- 选集区：横向数字按钮，当前选中高亮

---

## 7. 数据模型要求

必须与 API_Spec.md 中的定义保持一致。核心模型包括：

```kotlin
data class Movie(
    val id: String,
    val title: String,
    val highlightTitle: String? = null,
    val description: String,
    val posterUrl: String,
    val type: String,
    val region: String,
    val year: Int,
    val genre: List<String> = emptyList(),
    val director: String,
    val actors: List<String> = emptyList(),
    val episodeTotal: Int = 0,
    val episodeUpdated: Int = 0,
    val episodeTag: String,
    val hotTag: Boolean = false,
    val rating: Double = 0.0,
    val tags: String = "",
    val source: String = "",
    val sourceAvatar: String = "",
    val introduction: String = ""
)

data class Episode(
    val episodeNumber: Int,
    val title: String,
    val duration: Int,
    val current: Boolean = false
)

data class PlaySource(
    val sourceId: String,
    val sourceName: String,
    val priority: Int
)

data class RankItem(
    val rank: Int,
    val movieId: String,
    val movie: Movie
)
```

---

## 8. 后端 API 列表（必须全部实现）

| 方法 | 接口 | 说明 |
|------|------|------|
| GET | /api/home/recommend | 首页推荐 |
| GET | /api/search/history | 搜索历史 |
| GET | /api/search/hot | 热门搜索 |
| GET | /api/search/suggest | 搜索建议 |
| GET | /api/search | 搜索结果 |
| GET | /api/category/list | 分类筛选 |
| GET | /api/rank/list | 排行榜 |
| GET | /api/movie/detail | 影片详情 |
| GET | /api/movie/episodes | 影片剧集 |
| GET | /api/movie/playUrl | 播放地址 |
| GET | /api/user/profile | 用户资料 |

详细参数和响应格式见 [file:/workspace/output/API_Spec.md](file:/workspace/output/API_Spec.md)。

---

## 9. UI 还原要求

- 主色：#3B6EE5
- 热播角标：红色 #F44336
- 描述文字：#999999
- 标题文字：#212121
- 卡片圆角：8dp（首页）、6dp（找片）
- 页面边距：16dp
- 列表间距：12dp
- 底部 Tab 高度：56dp
- 字体大小层级见 UI_Design_System.md

**关键要求：最终 UI 必须与截图高度一致。**

---

## 10. 开发步骤建议

### 阶段 A：后端 Mock（建议先完成）
1. 创建 Spring Boot 项目
2. 定义数据模型和样例数据
3. 实现 Controller 和 Service
4. 启动服务，用 curl/Postman 验证每个接口

### 阶段 B：Android 基础框架
1. 创建 Android 项目
2. 配置依赖（Compose、Retrofit、Coil、ExoPlayer、Navigation）
3. 配置主题色和全局样式
4. 搭建网络层和数据模型
5. 实现底部 Tab 导航和空页面占位

### 阶段 C：页面实现
按以下顺序实现页面：
1. 首页
2. 我的页
3. 找片页
4. 排行页
5. 搜索页
6. 搜索结果页
7. 播放页

### 阶段 D：联调测试
1. Android 端连接后端 Mock
2. 检查数据解析是否正确
3. 检查 UI 是否与截图一致
4. 测试播放功能
5. 处理异常和空状态

---

## 11. 代码规范

### Kotlin 代码
- 使用 Kotlin 协程处理异步
- ViewModel 中使用 StateFlow/Compose State 管理 UI 状态
- Repository 负责数据获取
- UI 层只负责展示，不写业务逻辑
- 使用 Compose 时，Stateless Composable 优先

### 命名规范
- 包名：`com.earthvideo.app`
- Activity：`MainActivity`
- Screen 组件：`HomeScreen`、`SearchScreen`、`PlayerScreen`
- ViewModel：`HomeViewModel`、`SearchViewModel`
- Repository：`MovieRepository`
- 数据模型：`Movie`、`Episode`、`RankItem`

### 资源规范
- string 资源全部放到 `res/values/strings.xml`
- color 资源放到 `res/values/colors.xml` 或使用 Compose Color 常量
- 图标使用 Vector Asset，统一 24dp

---

## 12. 禁止事项

❌ 不要使用截图中的真实版权海报和视频资源  
❌ 不要硬编码后端 URL，使用 BuildConfig 或配置文件  
❌ 不要在 UI 层直接调用 Retrofit 接口  
❌ 不要忽略加载状态和错误状态  
❌ 不要让 App 因网络异常崩溃  
❌ 不要遗漏任何一个核心页面  
❌ 不要省略底部 Tab 导航  

---

## 13. 交付物清单

完成后，你必须在 `/workspace/output/` 目录下提供：

1. **Android 项目目录**：`/workspace/output/EarthVideoAndroid/`
2. **后端项目目录**：`/workspace/output/EarthVideoServer/`
3. **运行说明文档**：`/workspace/output/RUN.md`
   - Android 项目如何导入 Android Studio 并运行
   - 后端项目如何启动
   - 如何验证接口
   - 常见问题处理

---

## 14. 验收标准

### 14.1 后端验收
- [ ] 所有 11 个接口可用 curl 调用并返回正确 JSON
- [ ] 跨域已配置
- [ ] 样例数据充足（首页、分类、搜索、排行均有数据）

### 14.2 Android 验收
- [ ] 项目可成功编译运行
- [ ] 7 个核心页面全部实现
- [ ] 底部 Tab 导航可正常切换
- [ ] 首页、找片、排行可加载并显示后端数据
- [ ] 搜索功能和搜索结果页正常工作
- [ ] 播放页可播放测试视频
- [ ] UI 与截图高度一致

### 14.3 文档验收
- [ ] RUN.md 文档清晰，用户按步骤可运行项目

---

## 15. 调试建议

- Android 模拟器访问本机后端使用 `10.0.2.2:8080`
- 真机访问使用电脑局域网 IP + 端口
- 使用 Logcat 和 Chrome DevTools 调试网络请求
- 图片加载失败时检查 URL 和 Coil 配置
- 视频播放失败时检查 ExoPlayer 权限和网络 URL

---

## 16. 开始执行前请复述

在正式开始写代码前，请简要复述：
1. 你要开发的应用名称和核心页面数量
2. 你采用的技术栈
3. 你将要产出的交付物路径

然后开始执行。
