# AI Recorder 技术栈说明

## 📋 目录

- [整体架构](#整体架构)
- [PC 端 - 后端](#pc-端---后端)
- [PC 端 - 前端](#pc-端---前端)
- [Android 端](#android-端)
- [依赖版本](#依赖版本)

---

## 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (React)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Library     │  │    Watch     │  │   Settings   │      │
│  │    Page      │  │    Page      │  │    Page      │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
└─────────┼─────────────────┼─────────────────┼───────────────┘
          │                 │                 │
          ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────────────┐
│                    后端 API (FastAPI)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Recordings  │  │  Transcribe  │  │   Summary    │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
└─────────┼─────────────────┼─────────────────┼───────────────┘
          │                 │                 │
          ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────────────┐
│                   业务服务层 (Services)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  ASRService  │  │ AudioService │  │SummaryService│      │
│  │  (FunASR)    │  │  (FFmpeg)    │  │  (LLM)       │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  SQLite (数据)  │
                    └─────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  Android 客户端 (Compose)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Library     │  │   Detail     │  │   Health     │      │
│  │   Screen     │  │   Screen     │  │   Screen     │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

---

## PC 端 - 后端

### 核心技术

| 技术 | 说明 | 用途 |
|------|------|------|
| **Python** | 3.10+ | 编程语言 |
| **FastAPI** | - | Web 框架，提供 API 服务 |
| **SQLModel** | - | ORM，数据库操作 |
| **SQLite** | - | 数据库，存储录音、转写、摘要等 |
| **FunASR** | - | 语音转写引擎 |
| **FFmpeg** | - | 音频处理（格式转换、时长获取） |
| **OpenAI SDK** | - | LLM 调用 |
| **Uvicorn** | - | ASGI 服务器 |

### 主要依赖 (requirements.txt)

```
fastapi
uvicorn
sqlmodel
pydantic-settings
python-multipart
httpx
openai
pydub
soundfile
numpy
funasr
modelscope
torch
torchaudio
python-docx
```

---

## PC 端 - 前端

### 核心技术

| 技术 | 说明 | 用途 |
|------|------|------|
| **TypeScript** | - | 类型安全的 JavaScript |
| **React** | 19 | UI 框架 |
| **Vite** | - | 构建工具，提供开发服务器和生产构建 |
| **Lucide React** | - | 图标库 |

### 主要依赖 (package.json)

```json
{
  "dependencies": {
    "react": "^19.2.5",
    "react-dom": "^19.2.5",
    "lucide-react": "^0.487.0"
  },
  "devDependencies": {
    "@types/react": "^19.2.14",
    "@types/react-dom": "^19.2.3",
    "@vitejs/plugin-react": "^6.0.1",
    "typescript": "^6.0.3",
    "vite": "^8.0.10"
  }
}
```

---

## Android 端

### 核心技术

| 技术 | 说明 | 用途 |
|------|------|------|
| **Kotlin** | - | 编程语言 |
| **Jetpack Compose** | - | 现代 UI 框架 |
| **Material Design 3** | - | UI 设计系统 |
| **Navigation Compose** | - | 页面导航 |
| **ViewModel** | - | UI 相关数据管理 |
| **Retrofit** | - | HTTP 客户端 |
| **OkHttp** | - | 底层 HTTP 客户端 |
| **KotlinX Serialization** | - | JSON 序列化/反序列化 |
| **Hilt** | - | 依赖注入 |
| **DataStore Preferences** | - | 本地配置存储 |
| **Kotlin Coroutines** | - | 异步编程 |
| **Flow** | - | 响应式数据流 |
| **Coil** | - | 图片加载 |
| **Compose Richtext** | - | Markdown 渲染 |

### 主要依赖 (app/build.gradle.kts)

```kotlin
// Core Android
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
implementation("androidx.activity:activity-compose:1.8.2")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.02.01"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3:1.2.0")
implementation("androidx.compose.material:material-icons-extended:1.6.1")
implementation("androidx.navigation:navigation-compose:2.7.7")

// Hilt
implementation("com.google.dagger:hilt-android:2.48")
ksp("com.google.dagger:hilt-android-compiler:2.48")
implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

// Retrofit + OkHttp
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// KotlinX Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Coil
implementation("io.coil-kt:coil-compose:2.5.0")

// Markdown
implementation("com.halilibo.compose-richtext:richtext-commonmark-android:0.20.0")
implementation("com.halilibo.compose-richtext:richtext-ui-material3-android:0.20.0")

// Accompanist
implementation("com.google.accompanist:accompanist-permissions:0.32.0")
```

### 版本要求

| 项 | 版本 |
|----|------|
| **minSdk** | 26 (Android 8.0) |
| **targetSdk** | 36 |
| **compileSdk** | 36 |
| **Java** | 17 |

---

## 外部工具

| 工具 | 用途 |
|------|------|
| **FFmpeg** | 音频格式转换、时长获取 |
| **Git** | 版本控制 |

---

## 数据流

### 转写流程
```
上传音频 → 归一化 (FFmpeg) → 转写 (FunASR) → 保存片段 → 更新状态
```

### 摘要流程
```
读取转写 → 拼接文本 → LLM 生成摘要 → 保存 → 更新状态
```

---

## 相关文档

- [../PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md) - 整体项目结构
- [../CODE_WIKI.md](../CODE_WIKI.md) - 代码维基
- [../backend/PROJECT_STRUCTURE.md](../backend/PROJECT_STRUCTURE.md) - 后端结构
- [../frontend/PROJECT_STRUCTURE.md](../frontend/PROJECT_STRUCTURE.md) - 前端结构
- [../android/PROJECT_STRUCTURE.md](../android/PROJECT_STRUCTURE.md) - Android 结构
