# AI Recorder 项目结构说明

## 📋 目录

- [项目概述](#项目概述)
- [整体架构](#整体架构)
- [PC 端结构](#pc-端结构)
  - [后端](#后端)
  - [前端](#前端)
- [Android 端结构](#android-端结构)
- [数据存储](#数据存储)
- [相关文档](#相关文档)

---

## 项目概述

AI Recorder 是一个面向 Windows 本机的录音整理工具，包含：

- **PC 端**：负责音频处理、转写、摘要、存储和 Web 界面
- **Android 端**：提供移动端访问录音库、上传音频的能力
- **远程访问**：支持通过公网域名 + API Token 安全访问

---

## 整体架构

```
AI-workspace/
├── backend/              # FastAPI 后端服务
│   ├── app/
│   │   ├── api/       # API 路由层（12个模块）
│   │   ├── services/   # 业务服务层（10个服务）
│   │   ├── models/      # 数据模型层（5个模型）
│   │   ├── pipeline/    # 工作流编排
│   │   └── db/         # 数据库层
│   ├── tests/          # 单元测试
│   └── requirements.txt
├── frontend/            # React 前端应用
│   ├── src/
│   │   ├── pages/      # 页面组件（4个页面）
│   │   ├── components/ # 可复用组件
│   │   ├── hooks/       # 自定义 Hooks
│   │   ├── lib/         # 工具函数和 API 客户端
│   │   └── stores/       # 状态管理
│   └── package.json
├── android/             # Android 原生客户端
│   ├── app/src/main/java/com/airecorder/android/
│   │   ├── data/       # 数据层
│   │   ├── di/         # 依赖注入
│   │   └── ui/         # UI 层
│   └── build.gradle.kts
├── data/                # 数据目录（数据库、音频、转写、摘要）
├── models/             # FunASR 模型缓存
├── logs/               # 运行日志
├── docs/               # 项目文档
├── scripts/            # 辅助脚本
└── *.md               # 项目说明文档
```

---

## PC 端结构

### 后端

```
backend/
├── app/
│   ├── api/                    # API 路由层
│   │   ├── auth.py            # 认证中间件（API Token）
│   │   ├── events.py           # 事件订阅 API (SSE)
│   │   ├── filesystem.py      # 文件系统操作 API
│   │   ├── health.py          # 健康检查 API
│   │   ├── recordings.py      # 录音管理 API
│   │   ├── settings.py        # 设置 API
│   │   ├── summary.py         # 摘要 API
│   │   ├── tasks.py           # 任务管理 API
│   │   ├── tokens.py          # Token 管理 API
│   │   ├── transcribe.py      # 转写 API
│   │   └── watch.py           # 目录监控 API
│   ├── db/                     # 数据库层
│   │   └── database.py        # SQLite 数据库连接和初始化
│   ├── exceptions/             # 异常处理
│   │   ├── base.py           # 基础异常类
│   │   └── exception_handler.py # 全局异常处理
│   ├── middleware/            # 中间件
│   │   ├── exception_handler.py # 异常处理中间件
│   │   └── security_headers.py # 安全头中间件
│   ├── models/                 # 数据模型层
│   │   ├── recording.py      # Recording 模型
│   │   ├── summary.py         # Summary 模型
│   │   ├── task.py            # Task 模型
│   │   ├── transcript.py     # TranscriptSegment 模型
│   │   ├── watch_event.py    # WatchEvent 模型
│   │   ├── access_log.py     # AccessLog 模型
│   │   └── api_token.py      # ApiToken 模型
│   ├── pipeline/               # 工作流管道
│   │   └── workflow.py       # 转写/摘要任务编排
│   ├── services/               # 业务服务层
│   │   ├── asr_service.py     # ASR 转写服务（FunASR）
│   │   ├── audio_service.py   # 音频处理服务（FFmpeg）
│   │   ├── docx_export.py    # DOCX 导出服务
│   │   ├── export_names.py   # 导出文件名生成
│   │   ├── file_service.py   # 文件操作服务
│   │   ├── runtime_log.py    # 运行时日志服务
│   │   ├── sse_service.py    # SSE 事件推送服务
│   │   ├── summary_service.py # 摘要服务（大模型）
│   │   ├── task_service.py   # 任务管理服务
│   │   └── watch_service.py   # 目录监控服务
│   ├── config.py              # 配置管理
│   └── main.py               # FastAPI 应用入口
├── tests/                     # 单元测试
│   ├── test_api_auth.py
│   ├── test_recording_audio.py
│   ├── test_recording_management.py
│   └── test_task_service.py
├── .env.example               # 环境变量示例
├── requirements.txt           # Python 依赖
└── start.ps1                 # 后端启动脚本
```

#### 后端核心模块

| 模块 | 职责 |
|------|------|
| **api/** | RESTful API 端点，处理 HTTP 请求/响应 |
| **models/** | SQLModel 数据模型，对应数据库表结构 |
| **services/** | 核心业务逻辑（转写、摘要、音频处理等） |
| **pipeline/** | 任务执行流程编排 |
| **db/** | 数据库连接和会话管理 |

#### API 接口概览

| 功能 | 接口 |
|------|------|
| 录音管理 | `/api/recordings` - 列表、上传、详情、删除、编辑标签 |
| 音频播放 | `/api/recordings/{id}/audio` - 支持 Range 请求 |
| 转写 | `/api/transcribe/{id}` - 触发转写，支持批量 |
| 摘要 | `/api/summary/{id}` - 触发摘要，支持多模板 |
| 任务 | `/api/tasks/{id}` - 获取状态、取消任务 |
| 导出 | `/api/recordings/{id}/exports/transcript` - md/txt/json/srt/docx |
| 监控 | `/api/watch/events` - 监控事件、触发扫描 |
| 健康 | `/health` - 系统状态检查 |

---

### 前端

```
frontend/
├── src/
│   ├── components/              # 可复用组件
│   │   ├── feedback/           # 反馈组件（Toast、ConfirmDialog）
│   │   ├── layout/             # 布局组件（NavBar）
│   │   ├── markdown/           # Markdown 渲染组件
│   │   ├── recording/          # 录音相关组件
│   │   │   ├── RecordingDetailPanel.tsx
│   │   │   ├── SummaryCard.tsx
│   │   │   └── MetricCard.tsx
│   │   └── ui/                 # UI 基础组件
│   │       ├── FolderPicker.tsx
│   │       ├── ExportButtons.tsx
│   │       ├── SettingsSection.tsx
│   │       ├── InfoCard.tsx
│   │       └── StatusBadge.tsx
│   ├── hooks/                   # 自定义 Hooks
│   │   ├── useRecordings.ts    # 录音数据管理
│   │   └── useSSE.ts          # SSE 事件订阅
│   ├── lib/                    # 工具函数和库
│   │   ├── api.ts            # API 客户端
│   │   ├── types.ts          # TypeScript 类型定义
│   │   ├── viewTypes.ts      # 视图类型定义
│   │   ├── format.ts         # 格式化工具
│   │   └── sse.ts            # SSE 客户端
│   ├── pages/                  # 页面组件
│   │   ├── HealthPage.tsx    # 健康检查页面
│   │   ├── LibraryPage.tsx   # 录音库页面
│   │   ├── SettingsPage.tsx  # 设置页面
│   │   └── WatchPage.tsx    # 目录监控页面
│   ├── stores/                 # 状态管理
│   │   ├── appStore.ts       # 应用全局状态
│   │   └── taskStore.ts      # 任务状态管理
│   ├── App.tsx               # 应用主组件
│   ├── main.tsx              # 应用入口
│   └── styles.css            # 全局样式
├── index.html                # HTML 模板
├── package.json              # 项目依赖
├── tsconfig.json             # TypeScript 配置
├── vite.config.mjs           # Vite 构建配置
└── build.ps1                 # 前端构建脚本
```

#### 前端页面

| 页面 | 功能 |
|------|------|
| **LibraryPage** | 录音库：查看、搜索、筛选、管理录音 |
| **WatchPage** | 目录监控：配置监控目录、查看监控事件 |
| **SettingsPage** | 设置：配置存储路径、大模型、转写参数 |
| **HealthPage** | 健康面板：系统状态检查、日志查看 |

---

## Android 端结构

```
android/
├── app/src/main/
│   ├── java/com/airecorder/android/
│   │   ├── data/                    # 数据层
│   │   │   ├── local/              # 本地存储
│   │   │   │   └── PreferencesManager.kt
│   │   │   ├── model/              # 数据模型
│   │   │   │   └── Models.kt
│   │   │   ├── remote/             # 远程 API
│   │   │   │   ├── ApiService.kt
│   │   │   │   ├── AuthInterceptor.kt
│   │   │   │   └── SseClient.kt
│   │   │   └── repository/         # 仓库层
│   │   │       ├── RecordingRepository.kt
│   │   │       └── SettingsRepository.kt
│   │   ├── di/                      # 依赖注入
│   │   │   └── AppModule.kt
│   │   ├── ui/                      # UI 层
│   │   │   ├── components/          # 可复用组件
│   │   │   ├── navigation/          # 导航配置
│   │   │   ├── screens/             # 页面
│   │   │   │   ├── LibraryScreen.kt
│   │   │   │   ├── DetailScreen.kt
│   │   │   │   ├── HealthScreen.kt
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── UploadBottomSheet.kt
│   │   │   └── theme/               # 主题配置
│   │   ├── util/                    # 工具类
│   │   ├── MainActivity.kt
│   │   └── AIRecorderApplication.kt
│   ├── res/                        # 资源文件
│   └── AndroidManifest.xml
├── build.gradle.kts               # 项目级 Gradle 配置
├── settings.gradle.kts            # Gradle 设置
├── gradle.properties
└── README.md                      # Android 端快速开始
```

#### Android 端架构

| 层级 | 职责 |
|------|------|
| **UI Layer** | Jetpack Compose 组件 + ViewModel |
| **Data Layer** | Repository + ApiService + DataStore |
| **DI Layer** | Hilt 依赖注入 |

#### Android 端页面

| 页面 | 功能 |
|------|------|
| **LibraryScreen** | 录音库：查看 PC 端所有录音、搜索、上传 |
| **DetailScreen** | 录音详情：查看转写、摘要、录音信息、删除 |
| **HealthScreen** | 健康面板：系统状态、资源使用 |
| **SettingsScreen** | 设置：配置服务器地址、Token |

---

## 数据存储

### 目录结构

```
data/
├── app.db                    # SQLite 数据库
├── recordings/               # 上传的录音文件
├── normalized/               # 归一化后的音频（用于转写）
├── transcripts/               # 转写结果 JSON
└── summaries/                # 摘要结果 Markdown
```

### 数据库表

| 表名 | 说明 |
|------|------|
| `recording` | 录音记录 |
| `task` | 任务记录 |
| `transcript_segment` | 转写片段 |
| `summary` | 摘要记录 |
| `watch_event` | 目录监控事件 |
| `access_log` | 访问日志 |
| `api_token` | API Token |

---

## 相关文档

| 文档 | 说明 |
|------|------|
| [README.md](README.md) | 主项目说明 |
| [CODE_WIKI.md](CODE_WIKI.md) | 代码维基 |
| [CHANGELOG.md](CHANGELOG.md) | 变更日志 |
| [docs/TECH_STACK.md](docs/TECH_STACK.md) | 技术栈说明 |
| [docs/product/roadmap.md](docs/product/roadmap.md) | 产品路线图 |
| [backend/PROJECT_STRUCTURE.md](backend/PROJECT_STRUCTURE.md) | 后端详细结构 |
| [frontend/PROJECT_STRUCTURE.md](frontend/PROJECT_STRUCTURE.md) | 前端详细结构 |
| [android/PROJECT_STRUCTURE.md](android/PROJECT_STRUCTURE.md) | Android 端详细结构 |
| [docs/android-remote-access.md](docs/android-remote-access.md) | Android 远程访问配置 |
| [docs/troubleshooting.md](docs/troubleshooting.md) | 故障排查 |
