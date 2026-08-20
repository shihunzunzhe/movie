# 大地视频 App 后端 API 接口规范

> 本规范定义后端 Mock 服务需提供的所有 RESTful API，用于支撑 Android 端 7 个核心页面。

---

## 1. 基础约定

### 1.1 Base URL
```
http://localhost:8080/api
```
（Android 端通过 `10.0.2.2:8080` 访问本机后端）

### 1.2 统一响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

### 1.3 分页参数
- `page`：页码，从 1 开始
- `size`：每页数量，默认 20

### 1.4 统一分页响应
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [ ],
    "page": 1,
    "size": 20,
    "total": 100,
    "hasMore": true
  }
}
```

---

## 2. 接口列表

### 2.1 首页推荐

**Endpoint**
```
GET /home/recommend
```

**请求参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| category | string | 否 | 分类：recommend/new/oversea/tv/movie/variety，默认 recommend |
| page | int | 否 | 页码 |
| size | int | 否 | 每页数量 |

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "m001",
        "title": "重器",
        "description": "年代法治群像大剧",
        "posterUrl": "https://via.placeholder.com/300x400/3B6EE5/FFFFFF?text=重器",
        "type": "电视剧",
        "episodeTag": "更新至16集",
        "hotTag": false,
        "category": "recommend"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 100,
    "hasMore": true
  }
}
```

---

### 2.2 搜索历史

**Endpoint**
```
GET /search/history
```

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "keywords": ["南部档案", "老九门2", "老九门", "九门", "余罪", "凡人修仙传全集", "理想之城", "寒战1994", "寒战", "剑来"]
  }
}
```

---

### 2.3 热门搜索

**Endpoint**
```
GET /search/hot
```

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "keyword": "仙逆",
        "tag": "热",
        "description": "我辈修士,何惜一战"
      },
      {
        "keyword": "欢迎来龙餐馆",
        "tag": "荐",
        "description": ""
      }
    ]
  }
}
```

---

### 2.4 搜索建议

**Endpoint**
```
GET /search/suggest
```

**请求参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | string | 是 | 输入关键词 |

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "suggestions": ["老九门2", "老九门", "老九门之青山海棠"]
  }
}
```

---

### 2.5 搜索结果

**Endpoint**
```
GET /search
```

**请求参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | string | 是 | 搜索关键词 |
| type | string | 否 | 全部/电视剧/电影/综艺/动漫/短剧，默认 all |
| page | int | 否 | 页码 |
| size | int | 否 | 每页数量 |

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "m002",
        "title": "九门/老九门2",
        "highlightTitle": "九门/<em>老九门2</em>",
        "type": "电视剧",
        "region": "中国大陆",
        "year": 2026,
        "director": "柏杉",
        "actors": ["陈伟霆", "陈瑶", "曾舜晞", "王茂蕾", "王奕婷"],
        "posterUrl": "https://via.placeholder.com/300x400/333/FFF?text=老九门2",
        "episodeTag": "30集全",
        "hotTag": true
      }
    ],
    "page": 1,
    "size": 20,
    "total": 50,
    "hasMore": true
  }
}
```

---

### 2.6 分类筛选

**Endpoint**
```
GET /category/list
```

**请求参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 否 | 连续剧/电影/综艺/动漫/短剧，默认 all |
| genre | string | 否 | 爱情/喜剧/悬疑/犯罪/古装/惊悚，默认 all |
| region | string | 否 | 内地/美国/中国香港/中国台湾/韩国，默认 all |
| year | string | 否 | 2026/2025/2024...，默认 all |
| sort | string | 否 | 最热/最近更新/最新上线/评分/日榜，默认 最热 |
| page | int | 否 | 页码 |
| size | int | 否 | 每页数量 |

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "m003",
        "title": "花开锦绣",
        "posterUrl": "https://via.placeholder.com/300x400/E53935/FFF?text=花开锦绣",
        "episodeTag": "更新至18集",
        "hotTag": true
      }
    ],
    "page": 1,
    "size": 20,
    "total": 200,
    "hasMore": true
  }
}
```

---

### 2.7 排行榜

**Endpoint**
```
GET /rank/list
```

**请求参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 否 | hot/rising/search/new/tv/movie，默认 hot |
| page | int | 否 | 页码 |
| size | int | 否 | 每页数量 |

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "rank": 1,
        "movieId": "m004",
        "movie": {
          "id": "m004",
          "title": "天才女友",
          "type": "电视剧",
          "region": "中国大陆",
          "year": 2026,
          "director": "田羽生",
          "actors": ["田曦薇", "胡一天", "赖伟明", "安沺", "夏浩然"],
          "posterUrl": "https://via.placeholder.com/300x400/FF9800/FFF?text=天才女友",
          "episodeTag": "28集全",
          "hotTag": true
        }
      }
    ],
    "page": 1,
    "size": 20,
    "total": 100,
    "hasMore": true
  }
}
```

