# AI Recorder Android 端项目结构说明

## 📋 目录

- [项目概述](#项目概述)
- [目录结构](#目录结构)
- [核心模块说明](#核心模块说明)
- [数据模型](#数据模型)
- [API 接口](#api-接口)
- [架构设计](#架构设计)

---

## 项目概述

AI Recorder Android 端是一个原生 Android 应用，用于通过远程 API 访问 PC 端的录音库。

**主要功能：**
- 查看 PC 端所有录音
- 查看和复制转写内容
- 查看和复制摘要内容
- 上传新录音到 PC 端
- 系统健康状态监控
- 录音删除

---

## 目录结构

```
app/src/main/
├── java/com/airecorder/android/
│   ├── data/                           # 数据层
│   │   ├── local/                      # 本地存储
│   │   │   └── PreferencesManager.kt    # DataStore 配置管理（服务器地址、Token）
│   │   ├── model/                      # 数据模型
│   │   │   └── Models.kt               # 所有数据模型定义
│   │   ├── remote/                     # 远程 API
│   │   │   ├── ApiService.kt           # Retrofit API 接口定义
│   │   │   └── AuthInterceptor.kt      # Token 认证拦截器
│   │   └── repository/                  # 仓库层
│   │       ├── RecordingRepository.kt   # 录音数据仓库
│   │       └── SettingsRepository.kt    # 设置数据仓库
│   ├── di/                             # 依赖注入层（Hilt）
│   │   └── AppModule.kt                # Hilt 模块配置
│   ├── ui/                             # UI 层
│   │   ├── components/                  # 可复用组件
│   │   │   ├── BottomNavigationBar.kt  # 底部导航栏
│   │   │   ├── RecordingItem.kt        # 录音列表项组件
│   │   │   ├── StateComponents.kt      # 状态组件（加载、空状态、错误）
│   │   │   └── StatusIndicator.kt      # 状态指示器组件
│   │   ├── navigation/                  # 导航配置
│   │   │   └── NavDestinations.kt      # 导航目标定义
│   │   ├── screens/                     # 页面
│   │   │   ├── LibraryScreen.kt        # 录音库首页
│   │   │   ├── LibraryViewModel.kt     # 录音库 ViewModel
│   │   │   ├── DetailScreen.kt         # 录音详情页
│   │   │   ├── DetailViewModel.kt      # 录音详情 ViewModel
│   │   │   ├── HealthScreen.kt         # 健康面板页
│   │   │   ├── SettingsScreen.kt       # 设置页
│   │   │   └── UploadBottomSheet.kt    # 上传底部弹窗
│   │   ├── theme/                       # 主题配置
│   │   │   ├── Color.kt                # 颜色定义
│   │   │   ├── Theme.kt                # 主题配置
│   │   │   └── Type.kt                 # 字体样式
│   │   └── AIRecorderApp.kt            # 应用入口 Composable
│   ├── util/                           # 工具类
│   │   └── FormatUtils.kt              # 格式化工具
│   ├── MainActivity.kt                  # 主 Activity
│   └── AIRecorderApplication.kt         # Application 类（Hilt 初始化）
├── res/                                # 资源文件
│   ├── values/
│   │   ├── strings.xml                 # 字符串资源
│   │   └── themes.xml                  # 主题资源
│   └── ...
└── AndroidManifest.xml                 # 应用清单
```

---

## 核心模块说明

### 1. 数据层 (`data/`)

#### 本地存储 (`local/`)
- **PreferencesManager.kt**：使用 DataStore Preferences 存储
  - 服务器地址
  - API Token
  - 连接状态

#### 数据模型 (`model/`)
- **Models.kt**：定义所有 API 响应和请求的数据结构
  - `Recording`：录音信息
  - `TranscriptSegment`：转写片段
  - `Summary`：摘要
  - `Task`：任务信息
  - `HealthResponse`：健康检查响应
  - `SearchResult`：搜索结果
  - 等等

#### 远程 API (`remote/`)
- **ApiService.kt**：Retrofit 接口定义，包含所有 API 调用
- **AuthInterceptor.kt**：OkHttp 拦截器，自动添加 `X-API-Token` 请求头

#### 仓库层 (`repository/`)
- **RecordingRepository.kt**：录音数据仓库
  - 获取录音列表
  - 获取录音详情
  - 上传录音
  - 删除录音
  - 触发转写/摘要
- **SettingsRepository.kt**：设置数据仓库
  - 读取/保存服务器地址
  - 读取/保存 API Token
  - 测试连接

---

### 2. 依赖注入层 (`di/`)

#### AppModule.kt
提供以下依赖：
- `OkHttpClient`：配置了日志和认证拦截器
- `Retrofit`：配置了 KotlinX Serialization 转换器
- `ApiService`：API 服务实例
- `DataStore<Preferences>`：数据存储实例
- `PreferencesManager`：配置管理实例
- Repositories：仓库实例

---

### 3. UI 层 (`ui/`)

#### 页面 (`screens/`)

| 页面 | ViewModel | 功能 |
|------|-----------|------|
| **LibraryScreen** | LibraryViewModel | 录音库首页，显示录音列表、搜索、上传 |
| **DetailScreen** | DetailViewModel | 录音详情，显示转写、摘要、信息 |
| **HealthScreen** | - | 健康面板，显示系统状态 |
| **SettingsScreen** | - | 设置页，配置服务器地址和 Token |
| **UploadBottomSheet** | - | 上传底部弹窗，选择音频文件 |

#### 导航 (`navigation/`)
- **NavDestinations.kt**：定义导航路由和参数

#### 组件 (`components/`)
- **BottomNavigationBar.kt**：底部导航栏，4 个 Tab
- **RecordingItem.kt**：录音列表项组件
- **StateComponents.kt**：状态组件集合
  - `LoadingState`：加载状态组件
  - `EmptyState`：空状态组件（含插图和操作按钮）
  - `ErrorState`：错误状态组件（含重试按钮）
- **StatusIndicator.kt**：状态指示器组件
  - `RecordingStatus`：状态枚举（待处理、已转写、已摘要、处理中、失败）
  - `getStatus()`：根据录音信息判断状态
  - `StatusIndicator`：状态指示器 UI 组件

#### 主题 (`theme/`)
- **Color.kt**：颜色定义
- **Theme.kt**：Material 3 主题配置
- **Type.kt**：字体样式

---

## 数据模型

### 主要数据模型

```kotlin
// 录音信息
data class Recording(
    val id: String,
    val filename: String,
    val originalPath: String?,
    val format: String?,
    val status: String,
    val durationSeconds: Double?,
    val fileSizeBytes: Long?,
    val createdAt: String?,
    val updatedAt: String?,
    val tags: String?,
    val sourceType: String?
)

// 转写片段
data class TranscriptSegment(
    val id: String?,
    val recordingId: String?,
    val startTime: Double?,
    val endTime: Double?,
    val speaker: String?,
    val text: String,
    val sequence: Int?
)

// 摘要
data class Summary(
    val id: String?,
    val recordingId: String?,
    val content: String,
    val createdAt: String?
)

// 健康检查响应
data class HealthResponse(
    val status: String,
    val python: String?,
    val ffmpeg: Boolean,
    val funasr: Boolean,
    val dataDir: String?,
    val modelDir: String?,
    val asrModel: String?,
    val llmProvider: String?,
    val llmBaseUrl: String?,
    val llmModel: String?,
    val llmConfigured: Boolean,
    val logDir: String?,
    val recentErrors: List<String>,
    val system: SystemInfo?,
    val tunnel: TunnelInfo?
)
```

---

## API 接口

### ApiService 定义

```kotlin
interface ApiService {
    // 健康检查
    @GET("/health")
    suspend fun getHealth(): Response<HealthResponse>
    
    // 获取录音列表（支持搜索和标签筛选）
    @GET("/api/recordings")
    suspend fun getRecordings(
        @Query("query") query: String = "",
        @Query("tag") tag: String = ""
    ): Response<SearchResult>
    
    // 获取单个录音详情
    @GET("/api/recordings/{id}")
    suspend fun getRecording(@Path("id") id: String): Response<RecordingDetail>
    
    // 上传录音
    @Multipart
    @POST("/api/recordings")
    suspend fun uploadRecording(
        @Part file: MultipartBody.Part
    ): Response<Recording>
    
    // 删除录音
    @DELETE("/api/recordings/{id}")
    suspend fun deleteRecording(@Path("id") id: String): Response<Map<String, String>>
    
    // 导出转写
    @GET("/api/recordings/{id}/exports/transcript")
    suspend fun exportTranscript(
        @Path("id") id: String,
        @Query("format") format: String = "md"
    ): Response<String>
    
    // 触发转写
    @POST("/api/transcribe/{id}")
    suspend fun transcribe(@Path("id") id: String): Response<Task>
    
    // 触发摘要
    @POST("/api/summary/{id}")
    suspend fun summarize(
        @Path("id") id: String,
        @Query("mode") mode: String = "summary"
    ): Response<Task>
    
    // 获取任务状态
    @GET("/api/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): Response<Task>
    
    // 获取摘要模板
    @GET("/api/summary/templates")
    suspend fun getSummaryTemplates(): Response<List<Map<String, String>>>
    
    // 获取 LLM 设置
    @GET("/api/settings/llm")
    suspend fun getLLMSettings(): Response<LLMSettings>
    
    // 获取目录监控设置
    @GET("/api/settings/watch")
    suspend fun getWatchSettings(): Response<WatchSettings>
    
    // 获取存储设置
    @GET("/api/settings/storage")
    suspend fun getStorageSettings(): Response<StorageSettings>
}
```

---

## 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                     UI Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Library    │  │   Detail     │  │   Health     │  │
│  │   Screen     │  │   Screen     │  │   Screen     │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                 │           │
│         └─────────────────┼─────────────────┘           │
│                           │                             │
│                  ┌────────▼────────┐                   │
│                  │   ViewModels    │                   │
│                  └────────┬────────┘                   │
└───────────────────────────┼──────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────┐
│                  Repository Layer                        │
│  ┌──────────────────────┐  ┌──────────────────────┐    │
│  │ RecordingRepository  │  │  SettingsRepository  │    │
│  └──────────┬───────────┘  └──────────┬───────────┘    │
└─────────────┼──────────────────────────┼────────────────┘
              │                          │
     ┌────────▼────────┐       ┌────────▼────────┐
     │   ApiService    │       │ PreferencesMgr  │
     │  (Retrofit)     │       │   (DataStore)   │
     └─────────────────┘       └─────────────────┘
```

### 设计模式

- **MVVM**：UI 层与业务逻辑分离
- **Repository Pattern**：统一数据访问入口
- **Dependency Injection**：使用 Hilt 管理依赖
- **Observer Pattern**：使用 Flow 观察数据变化

---

## 依赖管理

### 主要依赖

```kotlin
// Compose & Material 3
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose")

// Hilt (DI)
implementation("com.google.dagger:hilt-android")
ksp("com.google.dagger:hilt-android-compiler")

// Retrofit & OkHttp
implementation("com.squareup.retrofit2:retrofit")
implementation("com.squareup.okhttp3:okhttp")

// KotlinX Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

// DataStore
implementation("androidx.datastore:datastore-preferences")

// Coroutines & Flow
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android")

// Markdown 渲染
implementation("com.halilibo.compose-richtext:richtext-commonmark-android")
```

---

## 相关文档

- [README.md](README.md) - Android 端快速开始
- [../docs/android-remote-access.md](../docs/android-remote-access.md) - 远程访问配置
- [../PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md) - 整体项目结构