---

### 2.8 影片详情

**Endpoint**
```
GET /movie/detail
```

**请求参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 影片ID |

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "m001",
    "title": "重器",
    "description": "年代法治群像大剧",
    "posterUrl": "https://via.placeholder.com/300x400/3B6EE5/FFFFFF?text=重器",
    "type": "电视剧",
    "region": "中国大陆",
    "year": 2026,
    "genre": ["剧情"],
    "director": "未知",
    "actors": ["未知"],
    "episodeTotal": 16,
    "episodeUpdated": 16,
    "episodeTag": "更新至16集",
    "rating": 0.0,
    "tags": "国产,剧情",
    "source": "iris 上传",
    "sourceAvatar": "https://via.placeholder.com/100x100/999/FFF?text=iris",
    "hotTag": false,
    "introduction": "这是一部年代法治群像大剧..."
  }
}
```

---

### 2.9 影片剧集

**Endpoint**
```
GET /movie/episodes
```

**请求参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 影片ID |

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "movieId": "m001",
    "total": 16,
    "updated": 16,
    "episodes": [
      {
        "episodeNumber": 1,
        "title": "第1集",
        "duration": 2762,
        "current": true
      },
      {
        "episodeNumber": 2,
        "title": "第2集",
        "duration": 2700,
        "current": false
      }
    ]
  }
}
```

---

### 2.10 播放地址

**Endpoint**
```
GET /movie/playUrl
```

**请求参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 影片ID |
| episode | int | 是 | 集数 |
| source | string | 否 | 播放源ID，默认 default |

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "movieId": "m001",
    "episode": 1,
    "url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    "sources": [
      { "sourceId": "default", "sourceName": "默认源", "priority": 1 },
      { "sourceId": "hd", "sourceName": "高清源", "priority": 2 }
    ]
  }
}
```

> 说明：播放地址使用公开测试视频，如 Google 的 BigBuckBunny 或本地 MP4。

---

### 2.11 用户资料（模拟）

**Endpoint**
```
GET /user/profile
```

**响应示例**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "isLogin": false,
    "nickname": "",
    "avatar": "",
    "historyCount": 0,
    "favoriteCount": 0,
    "downloadCount": 0
  }
}
```

---

## 3. 数据模型定义

### 3.1 Movie
```kotlin
data class Movie(
    val id: String,
    val title: String,
    val highlightTitle: String? = null,
    val description: String,
    val posterUrl: String,
    val type: String,        // 电视剧 | 电影 | 综艺 | 动漫 | 短剧
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
```

### 3.2 Episode
```kotlin
data class Episode(
    val episodeNumber: Int,
    val title: String,
    val duration: Int,       // 秒
    val current: Boolean = false
)
```

### 3.3 PlaySource
```kotlin
data class PlaySource(
    val sourceId: String,
    val sourceName: String,
    val priority: Int
)
```

### 3.4 SearchHistory
```kotlin
data class SearchHistory(
    val keywords: List<String>
)
```

### 3.5 HotSearch
```kotlin
data class HotSearch(
    val keyword: String,
    val tag: String,         // 热 | 荐
    val description: String
)
```

### 3.6 RankItem
```kotlin
data class RankItem(
    val rank: Int,
    val movieId: String,
    val movie: Movie
)
```

### 3.7 ApiResponse
```kotlin
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T
)

 data class PageData<T>(
    val list: List<T>,
    val page: Int,
    val size: Int,
    val total: Int,
    val hasMore: Boolean
)
```

---

## 4. 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 5. CORS 配置

后端需开启跨域，允许 Android 模拟器和本地调试访问：
```java
// Spring Boot 示例
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }
}
```

---

## 6. 样例数据建议

### 6.1 影片数据量
- 首页推荐：每类 40-60 条
- 分类筛选：每类 100+ 条
- 搜索结果：每个关键词 10-30 条
- 排行榜：每类 50 条

### 6.2 图片资源
- 使用 `https://via.placeholder.com/{width}x{height}/{bg}/{text}?text={name}` 生成占位海报
- 避免使用截图中的真实版权海报

### 6.3 测试视频
- 公开测试视频：`https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4`
- 或使用本地 MP4 资源
